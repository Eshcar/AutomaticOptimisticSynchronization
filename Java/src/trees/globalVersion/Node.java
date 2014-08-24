package trees.globalVersion;


import util.globalVersion.GlobalVersion;
import util.globalVersion.SpinHeapReentrant;

public class Node<K, V> extends SpinHeapReentrant {
	/**
	 * Default 
	 */
	private static final long serialVersionUID = 1L;

	enum Direction {
        LEFT, RIGHT
    }

	
	public Node(K key, V value) {
		this.key = key;
		this.value = value;
		this.left = null;
		this.right = null;
		//this.version = 0; 
	}
	
	//private int version; 
	final K key;
	V value;
	Node<K, V> left;
	Node<K, V> right; 

	/*
	public int getVersion(){
	    	return this.version; 
	}*/
	
	void setChild(final Direction dir, final Node<K,V> n, final Thread self, int newVersion) {
        if (n != null)
            n.addIncoming(self);  
        if(this.lockedBy()!= self){
        	assert(false);
        }
        /*
        if(newVersion < this.version ){
        	assert(false);
        }
        this.version = newVersion;*/
        //this.version = GlobalVersion.incrementVersion();
        if (dir == Direction.LEFT) {
            if (left != null)
                left.removeIncoming(self);
            left = n;
        } else {
            if (right != null)
                right.removeIncoming(self);
            right = n;
        }
    }
	
	
	void setValue(V val, int newVersion){
		/*
		if(newVersion < this.version){
        	assert(false);
		}
		this.version = newVersion;*/
		//this.version = GlobalVersion.incrementVersion();
		this.value = val; 
	}
	
}