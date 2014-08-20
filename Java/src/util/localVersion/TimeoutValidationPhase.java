package util.localVersion;

import java.util.ArrayList;
import java.util.List;

import trees.localVersion.Node;

public class TimeoutValidationPhase<K,V>{

	private boolean tryAcquire(final Node<K,V>  node, final Thread self) {
        if (node != null) {
            return node.tryAcquire(self);
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
				if (!tryAcquire(node, self)) {
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
		if(!tryAcquire(local1,self)){
			return false; 
		}
		if(local1!= null) readSet.incrementLocalVersion(local1);
		if(!tryAcquire(local2, self)){
			release(local1);
			return false; 
		}
		if(local2!= null) readSet.incrementLocalVersion(local2);
		if(!readSet.validate(self)){
			release(local1);
			release(local2);
			return false;
		}
		return true;
	}


	public boolean validateReadOnly(ReadSet<K,V> readSet, final Thread self) {
		return readSet.validate(self); 
	}
}
