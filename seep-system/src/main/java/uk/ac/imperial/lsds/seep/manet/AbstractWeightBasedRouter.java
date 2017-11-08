package uk.ac.imperial.lsds.seep.manet;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.DownUpRCtrl;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.Timestamp;
import uk.ac.imperial.lsds.seep.manet.GraphUtil.InetAddressNodeId;
import uk.ac.imperial.lsds.seep.operator.OperatorContext;

public class AbstractWeightBasedRouter implements IRouter {
	protected final static double INITIAL_WEIGHT = 1;
	protected final Map<Integer, Double> weights;
	protected final Map<Integer, Set<Timestamp>> unmatched;
	protected final OperatorContext opContext;	//TODO: Want to get rid of this dependency!
	protected Integer lastRouted = null;
	protected int switchCount = 0;
	protected final Object lock = new Object(){};
	protected final boolean upstreamRoutingController;
	protected final boolean downIsMultiInput;
	protected final boolean downIsUnreplicatedSink;

	//private final WeightExpiryMonitor weightExpiryMonitor = new WeightExpiryMonitor();
	//private final Map<Integer, Long> lastWeightUpdateTimes;
	//private long tLastSwitch = 0;

	public AbstractWeightBasedRouter(OperatorContext opContext) {
		this.weights = new HashMap<>();
		this.unmatched = new HashMap<>();
		this.opContext = opContext;
		ArrayList<Integer> downOps = opContext.getDownstreamOpIdList();
		for (int downOpId : downOps)
		{
			weights.put(downOpId, INITIAL_WEIGHT);
			unmatched.put(downOpId, new HashSet<Timestamp>());
		}
		Query meanderQuery = opContext.getMeanderQuery(); 
		int logicalId = meanderQuery.getLogicalNodeId(opContext.getOperatorStaticInformation().getOpId());
		int downLogicalId = meanderQuery.getNextHopLogicalNodeId(logicalId); 

		downIsMultiInput = meanderQuery.getLogicalInputs(downLogicalId).length > 1;
		downIsUnreplicatedSink = meanderQuery.isSink(downLogicalId) && meanderQuery.getPhysicalNodeIds(downLogicalId).size() == 1;
		upstreamRoutingController = Boolean.parseBoolean(GLOBALS.valueFor("enableUpstreamRoutingControl")) && !downIsMultiInput;
	}

	@Override
	public ArrayList<Integer> route(Timestamp batchId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Integer, Set<Timestamp>> handleDownUp(DownUpRCtrl downUp) {
		// TODO Auto-generated method stub
		throw new RuntimeException("TODO");
	}
	
	@Override
	public void updateNetTopology(
			Map<InetAddressNodeId, Map<InetAddressNodeId, Double>> linkState) {
		throw new RuntimeException("Logic error");		
	}
	
	public Set<Timestamp> areConstrained(Set<Timestamp> queued)
	{
		return null;
	}
	
	public void handleDownFailed(int downOpId)
	{
		throw new RuntimeException("TODO"); 
	}

	public Map<Integer, Set<Timestamp>> handleWeights(Map<Integer, Double> newWeights, Integer downUpdated)
	{
		throw new RuntimeException("Logic error."); 
	}
}
