package trees.citrus;

public class RCU {
	
	static private int numThreads;
	static private RCUNode rcuTable[]; 

	static public void initRCU(int runNumThreads) {
		numThreads = runNumThreads;
		rcuTable = new RCUNode[runNumThreads];
		for( int i=0; i < runNumThreads ; i++){
	        rcuTable[i] = new RCUNode();
		}
	} 
	
	 static private final ThreadLocal<Integer> id = new ThreadLocal<Integer>();
	 static private final ThreadLocal<Long[]> threadTimes = new ThreadLocal<Long[]>();
	 
	 static public void register(int thread_id){
		 id.set(thread_id);
		 threadTimes.set(new Long[numThreads]);
	 }
	 
	 static public void unregister(){
		 //do nothing
	 }
	 
	 static public void rcuReadLock(){
		 rcuTable[id.get()].lock();
	 }
	 
	 static public void rcuReadUnlock(){
		 rcuTable[id.get()].unlock();
	 }
	 
	 static public void synchronize(){
		 Long[] times = threadTimes.get();  
		 for( int i=0; i < numThreads ; i++){
		        times[i] = rcuTable[i].getTime();
		 }
		 for( int i=0; i < numThreads ; i++){
			 if((times[i] & 1) !=0)  continue; 
			 while(true){
				 long t = rcuTable[i].getTime();
				 if((t & 1) !=0 || t > times[i]){
					 break;
				 }
			 }
		 }
	 }
}
