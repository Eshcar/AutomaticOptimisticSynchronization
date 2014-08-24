package util.globalVersion;


import trees.globalVersion.Node;

public class GlobalVersionValidationPhase<K,V>{


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

	public int validateTwo(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, final Thread self,  int startVersion) {
		int prevVersion = startVersion; 
		int writeVersion = prevVersion +1;
		
		if(!tryAcquire(local1,self)){
			return -1; 
		}
		
		if(!tryAcquire(local2, self)){
			release(local1);
			if(local1!=null) { assert(local1.lockedBy()!=self);}
			return -1; 
		}
		while(!GlobalVersion.tryIncrementVersion(prevVersion, writeVersion)){			
			prevVersion= GlobalVersion.getVersion();
			writeVersion = prevVersion +1;
			
			if(!readSet.validate(startVersion,self)){
				release(local1);
				release(local2);
				if(local1!=null) { assert(local1.lockedBy()!=self);}
				if(local2!=null) { assert(local2.lockedBy()!=self);}
				return -1;
			}
			
		}
		if(local1!=null) { local1.changeVersion(writeVersion); }
		if(local2!=null) { local2.changeVersion(writeVersion); }
		return writeVersion;
	}

}
