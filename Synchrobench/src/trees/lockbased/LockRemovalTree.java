package trees.lockbased;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;




import trees.lockbased.LogicalOrderingAVL.AVLMapNode;
import trees.lockbased.lockremovalutils.Error;
import trees.lockbased.lockremovalutils.ReadSet;
import trees.lockbased.lockremovalutils.SpinHeapReentrant;
import contention.abstractions.CompositionalMap;

public class LockRemovalTree<K,V> implements CompositionalMap<K, V>{
	private final long LIMIT = 2000; 
	
	private final Comparator<K> comparator;
	//private final K min; 
	private final Node<K, V> root; 
	
	/*Constructors*/
	
	public LockRemovalTree() {	
		//this.min = Integer.MIN_VALUE;
		this.root = new Node(Integer.MAX_VALUE,null); 
        this.comparator = null;   
    }
	
	public LockRemovalTree(final K min, final K max) {	
		//this.min = min;
		this.root = new Node<K, V>(max,null); 
        this.comparator = null;   
    }

    public LockRemovalTree(final K min, final K max, final Comparator<K> comparator) {
    	//this.min = min;
    	this.root = new Node<K, V>(max,null); 
        this.comparator = comparator;
    }
    
    /*Node*/
    private static class Node<K, V> extends SpinHeapReentrant {
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
    	}
    	
    	final K key;
    	V value;
    	Node<K, V> left;
    	Node<K, V> right; 

    	void setChild(final Direction dir, final Node<K,V> n, final Thread self) {
            if (n != null)
                n.addIncoming(self);
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
    }
    
    /*Thread Locals*/
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
    
    @SuppressWarnings("unchecked")
	private Comparable<K> comparable(final Object object) {

		if (object == null) throw new NullPointerException();
		if (comparator == null) return (Comparable<K>)object;

		return new Comparable<K>() {
			final Comparator<K> cmp = comparator;
			final K obj = (K) object;

			public int compareTo(final K other) { 
				return cmp.compare(obj, other); 
			}
		};
	}
    
    /*Helper functions*/
    
    private Node<K,V> acquire(Node<K,V> node, Thread self) {
		if (node != null) {
            node.acquire(self);
        }
        return node;
	}
	
	private void release(Node<K,V> node) {
		if (node != null){
	           node.release();
		}
	}
	
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
	
	private Node<K,V> readRef(Node<K,V> newNode,ReadSet<K,V> readSet, Error err) {
		
		if(newNode!=null){
			int version = newNode.getVersion();
			if(newNode.isLocked()){
				err.set();
				return null;
			}
			
			readSet.add(newNode, version);
		}
		
		return newNode;
	}
	
	private boolean validateTwo(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, final Thread self) {
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
	
	private boolean validateFour(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, 
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

	
	private boolean validateSix(ReadSet<K,V> readSet, Node<K,V> local1, Node<K,V> local2, 
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
    
	/*Map functions*/
	
	@Override
	public boolean containsKey(Object key) {
		if(get(key)!=null){
			return true;
		}
		return false;
	}

	@Override
	public boolean containsValue(Object value) {
		throw new RuntimeException("unimplemented method");
		// TODO Auto-generated method stub
		//return false;
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		 throw new RuntimeException("unimplemented method");
		// TODO Auto-generated method stub
		//return null;
	}

	@Override
	public V get(Object key) {
		final Comparable<K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = getImpl(k, self.get(), err);
			if(!err.isSet()) break;
		}
		return value; 
	}

	private V getImpl(Comparable<K> k, final Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		
		Node<K, V> curr = readRef(this.root,readSet ,err);
		if(err.isSet()) return null;
		while(curr!=null){
			int res = k.compareTo(curr.key);
			if(res == 0) break; 
			if(res > 0){ //key > x.key
				curr=readRef(curr.right,readSet,err);
				if(err.isSet()) return null;
			}else{
				curr=readRef(curr.left,readSet,err);
				if(err.isSet()) return null;
			}
			if(count++ == LIMIT){
				if (!validateReadOnly(readSet, self)){
					err.set();
					return null;
				}
			}
		}

		if(curr!=null){
			V value = curr.value;
			if (!validateReadOnly(readSet, self)) err.set();
			return value;
		}
		if (!validateReadOnly(readSet, self)) err.set();
		return null;
	}

	@Override
	public boolean isEmpty() {
		return root.left==null;
	}

	@Override
	public Set<K> keySet() {
		// Non Linearizable
		Set<K> keys = new HashSet<K>();
		doGetAllKeys(this.root.left,keys);
		return keys; 
	}
	
	private void doGetAllKeys(Node<K, V> node, Set<K> keys ){
		if(node!=null){
			keys.add(node.key);
			doGetAllKeys(node.left, keys);
			doGetAllKeys(node.right, keys);
		}
	}

	@Override
	public V put(K key, V val) {
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
	
	private V putImpl(K key, final Comparable<K> k , V val , final Thread self, Error err) {		
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		V oldValue = null; 
		
		//Read-only phase//
		Node<K, V> prev = null;
		Node<K, V> curr = readRef(this.root,readSet,err); 
		if(err.isSet()) return null;
		int res = -1;
		while(curr!=null){
			prev = curr;
			res = k.compareTo(curr.key);
			if(res == 0){
				oldValue = prev.value;
				break; 
			}
			if(res > 0){ //key > x.key
				curr= readRef(curr.right,readSet,err);
				if(err.isSet()) return null;
			}else{
				curr= readRef(curr.left,readSet,err);
				if(err.isSet()) return null;
			}
			if(count++ == LIMIT){
				if (!validateReadOnly(readSet, self)){
					err.set();
					return null;
				}
			}
		}
		
		//Validation phase
		if(!validateTwo(readSet,prev,curr,self)){
			err.set();
			return null; 
		}
		
		//Read-write phase//
		if(res == 0){
			prev.value = val;
			release(prev);
			release(curr);
			return oldValue;
		}
		Node<K, V> node = acquire(new Node<K, V>(key,val),self);
		if (res > 0 ) { 
			prev.setChild(Node.Direction.RIGHT,node,self); 
		} else {
			prev.setChild(Node.Direction.LEFT,node,self); 
		}
		release(prev);
		release(curr);
		release(node);
		assert(prev ==null || prev.lockedBy()!=self);
		assert(curr ==null || curr.lockedBy()!=self);
		assert(node ==null || node.lockedBy()!=self);
		return oldValue;
	}
	
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		//NOT LINEARIZABLE
		Set<? extends K> keysToAdd = m.keySet();
		for(K key : keysToAdd){
			put(key, m.get(key));
		}
	}

	@Override
	public V remove(Object key) {
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

	private V removeImpl(Comparable<K> k, final Thread self, Error err){
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		V oldValue = null;		
		
		//Read-only phase//
		Node<K, V> prev = null;
		Node<K, V> curr = readRef(this.root,readSet,err); 
		int res = -1;
		while(curr!=null){			
			res = k.compareTo(curr.key);	
			if(res == 0){
				oldValue = curr.value;			
				break; 
			}
			prev=curr;
			if(res > 0){ //key > x.key
				curr= readRef(curr.right,readSet,err); 
				if(err.isSet()) return null;
			}else{
				curr= readRef(curr.left,readSet,err);
				if(err.isSet()) return null;
			}
			if(count++ == LIMIT){
				if (!validateReadOnly(readSet, self)){
					err.set();
					return null;
				}
			}
		}
		if(res!= 0) {
			if(!validateReadOnly(readSet, self)) err.set();
			return oldValue;
		}
		
		
		Node<K, V> currL = readRef(curr.left,readSet,err); 
		if(err.isSet()) return null;
		Node<K, V> currR = readRef(curr.right,readSet,err);
		if(err.isSet()) return null;
		
		boolean isLeft = prev.left == curr; 
		if (currL == null){ //no left child
			
			//Validation phase//
			if(!validateFour(readSet,prev,curr,currL, currR, self)){
				err.set();
				return null; 
			}			
			
			if(isLeft){
				prev.setChild(Node.Direction.LEFT,currR,self);
			}else {
				prev.setChild(Node.Direction.RIGHT,currR,self);
			}
			curr.setChild(Node.Direction.RIGHT,null,self);
			
			release(prev);
			release(curr);
			release(currL);
			release(currR);
			return oldValue;
		} 
		
		
		if (currR == null){ //no right child
			
			//Validation phase//
			if(!validateFour(readSet,prev,curr,currL, currR, self)){
				err.set();
				return null; 
			}	
			
			if(isLeft){
				prev.setChild(Node.Direction.LEFT,currL,self);
			}else {
				prev.setChild(Node.Direction.RIGHT,currL,self);
			}
			curr.setChild(Node.Direction.LEFT,null,self);
			
			release(prev);
			release(curr);
			release(currL);
			release(currR);
			return oldValue;
			
		}   
		//both children
		Node<K, V> prevSucc =  curr; 
		Node<K, V> succ = currR;
		Node<K, V> succL =  readRef(succ.left,readSet,err); 
		if(err.isSet()) return null;
		
		while(succL != null){
			prevSucc = succ;
			succ = succL;
			succL =  readRef(succ.left,readSet,err);
			if(err.isSet()) return null;
		}
		
		//Validation phase//
		if(!validateSix(readSet,prev,curr,currL,currR,prevSucc,succ, self)){
			err.set();
			return null; 
		}	
		
		if (prevSucc != curr){	
			Node<K, V> succR=  acquire(succ.right,self); 
			prevSucc.setChild(Node.Direction.LEFT,succR,self);				
			succ.setChild(Node.Direction.RIGHT,currR,self);
			release(succR);
		}
		succ.setChild(Node.Direction.LEFT,currL,self);
		if (isLeft){
			prev.setChild(Node.Direction.LEFT,succ,self); 
		} else{
			prev.setChild(Node.Direction.RIGHT,succ,self); 
		}
		
		curr.setChild(Node.Direction.RIGHT,null,self);
		curr.setChild(Node.Direction.LEFT,null,self);
		release(prevSucc);
		release(succ);
		release(succL);		
		release(prev);
		release(curr);
		release(currL);
		release(currR);
		
		assert(prev ==null || prev.lockedBy()!=self );
		assert(curr ==null || curr.lockedBy()!=self);
		assert(currL ==null || currL.lockedBy()!=self);
		assert(currR ==null || currR.lockedBy()!=self);
		return oldValue; 
	}
	
	@Override
	public Collection<V> values() {
		 throw new RuntimeException("unimplemented method");
		// TODO Auto-generated method stub
		//return null;
	}

	@Override
	public V putIfAbsent(K key, V val) {
		final Comparable<K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = putIfAbsentImpl(key, k, val, self.get(), err);
			if(!err.isSet()) break;
		}
		return value; 
	}

	private V putIfAbsentImpl(K key, final Comparable<K> k , V val , final Thread self, Error err) {		
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		V oldValue = null; 
		
		//Read-only phase//
		Node<K, V> prev = null;
		Node<K, V> curr = readRef(this.root,readSet,err); 
		if(err.isSet()) return null;
		int res = -1;
		while(curr!=null){
			prev = curr;
			res = k.compareTo(curr.key);
			if(res == 0){
				//key was found
				oldValue = prev.value;
				if (!validateReadOnly(readSet, self)){
					err.set();
					return null;
				}				
				return oldValue; 
			}
			if(res > 0){ //key > x.key
				curr= readRef(curr.right,readSet,err);
				if(err.isSet()) return null;
			}else{
				curr= readRef(curr.left,readSet,err);
				if(err.isSet()) return null;
			}
			if(count++ == LIMIT){
				if (!validateReadOnly(readSet, self)){
					err.set();
					return null;
				}
			}
		}
		
		//Validation phase
		if(!validateTwo(readSet,prev,curr,self)){
			err.set();
			return null; 
		}
		
		//Read-write phase//
	
		Node<K, V> node = acquire(new Node<K, V>(key,val),self);
		if (res > 0 ) { 
			prev.setChild(Node.Direction.RIGHT,node,self); 
		} else {
			prev.setChild(Node.Direction.LEFT,node,self); 
		}
		release(prev);
		release(curr);
		release(node);
		assert(prev ==null || prev.lockedBy()!=self);
		assert(curr ==null || curr.lockedBy()!=self);
		assert(node ==null || node.lockedBy()!=self);
		return oldValue;
	}
	
	@Override
	public void clear() {
		acquire(root,self.get()); 
		root.setChild(Node.Direction.LEFT,null,self.get());
	}

	@Override
	public int size() {
		//NOT LINEARIZABLE
		return keySet().size();
	}

}
