package uk.ac.imperial.lsds.seep.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PerformanceMonitor implements Runnable {
	
	int counter = 0;
	
	private long time, _time = 0L;
	private long dt;
		
	private MultiOperator operator;
	private int size;
		
	private Measurement [] measurements;
		
	static final Comparator<SubQuery> ordering = 
		new Comparator<SubQuery>() {
			
		@Override
		public int compare(SubQuery q, SubQuery p) {
			return (q.getId() < p.getId()) ? -1 : 1;
		}
	};
		
	public PerformanceMonitor (MultiOperator operator) {
		this.operator = operator;
			
		size = operator.getSubQueries().size();
		measurements = new Measurement [size];
		List<SubQuery> L = new ArrayList<SubQuery>(operator.getSubQueries());
		Collections.sort(L, ordering);
		int idx = 0;
		for (SubQuery query : L) {
			System.out.println(String.format("[DBG] [MultiOperator] S %3d", query.getId()));
			measurements[idx++] = 
				new Measurement (query.getId(), 
						(CircularQueryBuffer) query.getTaskDispatcher().getBuffer(), 
						(CircularQueryBuffer) query.getTaskDispatcher().getSecondBuffer(),
						query.getLatencyMonitor());
		}
	}
	
	@Override
	public void run () {
		while (true) {
			
			try { 
				Thread.sleep(1000L); 
			} catch (Exception e) 
			{}
			
			time = System.currentTimeMillis();
			StringBuilder b = new StringBuilder();
			b.append("[DBG]");
			dt = time - _time;
			for (int i = 0; i < size; i++)
				b.append(measurements[i].info(dt));
			b.append(String.format(" q %6d", operator.getExecutorQueueSize()));
			/* if (Utils.HYBRID)
			 *	b.append(String.format(" _q %6d", operator.getSecondExecutorQueueSize())); */
			System.out.println(b);
			_time = time;
			
			 if (counter++ > 60) {
				System.out.println("Done.");
				for (int i = 0; i < size; i++)
					measurements[i].stop();
				break;
			 }
		}
	}
		
	class Measurement {
		
		int id;
		CircularQueryBuffer buffer;
		CircularQueryBuffer secondBuffer;
		
		LatencyMonitor monitor;
		
		long bytes, _bytes = 0;
		double Dt, MBps;
		double MB, _1MB_ = 1048576.0;

		public Measurement (int id, CircularQueryBuffer buffer, CircularQueryBuffer secondBuffer, LatencyMonitor monitor) {
			this.id = id;
			this.buffer = buffer;	
			this.secondBuffer = secondBuffer;	
			
			this.monitor = monitor;
		}
			
		public void stop() {
			monitor.stop();
		}

		@Override
		public String toString () {
			return null;
		}
			
		public String info(long delta) {
			
			String s = "";
			bytes = buffer.getBytesProcessed();
			
			if (secondBuffer != null)
				bytes += secondBuffer.getBytesProcessed();
			
			if (_bytes > 0) {
				Dt = (delta / 1000.0);
				MB = (bytes - _bytes) / _1MB_;
//				if (MB == 0)
//					System.out.println("zero...");
				MBps = MB / Dt;
				s = String.format(" S%03d %10.3f MB/s %10.3f Gbps [%s] ", id, MBps, ((MBps / 1024.) * 8.), monitor);
			}
			_bytes = bytes;
			return s;
		}
	}
}