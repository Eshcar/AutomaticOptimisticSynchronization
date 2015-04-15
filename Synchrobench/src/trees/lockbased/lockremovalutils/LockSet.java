package trees.lockbased.lockremovalutils;

import java.util.HashMap;

public class LockSet{
	
	private HashMap<SpinHeapReentrant,Integer> lockSet; 
	
	public LockSet(){
		lockSet = new HashMap<SpinHeapReentrant,Integer>();
	}

	public LockSet(int size){
		lockSet = new HashMap<SpinHeapReentrant,Integer>(size);
	}
	
	public void clear(){
		lockSet.clear();
	}
	
	public void add(SpinHeapReentrant node){
		assert(node!=null);
		if(lockSet.containsKey(node)){
			int count = lockSet.get(node);
			count = count + 1; 
			lockSet.put(node, count);
		}else{
			lockSet.put(node,1);
		}
	}
	
	public void remove(SpinHeapReentrant node){
		assert(lockSet.containsKey(node));
		int count = lockSet.get(node);
		if (count >1){
			count = count -1; 
			lockSet.put(node, count);
		}else{
			assert(count == 1 );
			lockSet.remove(node);
		}
	}
	
}
