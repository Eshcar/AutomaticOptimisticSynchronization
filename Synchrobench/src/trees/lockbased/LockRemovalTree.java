package trees.lockbased;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import trees.lockbased.lockremovalutils.Error;
import trees.lockbased.lockremovalutils.LockSet;
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
    
    private final ThreadLocal<LockSet> threadLockSet = new ThreadLocal<LockSet>(){
        @Override
        protected LockSet initialValue()
        {
            return new LockSet(8); 
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
    
    public Node<K,V> assign(Node<K,V> prevValue,
			Node<K,V> newValue, Thread self) {
		acquire(newValue, self);
        release(prevValue);
        return newValue;
	}
    
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
            	//readSet.incrementLocalVersion(node);
            	return true;
            }else{
            	return false; 
            }
        }
        return true;
    }
	
	private Node<K,V> readRef(Node<K,V> newNode, Node<K,V> oldNode ,ReadSet<K,V> readSet,LockSet lockSet, Error err) {
		//use for assign 
		if(newNode!=null){
			int version = newNode.getVersion();
			if(newNode.isLocked()){
				err.set();
				return null;
			}
			lockSet.add(newNode);
			readSet.add(newNode, version);
		}
		if(oldNode!=null){
			lockSet.remove(oldNode);
		}
		return newNode;
	}
	
	private Node<K,V> readRef(Node<K,V> newNode,ReadSet<K,V> readSet,LockSet lockSet, Error err) {
		//use for acquire
		if(newNode!=null){
			int version = newNode.getVersion();
			if(newNode.isLocked()){
				err.set();
				return null;
			}
			lockSet.add(newNode);
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
		int retryCount = 0;
		while(true){
			err.clean();
			value = getImpl(k, self.get(), err);
			if(!err.isSet()) break;
			retryCount ++;
			if(retryCount > LIMIT){
				return getDomImpl((K) key,self.get());
			}
		}
		return value; 
	}

	private V getImpl(Comparable<K> k, final Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		LockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			
			Node<K, V> curr = readRef(this.root,readSet, lockSet ,err);
			if(err.isSet()) return null;
			while(curr!=null){
				int res = k.compareTo(curr.key);
				if(res == 0) break; 
				if(res > 0){ //key > x.key
					curr=readRef(curr.right,curr, readSet, lockSet, err);
					if(err.isSet()) return null;
				}else{
					curr=readRef(curr.left, curr, readSet, lockSet, err);
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
				lockSet.remove(curr);
				V value = curr.value;
				if (!validateReadOnly(readSet, self)) err.set();
				return value;
			}
			if (!validateReadOnly(readSet, self)) err.set();
			return null;
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	public V getDomImpl(Object key, Thread self) {
		final Comparable<? super K> k = comparable(key);
		Node<K, V> curr = acquire(this.root,self); 
		while(curr!=null){
			int res = k.compareTo(curr.key);
			if(res == 0) break; 
			if(res > 0){ //key > x.key
				curr = assign(curr, curr.right, self); 
			}else{
				curr = assign(curr, curr.left, self); 
			}
		}
		if(curr!=null){
			release(curr);
			return curr.value;
		}
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
		int retryCount = 0;
		while(true){
			err.clean();
			value = putImpl(key, k, val, self.get(), err);
			if(!err.isSet()) break; 
			retryCount ++;
			if(retryCount > LIMIT){
				return putDomImpl(key, val, self.get());
			}
		}
		return value;  
	}
	
	private V putImpl(K key, final Comparable<K> k , V val , final Thread self, Error err) {		
		ReadSet<K,V> readSet = threadReadSet.get();
		LockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			V oldValue = null; 
			
			//Read-only phase//
			Node<K, V> prev = null;
			Node<K, V> curr = readRef(this.root,readSet,lockSet,err); 
			if(err.isSet()) return null;
			int res = -1;
			while(curr!=null){
				prev = readRef(curr,prev,readSet,lockSet,err);
				if(err.isSet()) return null;
				res = k.compareTo(curr.key);
				if(res == 0){
					oldValue = prev.value;
					break; 
				}
				if(res > 0){ //key > x.key
					curr= readRef(curr.right,curr,readSet,lockSet,err);
					if(err.isSet()) return null;
				}else{
					curr= readRef(curr.left,curr,readSet,lockSet,err);
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
			/* old Version:
			if(!validateTwo(readSet,prev,curr,self)){
				err.set();
				return null; 
			}*/
			if(!lockSet.tryLockAll(self)){
				err.set();
				return null; 
			}
			if (!validateReadOnly(readSet, self)){
				lockSet.releaseAll();
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
			//assert(prev ==null || prev.lockedBy()!=self);
			//assert(curr ==null || curr.lockedBy()!=self);
			//assert(node ==null || node.lockedBy()!=self);
			return oldValue;
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	public V putDomImpl(K key, V val, Thread self) {
		final Comparable<? super K> k = comparable(key);
		
		V oldValue = null;
		Node<K, V> prev = null;
		Node<K, V> curr = acquire(this.root, self); 
		int res = -1;
		while(curr!=null){
			prev = assign(prev,curr,self);
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			if(res > 0){ //key > x.key
				curr = assign(curr, curr.right, self) ; 
			}else{
				curr = assign(curr, curr.left, self) ; 
			}
		}
		if(res == 0) {
			curr.value = val; 
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
		int retryCount = 0; 
		while(true){
			err.clean();
			value = removeImpl(k, self.get(), err);
			if(!err.isSet()) break;
			retryCount ++;
			if(retryCount > LIMIT){
				return removeDomImpl(key, self.get());
			}
		}
		return value;  
	}

	private V removeImpl(Comparable<K> k, final Thread self, Error err){
		ReadSet<K,V> readSet = threadReadSet.get();
		LockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			V oldValue = null;		
			
			//Read-only phase//
			Node<K, V> prev = null;
			Node<K, V> curr = readRef(this.root,readSet,lockSet,err);
			if(err.isSet()) return null;
			int res = -1;
			while(curr!=null){			
				res = k.compareTo(curr.key);	
				if(res == 0){
					oldValue = curr.value;			
					break; 
				}
				prev=readRef(curr,prev,readSet,lockSet,err);
				if(err.isSet()) return null;
				if(res > 0){ //key > x.key
					curr= readRef(curr.right,curr,readSet,lockSet,err); 
					if(err.isSet()) return null;
				}else{
					curr= readRef(curr.left,curr,readSet,lockSet,err);
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
			
			
			Node<K, V> currL = readRef(curr.left,readSet,lockSet,err); 
			if(err.isSet()) return null;
			Node<K, V> currR = readRef(curr.right,readSet,lockSet,err);
			if(err.isSet()) return null;
			
			boolean isLeft = prev.left == curr; 
			if (currL == null){ //no left child
				
				//Validation phase//
				/*old validation
				if(!validateFour(readSet,prev,curr,currL, currR, self)){
					err.set();
					return null; 
				}*/
				if(!lockSet.tryLockAll(self)){
					err.set();
					return null; 
				}
				if (!validateReadOnly(readSet, self)){
					lockSet.releaseAll();
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
				/*old validation
				if(!validateFour(readSet,prev,curr,currL, currR, self)){
					err.set();
					return null; 
				}*/
				if(!lockSet.tryLockAll(self)){
					err.set();
					return null; 
				}
				if (!validateReadOnly(readSet, self)){
					lockSet.releaseAll();
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
			Node<K, V> prevSucc = readRef(curr,readSet,lockSet,err);
			if(err.isSet()) return null;
			Node<K, V> succ = readRef(currR,readSet,lockSet,err); 
			if(err.isSet()) return null;
			Node<K, V> succL =  readRef(succ.left,readSet,lockSet,err); 
			if(err.isSet()) return null;
			
			while(succL != null){
				prevSucc = readRef(succ,prevSucc,readSet,lockSet,err);
				if(err.isSet()) return null;
				succ = readRef(succL,succ,readSet,lockSet,err); 
				if(err.isSet()) return null;
				succL =  readRef(succ.left,succL,readSet,lockSet,err);
				if(err.isSet()) return null;
			}
			
			//Validation phase//
			/*old validation
			if(!validateSix(readSet,prev,curr,currL,currR,prevSucc,succ, self)){
				err.set();
				return null; 
			}*/
			if(!lockSet.tryLockAll(self)){
				err.set();
				return null; 
			}
			if (!validateReadOnly(readSet, self)){
				lockSet.releaseAll();
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
		
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	public V removeDomImpl(Object key,Thread self) {
		final Comparable<? super K> k = comparable(key);
		V oldValue = null;
		Node<K, V> prev = null;
		Node<K, V> curr = acquire(this.root, self); 
		int res = -1;
		while(curr!=null){
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			prev = assign(prev,curr,self);
			if(res > 0){ //key > x.key
				curr = assign(curr, curr.right, self) ; 
			}else{
				curr = assign(curr, curr.left, self) ; 
			}
		}
		
		if(res!= 0) {	
			release(prev);
			release(curr);
			return oldValue;
		}
		Node<K, V> currL = acquire(curr.left,self); 
		Node<K, V> currR = acquire(curr.right,self); 
		boolean isLeft = prev.left == curr; 
		if (currL == null){ //no left child
			if(isLeft){
				prev.setChild(Node.Direction.LEFT,currR,self);
			}else {
				prev.setChild(Node.Direction.RIGHT,currR,self);
			}
			curr.setChild(Node.Direction.RIGHT,null,self);
		} else if (currR == null){ //no right child
			if(isLeft){
				prev.setChild(Node.Direction.LEFT,currL,self);
			}else {
				prev.setChild(Node.Direction.RIGHT,currL,self);
			}
			curr.setChild(Node.Direction.LEFT,null,self);
		}else { //both children
			Node<K, V> prevSucc = acquire(curr,self); //TODO re-acquire ?? 
			Node<K, V> succ =acquire(currR,self);  //TODO re-acquire ?? 
			Node<K, V> succL = acquire(succ.left,self); 
			while(succL != null){
				prevSucc =assign(prevSucc,succ,self);
				succ = assign(succ,succL,self);
				succL =  assign(succL,succ.left,self);
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
			
		}
		
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
		int retryCount = 0; 
		while(true){
			err.clean();
			value = putIfAbsentImpl(key, k, val, self.get(), err);
			if(!err.isSet()) break;
			retryCount ++;
			if(retryCount > LIMIT){
				return putIfAbsentDomImpl(key,val, self.get());
			}
		}
		return value; 
	}

	private V putIfAbsentImpl(K key, final Comparable<K> k , V val , final Thread self, Error err) {		
		ReadSet<K,V> readSet = threadReadSet.get();
		LockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			V oldValue = null; 
			
			//Read-only phase//
			Node<K, V> prev = null;
			Node<K, V> curr = readRef(this.root,readSet,lockSet,err); 
			if(err.isSet()) return null;
			int res = -1;
			while(curr!=null){
				prev = readRef(curr,prev,readSet,lockSet,err);
				if(err.isSet()) return null;
				res = k.compareTo(curr.key);
				if(res == 0){
					//key was found
					oldValue = prev.value;
					lockSet.remove(prev);
					lockSet.remove(curr);
					if (!validateReadOnly(readSet, self)){
						err.set();
						return null;
					}				
					return oldValue; 
				}
				if(res > 0){ //key > x.key
					curr= readRef(curr.right,curr,readSet,lockSet,err);
					if(err.isSet()) return null;
				}else{
					curr= readRef(curr.left,curr,readSet,lockSet,err);
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
			/* old validation
			if(!validateTwo(readSet,prev,curr,self)){
				err.set();
				return null; 
			}*/
			if(!lockSet.tryLockAll(self)){
				err.set();
				return null; 
			}
			if (!validateReadOnly(readSet, self)){
				lockSet.releaseAll();
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
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	public V putIfAbsentDomImpl(K key, V val, Thread self) {
		final Comparable<? super K> k = comparable(key);
		V oldValue = null;
		Node<K, V> prev = null;
		Node<K, V> curr = acquire(this.root, self); 
		int res = -1;
		while(curr!=null){
			prev = assign(prev,curr,self);
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			if(res > 0){ //key > x.key
				curr = assign(curr, curr.right, self) ; 
			}else{
				curr = assign(curr, curr.left, self) ; 
			}
		}
		if(res == 0) {
			//curr.value = val; 
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
		return oldValue;
	}
	
	@Override
	public void clear() {
		acquire(root,self.get()); 
		root.setChild(Node.Direction.LEFT,null,self.get());
		release(root);
	}

	@Override
	public int size() {
		//NOT LINEARIZABLE
		return keySet().size();
	}

	@Override
	public int getRange(K[] result, K min, K max) {
		throw new RuntimeException("unimplemented method");
	}

}
