/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.operator.compose.multi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.API;
import uk.ac.imperial.lsds.seep.operator.Connectable;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;
import uk.ac.imperial.lsds.seep.operator.compose.subquery.ISubQueryConnectable;
import uk.ac.imperial.lsds.seep.operator.compose.subquery.WindowBatchTaskCreationScheme;

public class MultiOperator implements StatelessOperator {

	final private Logger LOG = LoggerFactory.getLogger(MultiOperator.class);
	
//	private static final int SUB_QUERY_TRIGGER_DELAY = Integer.valueOf(GLOBALS.valueFor("subQueryTriggerDelay"));
//	private static final int MICRO_OP_BATCH_SIZE = Integer.valueOf(GLOBALS.valueFor("microOpBatchSize"));
	
	private static final long serialVersionUID = 1L;

	private final int id;
	
	private Set<ISubQueryConnectable> subQueries;
	private API api;
	private Set<ISubQueryConnectable> mostUpstreamSubQueries;
	private Set<ISubQueryConnectable> mostDownstreamSubQueries;
	
	private Connectable parentConnectable; 
	
	private ExecutorService executorService;
	
//	private Map<ISubQueryConnectable, Integer> numberThreadsPerSubQuery = new HashMap<>();
 	
	private MultiOperator(Set<ISubQueryConnectable> subQueries, int multiOpId){
		this.id = multiOpId;
		this.subQueries = subQueries;
		for (ISubQueryConnectable c : this.subQueries)
			c.setParentMultiOperator(this);
	}
	
	public void setParentConnectable(Connectable parentConnectable) {
		this.parentConnectable = parentConnectable;
	}
	
	/**
	 * Note that pushing the data to the buffers of the most upstream 
	 * operators is not threadsafe. We assume a single thread to call 
	 * processData.
	 */
	@Override
	public void processData(DataTuple data, API api) {
		/*
		 * Store the api so that it can be later used to forward tuples
		 */
		this.api = api;
		
		/*
		 * Try to push to all input buffers of the most upstream sub queries
		 */
		for (ISubQueryConnectable q : this.mostUpstreamSubQueries) {
			for (SubQueryBuffer b : q.getLocalUpstreamBuffers().values()) {
				// this code is accessed by a single thread only
					while (!b.add(data)) {
						try {
							synchronized (b.getLock()) {
//								System.out.println("wait for buffer");
								b.getLock().wait();
//								System.out.println("woken up");
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
				}
			}
		}
		
	}
	
	@Override
	public void processData(List<DataTuple> dataList, API localApi) {
		for (DataTuple tuple : dataList)
			this.processData(tuple, localApi);
	}

	@Override
	public void setUp() {
		
		/*
		 * Create the thread pool 
		 */
		int numberOfCores = Runtime.getRuntime().availableProcessors();
		//TODO: think about tuning this selection
		int numberOfCoresToUse = Math.max(numberOfCores, subQueries.size());
		
		this.executorService = Executors.newFixedThreadPool(numberOfCoresToUse);

		/*
		 * Identify most upstream and most downstream local operators
		 * and create input/output buffers for them
		 */
		this.mostUpstreamSubQueries = new HashSet<>();
		this.mostDownstreamSubQueries = new HashSet<>();
		for (ISubQueryConnectable connectable : subQueries){
			if (connectable.isMostLocalUpstream()) {
				this.mostUpstreamSubQueries.add(connectable);
				SubQueryBuffer b = new SubQueryBuffer();
				for (Integer streamID : connectable.getSubQuery().getWindowDefinitions().keySet())
					connectable.registerLocalUpstreamBuffer(b, streamID);
			}
			if (connectable.isMostLocalDownstream()) {
				this.mostDownstreamSubQueries.add(connectable);
				
				SubQueryBuffer b = new SubQueryBuffer();
				// deactivate for local testing
//				for (Integer streamID : parentConnectable.getOpContext().routeInfo.keySet())
//					connectable.registerLocalDownstreamBuffer(b, streamID);
				connectable.registerLocalDownstreamBuffer(b, 0);
			}
		}
		
		/*
		 * Start handlers for sub queries
		 */
		for (ISubQueryConnectable c : this.subQueries) {
			/* 
			 * Select appropriate forwarding mechanism:
			 *  - default is writing to downstream sub query buffer 
			 *  - if subquery is most downstream, forwarding to distributed nodes via API is enabled
			 */
			ISubQueryTaskResultForwarder resultForwarder = 
				(c.isMostLocalDownstream())? 
					new SubQueryTaskResultAPIForwarder(c)
					: new SubQueryTaskResultBufferForwarder(c);
			
			//TODO: select task creation scheme
			SubQueryHandler r = new SubQueryHandler(c, new WindowBatchTaskCreationScheme(c), resultForwarder);
			(new Thread(r)).start();
		}
		
	}

	public API getAPI() {
		return this.api;
	}
	
	public int getMultiOpId(){
		return id;
	}
	
	public static MultiOperator synthesizeFrom(Set<ISubQueryConnectable> subOperators, int multiOpId){
		return new MultiOperator(subOperators, multiOpId);
	}

	public ExecutorService getExecutorService() {
		return this.executorService;
	}
	
}