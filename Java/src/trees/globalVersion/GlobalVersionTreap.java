package trees.globalVersion;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import trees.Map;
import trees.globalVersion.Node;
import trees.globalVersion.Node.Direction;
import util.Error;
import util.FastSimpleRandom;
import util.globalVersion.GlobalVersion;
import util.globalVersion.GlobalVersionValidationPhase;
import util.globalVersion.GlobalVersionReadPhase;
import util.globalVersion.GlobalVersionReadWritePhase;
import util.globalVersion.ReadSet;

public class GlobalVersionTreap<K,V> implements Map<K,V>{

	@SuppressWarnings("hiding")
	private class TreapNode<K,V> extends Node<K,V>{

		/**
		 * Default 
		 */
		private static final long serialVersionUID = 1L;
		
		final int priority;
		 
		TreapNode(final K key, final V value, final int priority) {
	            super(key, value); 
	            this.priority = priority;
	    }
		
	}
	
	private final ThreadLocal<Thread> self = new ThreadLocal<Thread>(){
        @Override
        protected Thread initialValue()
        {
            return Thread.currentThread();
        }
    };
    
    private final ThreadLocal<Error> threadError = new ThreadLocal<Error>(){
    	@Override
        protected Error initialValue(){
    		return new Error();
    	}
    };
    
    private final ThreadLocal<ReadSet<K,V>> threadReadSet = new ThreadLocal<ReadSet<K,V>>(){
        @Override
        protected ReadSet<K,V> initialValue()
        {
            return new ReadSet<K,V>(); 
        }
    };
	
	private final GlobalVersionReadPhase<K, V> readPhaseStrategy = new GlobalVersionReadPhase<K, V>();
	private final GlobalVersionValidationPhase<K, V> validationStrategy = new GlobalVersionValidationPhase<K, V>();
	private final GlobalVersionReadWritePhase<K, V> readWritePhaseStrategy = new GlobalVersionReadWritePhase<K, V>();
	
    private static class KeyCmp<K> implements Comparable<K> {
        private final Comparator<K> cmp;
        private final K key;

        private KeyCmp(final Comparator<K> cmp, final K key) {
            this.cmp = cmp;
            this.key = key;
        }

        public int compareTo(final K rhs) {
            return cmp.compare(key, rhs);
        }
    }

    final Comparator<K> cmp;
    final TreapNode<K,V> rootHolder = new TreapNode<K,V>(null, null, 0);

    public GlobalVersionTreap() {
        this(null);
    }

    public GlobalVersionTreap(final Comparator<K> cmp) {
        this.cmp = cmp;
    }

    public void clear() {
        rootHolder.right = null;
    }

    @SuppressWarnings("unchecked")
    private Comparable<K> comparable(final Object key) {
        return (cmp == null) ? (Comparable<K>) key : new KeyCmp<K>(cmp, (K) key);
    }
    

    public V get(final Object key) {    
    	final Comparable<K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = getImpl(k, err);
			if(!err.isSet()) break;
		}
		return value; 
    }

    private V getImpl(final Comparable<K> key, Error err){
    	ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
        int startVersion = GlobalVersion.getVersion();
        
        TreapNode<K,V> parent;
        TreapNode<K,V> node;
  
        parent = (TreapNode<K, V>) readPhaseStrategy.readGlobalRef(this.rootHolder,readSet,err,startVersion);
        if(err.isSet()) return null;
        node = (TreapNode<K, V>) readPhaseStrategy.readRef(parent.right,parent,readSet,err,startVersion);
    	if(err.isSet()) return null;
        
    	while (node != null) {
            final int c = key.compareTo(node.key);
            readPhaseStrategy.readValFromNode(node, readSet,err,startVersion);
			if(err.isSet()) return null;
			
            if (c == 0) {
                break;
            }
            parent = node;
            if (c < 0) {
                node = (TreapNode<K, V>) readPhaseStrategy.readRef(node.left,node,readSet,err,startVersion);
                if(err.isSet()) return null;
            }
            else {
                node = (TreapNode<K, V>) readPhaseStrategy.readRef(node.right,node,readSet,err,startVersion);
                if(err.isSet()) return null;
            }
        }
    	if(node!=null){
			V value = node.value;
			readPhaseStrategy.readValFromNode(node, readSet,err,startVersion);
			if(err.isSet()) return null;	
			//if (!validationStrategy.validateReadOnly(readSet, self)) err.set();
			return value;
		}
    	//if (!validationStrategy.validateReadOnly(readSet, self)) err.set();
		return null;
    }

    public V put(K key, V val){
    	final Comparable<K> k = comparable(key);
		V value;
		Error err = threadError.get();
		while(true){
			err.clean();
			value = putImpl(key, k, val, self.get(), err);
			if(!err.isSet()) break; 
		}
		return value;     
	}
    
    private V putImpl(final K key, final Comparable<K> cmp, final V value,final Thread self, Error err){      
    	ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
        int startVersion = GlobalVersion.getVersion();
        
        V prevValue = null;      
        final int prio = FastSimpleRandom.nextInt();
        
        //Read-only phase//
        TreapNode<K,V> parent = (TreapNode<K, V>) readPhaseStrategy.readGlobalRef(this.rootHolder,readSet,err, startVersion);
        if(err.isSet()) return null; 
        TreapNode<K,V> node = (TreapNode<K, V>) readPhaseStrategy.readRef(parent.right,parent,readSet,err, startVersion);
    	if(err.isSet()) return null;
    	
    	Direction dir = Direction.RIGHT;
    	
    	int cmpRes;
    	
        while (node != null && prio <= node.priority) { //priority is final, no need to validate??? 
            cmpRes = cmp.compareTo(node.key);
            readPhaseStrategy.readValFromNode(node, readSet,err, startVersion);
			if(err.isSet()) return null;
			
            if (cmpRes == 0) {
                break;
            }        
            parent = node;
            if (cmpRes < 0) {
            	node = (TreapNode<K, V>) readPhaseStrategy.readRef(node.left,node,readSet,err, startVersion);
                if(err.isSet()) return null;  
                dir = Direction.LEFT;
            }
            else {
            	node = (TreapNode<K, V>) readPhaseStrategy.readRef(node.right,node,readSet,err, startVersion);
                if(err.isSet()) return null;              
                dir = Direction.RIGHT;
            }
           
        }
        
        
        //Validation phase//
      	int writeVersion = validationStrategy.validateTwo(readSet, parent, node, self, startVersion);
      	if(writeVersion == -1){
      		err.set();
      		return null;
      	}
        
		TreapNode<K,V> x = (TreapNode<K, V>) readWritePhaseStrategy.acquire(new TreapNode<K,V>(key, value, prio), self, writeVersion);
		TreapNode<K,V> lessParent = null;
		TreapNode<K,V> moreParent = null;
        Direction lessDir;
        Direction moreDir;      
        
        if (node == null){
            // simple
            parent.setChild(dir, x, self, writeVersion);
        }
        else {
            final int c0 = cmp.compareTo(node.key);
            if (c0 == 0) {
                // TODO: remove this node, then insert later with the new priority (prio must be > node.priority)

                // The update logic results in the post-update priority being
                // the minimum of the existing entry's and x's.  This skews the
                // uniform distribution slightly.
            	
            	//this is the old version:
            	prevValue = node.value;
            	node.setValue(value, writeVersion);
            }
            else {
                // TODO: update the existing node if it is a child of the current node
                parent.setChild(dir, x, self, writeVersion); // add the new node
                if (c0 < 0) {
                    x.setChild(Direction.RIGHT, node, self, writeVersion);  
                    moreParent = (TreapNode<K, V>) readWritePhaseStrategy.assign(moreParent, node, self, writeVersion);
                    moreDir = Direction.LEFT;
                    lessParent = (TreapNode<K, V>) readWritePhaseStrategy.assign(lessParent, x, self, writeVersion);
                    lessDir = Direction.LEFT;
                    node = (TreapNode<K, V>) readWritePhaseStrategy.assign(node, node.left, self, writeVersion);
                    
                    moreParent.setChild(Direction.LEFT, null, self, writeVersion);
                } else {
                	x.setChild(Direction.LEFT, node, self, writeVersion); 
                    lessParent = (TreapNode<K, V>) readWritePhaseStrategy.assign(lessParent, node, self, writeVersion);
                    lessDir = Direction.RIGHT;
                    moreParent = (TreapNode<K, V>) readWritePhaseStrategy.assign(moreParent, x, self, writeVersion);
                    moreDir = Direction.RIGHT;
                    node = (TreapNode<K, V>) readWritePhaseStrategy.assign(node, node.right, self, writeVersion);
                   
                    lessParent.setChild(Direction.RIGHT,null,self, writeVersion);
                }

                while (node != null) {
                    cmpRes = cmp.compareTo(node.key);
                    if (cmpRes == 0) {
                        lessParent.setChild(lessDir, node.left, self, writeVersion);
                        moreParent.setChild(moreDir, node.right, self, writeVersion);                
                        node.setChild(Direction.LEFT, null, self, writeVersion); //added 4/8/2014
                        node.setChild(Direction.RIGHT, null, self, writeVersion);
                        prevValue = node.value;
                        break;
                    }
                    else if (cmpRes < 0) {
                        moreParent.setChild(moreDir, node, self, writeVersion);
                        moreParent = (TreapNode<K, V>) readWritePhaseStrategy.assign(moreParent, node, self, writeVersion);
                        moreDir = Direction.LEFT;
                        node = (TreapNode<K, V>) readWritePhaseStrategy.assign(node, moreParent.left, self, writeVersion);
                        moreParent.setChild(Direction.LEFT, null, self, writeVersion);
                    }
                    else {
                        lessParent.setChild(lessDir, node, self, writeVersion);
                        lessParent = (TreapNode<K, V>) readWritePhaseStrategy.assign(lessParent, node, self, writeVersion);
                        lessDir = Direction.RIGHT;
                        node = (TreapNode<K, V>) readWritePhaseStrategy.assign(node, lessParent.right, self, writeVersion);
                        lessParent.setChild(Direction.RIGHT, null, self, writeVersion);
                    }
                }
            }
        }
        readWritePhaseStrategy.release(parent);
        readWritePhaseStrategy.release(node);
        readWritePhaseStrategy.release(moreParent);
        readWritePhaseStrategy.release(lessParent);
        readWritePhaseStrategy.release(x);
        return prevValue;
    }

    public V remove(K key){
    	final Comparable<K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = removeImpl(k, self.get(), err);
			if(!err.isSet()) break; 
		}
		return value;  
	}

    private V removeImpl(final Comparable<K> cmp ,final Thread self,Error err){
    	ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
        int startVersion = GlobalVersion.getVersion();
        
        TreapNode<K,V> parent = (TreapNode<K, V>) readPhaseStrategy.readGlobalRef(this.rootHolder,readSet,err, startVersion);
        if(err.isSet()) return null;
        TreapNode<K,V> node = (TreapNode<K, V>) readPhaseStrategy.readRef(parent.right,parent,readSet,err, startVersion);
    	if(err.isSet()) return null;
    	
    	 Direction dir = Direction.RIGHT;
         V prevValue = null; 
        
        while (node != null) {
            final int c = cmp.compareTo(node.key);
            readPhaseStrategy.readValFromNode(node, readSet,err, startVersion);
			if(err.isSet()) return null;
            if (c == 0) {
            	prevValue = node.value;
            	readPhaseStrategy.readValFromNode(node, readSet,err, startVersion);
    			if(err.isSet()) return null;
                break;
            }
            parent = node;
            if (c < 0) {
            	node = (TreapNode<K, V>) readPhaseStrategy.readRef(node.left,node,readSet,err, startVersion);
                if(err.isSet()) return null;  
                dir = Direction.LEFT;
            }
            else {
            	node = (TreapNode<K, V>) readPhaseStrategy.readRef(node.right,node,readSet,err, startVersion);
                if(err.isSet()) return null;  
                dir = Direction.RIGHT;
            }
        }
        
        //Validation phase//
      	int writeVersion = validationStrategy.validateTwo(readSet, parent, node, self, startVersion);
      	if(writeVersion == -1){
      		err.set();
      		return null;
      	}
        
		TreapNode<K,V> nL = null;
        TreapNode<K,V> nR = null;
		
        while (node != null) {
            if (node.left == null) {
                parent.setChild(dir, node.right, self, writeVersion);
                break;
            }
            else if (node.right == null) {
                parent.setChild(dir, node.left, self, writeVersion);
                break;
            }
            else {
                nL = (TreapNode<K, V>) readWritePhaseStrategy.assign(nL,node.left,self, writeVersion);
                nR = (TreapNode<K, V>) readWritePhaseStrategy.assign(nR,node.right,self, writeVersion);
            
                if (nL.priority > nR.priority) {
                	TreapNode<K, V> nLR =  (TreapNode<K, V>) readWritePhaseStrategy.acquire(nL.right,self, writeVersion); // ???
                    node.setChild(Direction.LEFT, nLR, self, writeVersion);
                    parent.setChild(dir, nL, self, writeVersion);
                    nL.setChild(Direction.RIGHT, node, self, writeVersion);
             
                    parent = (TreapNode<K, V>) readWritePhaseStrategy.assign(parent,nL,self, writeVersion);
                    dir = Direction.RIGHT;
                    readWritePhaseStrategy.release(nLR); //???
                }
                else {
                    node.setChild(Direction.RIGHT, nR.left, self, writeVersion);
                    parent.setChild(dir, nR, self, writeVersion);
                    nR.setChild(Direction.LEFT, node, self, writeVersion);
               
                    parent = (TreapNode<K, V>) readWritePhaseStrategy.assign(parent,nR,self, writeVersion);
                    dir = Direction.LEFT;
                }
            }
        }

        // code that prevents treeness violation for an object that happens to
        // be unreachable
        if (node != null) {
            node.setChild(Direction.LEFT, null, self, writeVersion);
            node.setChild(Direction.RIGHT, null, self, writeVersion);
        }
        readWritePhaseStrategy.release(parent);
        readWritePhaseStrategy.release(node);
        readWritePhaseStrategy.release(nL);
        readWritePhaseStrategy.release(nR);
        return prevValue;
    }

    private void append(final Node<K,V> node, final ArrayList< java.util.Map.Entry<K,V>> buffer) {
        if (node == null) {
            return;
        }
        append(node.left, buffer);
        buffer.add(new AbstractMap.SimpleImmutableEntry<K,V>(node.key, node.value));
        final Node<K,V> right = node.right;
        append(right, buffer);
	
    }

    public List< java.util.Map.Entry<K,V>> toList() { 
        final ArrayList< java.util.Map.Entry<K,V>> buffer = new ArrayList< java.util.Map.Entry<K,V>>();
        append(rootHolder.right, buffer);
		return buffer;
    }

    public String toString() {
        return toList().toString();
    }

    
    private boolean validatePriority(TreapNode<K,V> node, int priority){
    	if (node!=null){
    		if(node.priority > priority) return false;
    		return validatePriority((TreapNode<K, V>) node.left,node.priority) && validatePriority((TreapNode<K, V>) node.right, node.priority);
    	}
    	return true; 
    }
    
    private boolean validateKey(Node<K,V> node){
    	if (node==null) return true; 
    	Node<K,V> ln = node.left;
 	    Node<K,V> rn = node.right;
 	   if ( ( ln != null && (comparable(ln.key).compareTo(node.key) >= 0) )
 	  	      || ( rn != null && (comparable(rn.key).compareTo(node.key) <= 0) ) )
 	  	    {
 		   		return false; 
 	  	    }
    	return validateKey(ln) && validateKey(rn); 
    }   	
    
	@Override
	public boolean validate() {
		// TODO Auto-generated method stub
		return validatePriority((TreapNode<K, V>) rootHolder.right,Integer.MAX_VALUE) && validateKey(rootHolder.right);
	}

	@Override
	public HashSet<K> getAllKeys() {
		HashSet<K> keys = new HashSet<K>();
		getAllKeysImpl(rootHolder.right,keys);
		return keys;
	}

	private void getAllKeysImpl(Node<K, V> node, HashSet<K> keys) {
		if(node!=null){
			keys.add(node.key);
			getAllKeysImpl(node.left, keys);
			getAllKeysImpl(node.right, keys);
		}
		
	}

}

