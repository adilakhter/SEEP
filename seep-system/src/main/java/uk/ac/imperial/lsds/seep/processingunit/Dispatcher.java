package uk.ac.imperial.lsds.seep.processingunit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.imperial.lsds.seep.buffer.IBuffer;
import uk.ac.imperial.lsds.seep.comm.routing.IRoutingObserver;
import uk.ac.imperial.lsds.seep.comm.serialization.ControlTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.FailureCtrl;
import uk.ac.imperial.lsds.seep.operator.EndPoint;
import uk.ac.imperial.lsds.seep.operator.Operator;
import uk.ac.imperial.lsds.seep.runtimeengine.AsynchronousCommunicationChannel;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE.ControlTupleType;
import uk.ac.imperial.lsds.seep.runtimeengine.OutputQueue;
import uk.ac.imperial.lsds.seep.runtimeengine.SynchronousCommunicationChannel;

public class Dispatcher implements IRoutingObserver {

	//private final Map<Integer, DataTuple> senderQueues = new HashMap<Integer, ConcurrentNavigableMap<Integer, DataTuple>>();
	private static final Logger logger = LoggerFactory.getLogger(Dispatcher.class);
	private static final long FAILURE_TIMEOUT = 30 * 1000;
	private static final long RETRANSMIT_CHECK_INTERVAL = 1 * 1000;
	private static final long ROUTING_CTRL_DELAY = 1 * 1000;
	private final FailureCtrl nodeFctrl = new FailureCtrl();
	private final Map<Long, DataTuple> nodeOutBuffer = new LinkedHashMap<>();
	private final Map<Long, Long> nodeOutTimers = new LinkedHashMap<>();	//TODO: Perhaps change to a delayQueue
	private final Map<Integer, DispatcherWorker> workers = new HashMap<>();
	private final Set<RoutingControlWorker> rctrlWorkers = new HashSet<>();
	private final FailureDetector failureDetector = new FailureDetector();
	private final FailureCtrlHandler fctrlHandler = new FailureCtrlHandler();
	private final IProcessingUnit owner;
	private ArrayList<OutputQueue> outputQueues;

	
	private final Object lock = new Object(){};
	
	public Dispatcher(IProcessingUnit owner)
	{
		this.owner = owner;
	}
	
	public FailureCtrl getNodeFailureCtrl()
	{
		FailureCtrl copy = null;
		synchronized(lock)
		{
			copy = new FailureCtrl(nodeFctrl);
		}
		return copy;
	}
	public void setOutputQueues(ArrayList<OutputQueue> outputQueues)
	{
		//TODO: Not sure how well this will work wrt connection updates,
		//threading etc.
		this.outputQueues = outputQueues;
		
		for(int i = 0; i < outputQueues.size(); i++)
		{
			//1 thread per worker - assumes fan-out not too crazy and that we're network bound.
			DispatcherWorker worker = new DispatcherWorker(outputQueues.get(i), owner.getPUContext().getDownstreamTypeConnection().elementAt(i));			
			Thread workerT = new Thread(worker);
			workers.put(i, worker);
			workerT.start();
		}
		
		
	}
	
	public void startRoutingCtrlWorkers()
	{
		for(Integer downOpId : owner.getOperator().getOpContext().getDownstreamOpIdList())
		{
			//1 thread per worker - assumes fan-out not too crazy and that we're network bound.
			RoutingControlWorker worker = new RoutingControlWorker(downOpId);			
			Thread workerT = new Thread(worker);
			rctrlWorkers.add(worker);
			workerT.start();
		}		
	}
	
	public void startFailureDetector()
	{
		Thread fDetectorT = new Thread(failureDetector);
		fDetectorT.start();
	}
	
	public void dispatch(DataTuple dt, ArrayList<Integer> targets)
	{		
		if (targets == null || targets.isEmpty()) { throw new RuntimeException("Logic error."); }
		if (targets.size() > 1) { throw new RuntimeException("TODO."); }

		synchronized(lock)
		{
			if (nodeOutBuffer.containsKey(dt.getPayload().timestamp)) { return; }
			else
			{
				//TODO: Flow control if total q length > max q for round robin?
				nodeOutBuffer.put(dt.getPayload().timestamp, dt);
				nodeOutTimers.put(dt.getPayload().timestamp, System.currentTimeMillis());
			}
		}
		
		for(int i = 0; i<targets.size(); i++){
			int target = targets.get(i);
			EndPoint dest = owner.getPUContext().getDownstreamTypeConnection().elementAt(target);
			// REMOTE ASYNC
			if(dest instanceof AsynchronousCommunicationChannel){
				//Probably just tweak from spu.sendData
				throw new RuntimeException("TODO");
			}
			// REMOTE SYNC
			else if(dest instanceof SynchronousCommunicationChannel){
				workers.get(target).send(dt);
				//outputQueues.get(target).sendToDownstream(dt, dest);
	
			}
			// LOCAL
			else if(dest instanceof Operator){
				//Probably just tweak from spu.sendData
				throw new RuntimeException("TODO");
			}
		}
	}
	
	public FailureCtrl handleFailureCtrl(FailureCtrl fctrl, int dsOpId) 
	{
		fctrlHandler.handleFailureCtrl(fctrl, dsOpId);
		return new FailureCtrl(nodeFctrl);
	}
	
	public void routingChanged()
	{
		synchronized(lock) { lock.notifyAll(); }
	}
	
	public void stop(int target) { throw new RuntimeException("TODO"); }
	
	public class DispatcherWorker implements Runnable
	{
		private final BlockingQueue<DataTuple> tupleQueue = new LinkedBlockingQueue<DataTuple>();	//TODO: Want a priority set perhaps?
		private final OutputQueue outputQueue;
		private final EndPoint dest;
		
		public DispatcherWorker(OutputQueue outputQueue, EndPoint dest)
		{
			this.outputQueue = outputQueue;
			this.dest = dest;
		}
		
		public void send(DataTuple dt)
		{
			tupleQueue.add(dt);
		}
		
		@Override
		public void run()
		{
			while (true)
			{				
				DataTuple nextTuple = null;
				try {
					nextTuple = tupleQueue.take();					
				} catch (InterruptedException e) {
					throw new RuntimeException("TODO: Addition and removal of downstreams.");
				}
				logger.debug("Dispatcher sending tuple to downstream: "+dest.getOperatorId()+",dt="+nextTuple.getLong("tupleId"));
				outputQueue.sendToDownstream(nextTuple, dest);
				logger.debug("Dispatcher sent tuple to downstream: "+dest.getOperatorId()+",dt="+nextTuple.getLong("tupleId"));
			}
		}
		
		public boolean purgeSenderQueue()
		{
			logger.error("TODO: Thread safety?"); 
			boolean changed = false;
			  FailureCtrl currentFctrl = getNodeFailureCtrl();
			  Iterator<DataTuple> qIter = tupleQueue.iterator();
			  while (qIter.hasNext())
			  {
				  long batchId = qIter.next().getPayload().timestamp;
				  if (batchId <= currentFctrl.lw() || currentFctrl.acks().contains(batchId) || currentFctrl.alives().contains(batchId))
				  {
				  	changed = true;
					qIter.remove();
				  }
			  }
			return changed;
		}
		
		public int queueLength()
		{
			//TODO: Thread safe?
			return tupleQueue.size();
		}
	}
	
	public class RoutingControlWorker implements Runnable
	{
		private final int downId;
		
		public RoutingControlWorker(int downId) {
			this.downId = downId;
		}

		@Override
		public void run() {
			// while true
			while (true)
			{
				int totalQueueLength = 0;
				int downIndex = owner.getOperator().getOpContext().getDownOpIndexFromOpId(downId);
				synchronized(lock)
				{
					//measure tuple queue length
					totalQueueLength += workers.get(downIndex).queueLength();
					//measure buf length
					totalQueueLength += bufLength(downId);
				}	
				//Create and send control tuple
				sendQueueLength(totalQueueLength);
				
				//wait for interval
				try {
					Thread.sleep(ROUTING_CTRL_DELAY);
				} catch (InterruptedException e) {}
				logger.warn("TODO: Locking?");
			}
		}
		
		private int bufLength(int opId)
		{
			SynchronousCommunicationChannel cci = owner.getPUContext().getCCIfromOpId(opId, "d");
			if (cci != null)
			{
				IBuffer buffer = cci.getBuffer();
				return buffer.numTuples();
			}
			else { throw new RuntimeException("Logic error."); }
		}
		
		private void sendQueueLength(int queueLength)
		{
			int localOpId = owner.getOperator().getOperatorId();
			ControlTuple ct = new ControlTuple(ControlTupleType.UP_DOWN_RCTRL, localOpId, queueLength);
			logger.debug("Sending control tuple downstream from "+localOpId+" with queue length="+queueLength);
			owner.getOwner().getControlDispatcher().sendDownstream(ct, owner.getOperator().getOpContext().getDownOpIndexFromOpId(downId), false);
		}
	}
	
	/** Resends timed-out tuples/batches, possibly to a different downstream. */
	public class FailureDetector implements Runnable
	{	
		public void run()
		{
			if (outputQueues.size() <= 1) { throw new RuntimeException("No need?"); }
			
			while(true)
			{
				checkForRetransmissions();
				
				//TODO: This will cause a full iteration over node out timers
				// every second!
				synchronized(lock)
				{
					long waitStart = System.currentTimeMillis();
					long now = waitStart;
					
					while (waitStart + RETRANSMIT_CHECK_INTERVAL < now)
					{
						try {
							lock.wait(now - (waitStart + RETRANSMIT_CHECK_INTERVAL));
						} catch (InterruptedException e) {}
						now = System.currentTimeMillis();
					}
				}
			}
		}
		
		public void checkForRetransmissions()
		{
			Set<Long> timerKeys = new HashSet<Long>();
			synchronized(lock)
			{
				//TODO: Really don't want to be holding the lock for this long.
				for(Map.Entry<Long, Long> entry : nodeOutTimers.entrySet())
				{
					if (entry.getValue() + FAILURE_TIMEOUT  < System.currentTimeMillis())
					{
						timerKeys.add(entry.getKey());
						//entry.setValue(System.currentTimeMillis());
					}
				}
			}
			
			//TODO: This is a best effort retransmit. If there is no
			//available downstream for a particular tuple, it will have
			//to wait until the next failure detection timeout. Should
			//possibly tweak to retry in the meantime whenever there is
			//a routing change.
			for (Long tupleKey : timerKeys)
			{
				DataTuple dt = null;
				
				synchronized(lock)
				{
					dt = nodeOutBuffer.get(tupleKey);
				}
								
				if (dt != null)	
				{
					int target = -1;
					ArrayList<Integer> targets = owner.getOperator().getRouter().forward_highestWeight(dt);
					if (targets.size() > 1) { throw new RuntimeException("TODO"); }
					else if (targets.size() == 1) { target = targets.get(0); }

					if (target >= 0)
					{
						workers.get(target).send(dt);
						synchronized(lock)
						{
							//If hasn't been acked
							if (nodeOutTimers.containsKey(dt.getPayload().timestamp))
							{
								//Touch the retransmission timer
								nodeOutTimers.put(dt.getPayload().timestamp, System.currentTimeMillis());
							}
						}
					}
				}
			}					
		}
	}
	
	
	private class FailureCtrlHandler
	{

		public void handleFailureCtrl(FailureCtrl fctrl, int dsOpId) 
		{
			logger.debug("Handling failure ctrl received from "+dsOpId+",nodefctrl="+nodeFctrl+ ", fctrl="+fctrl);
			nodeFctrl.update(fctrl);
			boolean nodeOutChanged = purgeNodeOut();
			refreshNodeOutTimers(fctrl.alives());
			boolean senderOutsChanged = purgeSenderQueues();
			boolean senderBuffersChanged = purgeSenderBuffers();

			synchronized(lock) { lock.notifyAll(); }
		}
		private boolean purgeNodeOut()
		{
			boolean changed = false;
			synchronized(lock)
			{
				Iterator<Long> iter = nodeOutBuffer.keySet().iterator();
				while (iter.hasNext())
				{
					long nxtBatch = iter.next();
					if (nxtBatch <= nodeFctrl.lw() || nodeFctrl.acks().contains(nxtBatch))
					{
						iter.remove();
						nodeOutTimers.remove(nxtBatch);
						changed = true;
					}
				}
			}
			return changed;
		}

		private void refreshNodeOutTimers(Set newAlives)
		{
			synchronized(lock)
			{
				Iterator<Long> iter = nodeFctrl.alives().iterator();
				while (iter.hasNext())
				{
					Long nxtAlive = (Long)iter.next();
					if (newAlives.contains(nxtAlive))
					{
						//Only refresh the newly updated alives
						//TODO: Could perhaps use the time the fctrl was sent instead.
						nodeOutTimers.put(nxtAlive, System.currentTimeMillis());
					}
				}
			}
		}

		private boolean purgeSenderQueues()
		{
			boolean changed = false;
			for (DispatcherWorker worker : workers.values())
			{
				if (worker.purgeSenderQueue()) { changed = true; }
			}
			return changed;
		}

		private boolean purgeSenderBuffers()
		{
			//TODO: How to trim buffer?
			FailureCtrl currentFailureCtrl = getNodeFailureCtrl();
			for (int opId : workers.keySet())
			{
				SynchronousCommunicationChannel cci = owner.getPUContext().getCCIfromOpId(opId, "d");
				if (cci != null)
				{
					IBuffer buffer = cci.getBuffer();
					buffer.trim(currentFailureCtrl);
				}
			}
			return true;
		}
	}
}