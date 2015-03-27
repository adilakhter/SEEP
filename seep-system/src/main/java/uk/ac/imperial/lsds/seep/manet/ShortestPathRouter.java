package uk.ac.imperial.lsds.seep.manet;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.DownUpRCtrl;
import uk.ac.imperial.lsds.seep.operator.OperatorContext;

public class ShortestPathRouter implements IRouter {

	private final static Logger logger = LoggerFactory.getLogger(ShortestPathRouter.class);
	private final OperatorContext opContext;
	private final Query query;
	private final int localPhysicalId;
	private Map<InetAddress, Map<InetAddress, Double>> netTopology = null;
	private volatile Integer currentNextHop = null;
	private final Object lock = new Object(){};

	public ShortestPathRouter(OperatorContext opContext)
	{
		this.opContext = opContext;
		this.query = opContext.getMeanderQuery();
		this.localPhysicalId = opContext.getOperatorStaticInformation().getOpId();
	}

	//
	@Override
	public Integer route(long batchId) {
		// TODO Auto-generated method stub
		return currentNextHop;
	}

	@Override
	public void updateNetTopology(Map<InetAddress, Map<InetAddress, Double>> newNetTopology)
	{
		//TODO: Sync?
		synchronized(lock)
		{
			this.netTopology = newNetTopology;
			
			//TODO: Convert to something graph util can handle.
			Map initialAppTopology = computeInitialAppTopology(netTopology);
			Map appTopology = computeFinalAppTopology(initialAppTopology);
			logger.info("App topology (localOpId="+localPhysicalId+": "+ appTopology);
			Integer nextHop = useBandwidthMetric() ?
					GraphUtil.nextHopBandwidth(localPhysicalId, query.getSinkPhysicalId(), appTopology) :
						GraphUtil.nextHop(localPhysicalId, query.getSinkPhysicalId(), appTopology);
					if (nextHop == null) { currentNextHop = null; }
					else
					{
						throw new RuntimeException("TODO: Convert to operator id/index?");
					}
		}
	}

	private Map computeFinalAppTopology(Map initialAppTopology)
	{
		//TODO: Joins.
		return initialAppTopology;
	}
	private Map computeInitialAppTopology(Map<InetAddress, Map<InetAddress, Double>> currentNetTopology)
	{
		Map appTopology = initAppNodesMap();
		if (query.hasReplication())
		{
			appTopology = computeRawAppTopology(appTopology, currentNetTopology);
		}
		else
		{
			appTopology = computeUnreplicatedAppTopology(appTopology);
		}
		return appTopology;
	}
	private Map initAppNodesMap()
	{
		Map appNodesMap = new HashMap();
		Iterator srcIter = query.getSourcePhysicalIds().iterator();
		while (srcIter.hasNext())
		{
			appNodesMap.put(srcIter.next(), new HashMap());
		}
		appNodesMap.put(query.getSinkPhysicalId(), new HashMap());
		Iterator opIter = query.getOpPhysicalIds().iterator();
		while (opIter.hasNext())
		{
			appNodesMap.put(opIter.next(), new HashMap());
		}
		return appNodesMap;
	}
	private Map computeUnreplicatedAppTopology(Map appTopology)
	{
		Integer unitCost = new Integer(1);
		Iterator iter = appTopology.keySet().iterator();
		while (iter.hasNext())
		{
			Integer physicalId = (Integer)iter.next();
			Integer nextHopLogicalId = query.getNextHopLogicalNodeId(query.getLogicalNodeId(physicalId));
			//If this node isn't the sink, set its cost to next hop
			if (nextHopLogicalId != null)
			{
				Set nextHopPhysicalIds = query.getPhysicalNodeIds(nextHopLogicalId);
				if (nextHopPhysicalIds.size() != 1) throw new RuntimeException("Logic error");
				setLinkCost(physicalId, (Integer)nextHopPhysicalIds.iterator().next(), unitCost, appTopology);
			}
		}
		return appTopology;
	}
	private Map computeRawAppTopology(Map appTopology, Map currentNetTopology)
	{
		Set toProcess = new HashSet();
		Iterator srcIter = query.getSourcePhysicalIds().iterator();
		while (srcIter.hasNext())
		{
			Integer sourceId = (Integer)srcIter.next();
			Integer sourceNodeId = query.addrToNodeId(query.getNodeAddress(sourceId));
			Map sourceCosts = useBandwidthMetric() ? GraphUtil.widestPaths(sourceNodeId, currentNetTopology):GraphUtil.shortestPaths(sourceNodeId, currentNetTopology);
			Integer sourceLogicalId = query.getLogicalNodeId(sourceId);
			Integer nextHopLogicalId = query.getNextHopLogicalNodeId(sourceLogicalId);
			toProcess.add(nextHopLogicalId);
			Set sourceNextOpAlternatives = query.getPhysicalNodeIds(nextHopLogicalId);
			Iterator nextOpIter = sourceNextOpAlternatives.iterator();
			while(nextOpIter.hasNext())
			{
				Integer nextOp = (Integer)nextOpIter.next();
				Integer nextOpNodeId = query.addrToNodeId(query.getNodeAddress(nextOp));
				setLinkCost(sourceId, nextOp, sourceCosts.get(nextOpNodeId), appTopology);
			}
		}
		//TODO Could probably define a QueryIterator class
		Set processed = new HashSet();
		while (!toProcess.isEmpty())
		{
			Set moreToProcess = new HashSet();
			Iterator queryIter = toProcess.iterator();
			while (queryIter.hasNext())
			{
				Integer logicalId = (Integer)queryIter.next();
				Integer nextHopLogicalId = query.getNextHopLogicalNodeId(logicalId);
				if (nextHopLogicalId != null)
				{
					moreToProcess.add(nextHopLogicalId);
					Set physicalIds = query.getPhysicalNodeIds(logicalId);
					Set nextHopPhysicalIds = query.getPhysicalNodeIds(nextHopLogicalId);
					Iterator physIter = physicalIds.iterator();
					while (physIter.hasNext())
					{
						Integer physicalId = (Integer)physIter.next();
						Integer physicalNodeId = query.addrToNodeId(query.getNodeAddress(physicalId));
						Map opCosts = useBandwidthMetric() ? GraphUtil.widestPaths(physicalNodeId, currentNetTopology) : GraphUtil.shortestPaths(physicalNodeId, currentNetTopology);
						Iterator nextHopPhysIter = nextHopPhysicalIds.iterator();
						while (nextHopPhysIter.hasNext())
						{
							Integer nextHopPhysicalId = (Integer)nextHopPhysIter.next();
							Integer nextHopPhysicalNodeId = query.addrToNodeId(query.getNodeAddress(nextHopPhysicalId));
							setLinkCost(physicalId, nextHopPhysicalId, opCosts.get(nextHopPhysicalNodeId), appTopology);
						}
					}
				}
				else
				{
					//Must be the sink node.
					if (queryIter.hasNext()) throw new RuntimeException("Logic error.");
				}
				processed.add(logicalId);
			}
			toProcess.clear();
			toProcess.addAll(moreToProcess);
		}
		return appTopology;
	}
	
	private boolean useBandwidthMetric()
	{
		return false; //TODO
	}
	
	private void setLinkCost(Integer src, Integer nextHop, Object cost, Map appTopology)
	{
		((Map)appTopology.get(src)).put(nextHop, cost);
	}
	
	private Object getLinkCost(Integer src, Integer nextHop, Map appTopology)
	{
		return ((Map)appTopology.get(src)).get(nextHop);
	}

	@Override
	public void handleDownUp(DownUpRCtrl downUp) {
		throw new RuntimeException("Logic error.");
	}
}
