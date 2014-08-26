package util.localVersion;

import java.util.ArrayList;
import java.util.List;

import trees.localVersion.Node;

public class TimeoutValidationPhase<K,V>{

	private boolean tryAcquire(final Node<K,V>  node, ReadSet<K,V> readSet , final Thread self) {
        if (node != null) {
            if(node.tryAcquire(self)){
            	readSet.incrementLocalVersion(node);
            	return true;
            }else{
            	return false; 
            }
        }
        return true;
    }
	
	private void release(final Node<K,V> node) {
        if (node != null)
           node.release();
    }
	
	private void releaseAll(
			List<Node<K,V>> lockedNodes) {
			for (Node<K,V> node : lockedNodes){
				release(node);
			}
	}

	
	public boolean validate(ReadSet<K,V> readSet, List<Node<K,V>> locals,  final Thread self) {
		List<Node<K,V>> locked =  new ArrayList<Node<K,V>>();
		for(Node<K,V> node :locals){
			if (node!=null) {
				if (!tryAcquire(node, readSet, self)) {
					releaseAll(locked);
					return false;
				}
				locked.add(node);
				readSet.incrementLocalVersion(node); 
			}
		}		
		if(!readSet.validate(self)){
			releaseAll(locked);
			return false;
		}
		return true;
	}
	
	public boolean validateTwo(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, final Thread self) {
		if(!tryAcquire(local1, readSet, self)){
			return false; 
		}
		
		if(!tryAcquire(local2, readSet, self)){
			release(local1);
			return false; 
		}
		
		if(!readSet.validate(self)){
			release(local1);
			release(local2);
			return false;
		}
		return true;
	}
	
	public boolean validateFour(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, 
			Node<K,V> local3, Node<K,V> local4, final Thread self){
		if(!tryAcquire(local1, readSet, self)){
			return false; 
		}
		
		if(!tryAcquire(local2, readSet, self)){
			release(local1);
			return false; 
		}
		
		if(!tryAcquire(local3, readSet, self)){
			release(local1);
			release(local2);
			return false; 
		}
		
		if(!tryAcquire(local4, readSet, self)){
			release(local1);
			release(local2);
			release(local3);
			return false; 
		}
		
		if(!readSet.validate(self)){
			release(local1);
			release(local2);
			release(local3);
			release(local4);
			return false;
		}
		return true;
	}

	
	public boolean validateSix(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, 
			Node<K,V> local3, Node<K,V> local4, Node<K,V> local5, Node<K,V> local6, final Thread self){
		if(!tryAcquire(local1, readSet,self)){
			return false; 
		}
		
		if(!tryAcquire(local2, readSet, self)){
			release(local1);
			return false; 
		}
		
		if(!tryAcquire(local3, readSet, self)){
			release(local1);
			release(local2);
			return false; 
		}
		
		if(!tryAcquire(local4, readSet, self)){
			release(local1);
			release(local2);
			release(local3);
			return false; 
		}
		
		if(!tryAcquire(local5, readSet, self)){
			release(local1);
			release(local2);
			release(local3);
			release(local4);
			return false; 
		}
		
		if(!tryAcquire(local6, readSet, self)){
			release(local1);
			release(local2);
			release(local3);
			release(local4);
			release(local5);
			return false; 
		}
		
		if(!readSet.validate(self)){
			release(local1);
			release(local2);
			release(local3);
			release(local4);
			release(local5);
			release(local6);
			return false;
		}
		return true;
	}

	public boolean validateReadOnly(ReadSet<K,V> readSet, final Thread self) {
		return readSet.validate(self); 
	}
}
