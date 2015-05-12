package trees.lockbased.lockremovalutils;

import java.util.HashMap;
import java.util.Map.Entry;

public final class LockSet{
	
	private final static int LOCK_SET_SIZE = 256; 
	private final int actualSize;
	private SpinHeapReentrant lockSetObjects[]; 
	private int nextSlot; 
	private int count; //the actual number of elements in array
	private HashMap<SpinHeapReentrant,Integer> lockSet = new HashMap<SpinHeapReentrant,Integer>();
	private HashMap<SpinHeapReentrant,Integer> successfullyLocked = new HashMap<SpinHeapReentrant,Integer>(); 
	
	public LockSet(){
		lockSetObjects = new SpinHeapReentrant[LOCK_SET_SIZE];
		nextSlot = 0; 
		count = 0; 
		actualSize = LOCK_SET_SIZE;
	}

	public LockSet(int size){
		lockSetObjects = new SpinHeapReentrant[size];
		nextSlot = 0; 
		count = 0; 
		actualSize = size; 
	}
	
	public void clear(){
		nextSlot = 0; 
		count = 0; 
		if(!lockSet.isEmpty()){
			lockSet.clear();
		}
	}
	
	public void add(SpinHeapReentrant node){
		assert(node!=null);
		
		//Optimization: try to use the beginning of the array as much as possible
		//Look at the first two slots
		if(nextSlot > 1){	
			if(lockSetObjects[0] == null){
				lockSetObjects[0] = node;
				count++;
				return;
			}
			if(lockSetObjects[1] == null){
				lockSetObjects[1] = node;
				count++;
				return;
			}
		}
		
		
		//try to just add to the end of the array
		if(nextSlot < actualSize){
			lockSetObjects[nextSlot] = node;
			nextSlot++;
			count++;
			return;
		}
		assert(nextSlot==actualSize);
		if(count<actualSize){ //there is an empty slot in the array
			//find an empty spot in the array: (according to count) 
			for(int i=0; i<actualSize; i++){
				if (lockSetObjects[i]==null){
					//found an empty spot
					lockSetObjects[i] = node;
					count++;
					return;
				}
			}
		}
		assert(count == actualSize);
		assert(false); //make sure we have the best size!
		//array is full, use the hash set
		//In the hash set we also add a counter for reentrant
		if(lockSet.containsKey(node)){
			int times = lockSet.get(node);
			times = times + 1; 
			lockSet.put(node, times);
		}else{
			lockSet.put(node,1);
		}
	}
	
	public void remove(SpinHeapReentrant node){
		//try to find in the array 
		for(int i=0; i<nextSlot; i++){
			if (node.equals(lockSetObjects[i])){
				//found the node
				lockSetObjects[i] = null;
				//check if largest need to change
				if(i==nextSlot-1){
					nextSlot--; 
				}
				count--;
				return;
			}
		}
		//not found in the array, check hash
		//assert(lockSet.containsKey(node));
		int count = lockSet.get(node);
		if (count >1){
			count = count -1; 
			lockSet.put(node, count);
		}else{
			assert(count == 1 );
			lockSet.remove(node);
		}
	}
	
	public boolean tryLockAll(Thread self){
		//first lock nodes in the array- easy
		for(int i=0; i< nextSlot ; i++){
			if(lockSetObjects[i]!=null){ //node might be null !
				if(!lockSetObjects[i].tryAcquire(self)){
					//failed getting one of the locks
					//unlock [0,..,i-1]
					for(int j =0; j<= i-1 ; j++ ){
						if(lockSetObjects[j]!=null){
							lockSetObjects[j].release();
						}
					}
					return false; 
				}
			}
		}
		//if needed, lock nodes in the lockSet
		if(!lockSet.isEmpty()){
			//use another set for nodes successfully locked. 
			SpinHeapReentrant node; 
			for(Entry<SpinHeapReentrant, Integer> entry : lockSet.entrySet()) {
				node = entry.getKey();
			    int times = entry.getValue();
			    
			    if(!node.tryAcquire(self)){
		    		//free the array
		    		for(int j=0; j< nextSlot ; j++){
		    			if(lockSetObjects[j]!=null){
							lockSetObjects[j].release();
						}
		    		}
		    		//free previous nodes from the lockSet
		    		for(Entry<SpinHeapReentrant, Integer> locked : successfullyLocked.entrySet()){
		    			node = locked.getKey();
		    			times = locked.getValue();
		    			for(int k=0; k< times; k++){
		    				node.release();
		    			}
		    		}
		    		successfullyLocked.clear();
		    		return false; 
		    	}
			    successfullyLocked.put(node, times);
			    for(int i=1; i<times; i++){
			    	//re-acquire according to times
			    	node.reacquire();
			    }
			}	
			successfullyLocked.clear();
		}
		return true; 
	}

	public void releaseAll() {
		// free locks from the array
		for(int j=0; j< nextSlot ; j++){
			if(lockSetObjects[j]!=null){
				lockSetObjects[j].release();
			}
		}
		//free nodes from the lockSet
		if(!lockSet.isEmpty()){
			for(Entry<SpinHeapReentrant, Integer> entry : lockSet.entrySet()) {
				SpinHeapReentrant node = entry.getKey();
			    int times = entry.getValue();
			    for(int i=0; i<times; i++){
			    	//release according to times
			    	node.release();
			    }
			}
		}
	}
	
	//for debug
	public int getCount() {
		if(lockSet.isEmpty()){
			return count; 
		}else{
			return count + lockSet.size();
		}
	}
	
}
