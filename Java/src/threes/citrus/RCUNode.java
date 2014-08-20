package threes.citrus;

import java.util.concurrent.atomic.AtomicLong;

public class RCUNode {
		volatile AtomicLong time;
		
		public RCUNode(){
			time = new AtomicLong();
			time.set(1);
		}
		
		public void lock(){
			time.incrementAndGet();
		}
		
		public void unlock(){
			time.incrementAndGet();
		}

		public long getTime() {
			return time.get();
		}
	
}
