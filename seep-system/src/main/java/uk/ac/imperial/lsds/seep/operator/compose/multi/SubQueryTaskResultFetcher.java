package uk.ac.imperial.lsds.seep.operator.compose.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.compose.subquery.SubQueryTaskResult;

public class SubQueryTaskResultFetcher implements Runnable {
	
	private long lastFinishedOrderID = -1;

	private IRunningSubQueryTaskHandler runningSubQueryTaskHandler;

	private ISubQueryTaskResultForwarder resultForwarder;

	public SubQueryTaskResultFetcher(IRunningSubQueryTaskHandler runningSubQueryTaskHandler, ISubQueryTaskResultForwarder resultForwarder) {
		this.runningSubQueryTaskHandler = runningSubQueryTaskHandler;
		this.resultForwarder = resultForwarder;
	}
	
	@Override
	public void run() {
		
		/*
		 * For each running task, check whether it has terminated and collect
		 * those that have finished
		 */
		Map<Integer, Future<SubQueryTaskResult>> finished = new HashMap<>();
		for (Future<SubQueryTaskResult> future : runningSubQueryTaskHandler.getRunningSubQueryTasks())
			if (future.isDone())
				try {
					finished.put(future.get().getLogicalOrderID(), future);
				} catch (InterruptedException | ExecutionException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
		
		/*
		 * Process all tasks that are finished and have consecutive logical order ids,
		 * starting from the last known one
		 */
		List<Integer> lIds = new ArrayList<>(finished.keySet());
		Collections.sort(lIds);
		for (int lId : lIds) {
			Future<SubQueryTaskResult> future = finished.get(lId);
			if (this.lastFinishedOrderID == lId - 1) {
				// Remove from running tasks
				runningSubQueryTaskHandler.getRunningSubQueryTasks().remove(future);
				// Get result
				try {
					SubQueryTaskResult result = future.get();
					List<DataTuple> resultStream = result.getResultStream();
					/*
					 * Forward the result using the appropriate mechanism,
					 * either writing to the downstream sub query buffer or
					 * by sending to distributed nodes via the API of the 
					 * MultiOperator
					 */
					this.resultForwarder.forwardResult(resultStream);
					
					// Record progress by updating the last finished pointer
					this.lastFinishedOrderID = result.getLogicalOrderID();
					// free data in the buffer over which the task was defined
					result.freeIndicesInBuffers();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			else
				/*
				 * We are missing the result of the task with the next logical order id,
				 * so we cannot further process the finished tasks
				 */
				break;
		}
	}
}