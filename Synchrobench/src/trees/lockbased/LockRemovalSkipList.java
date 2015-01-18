package trees.lockbased;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import trees.lockbased.lockremovalutils.Error;
import trees.lockbased.lockremovalutils.ReadSet;
import trees.lockbased.lockremovalutils.SpinHeapReentrant;
import contention.abstractions.CompositionalMap;
import contention.benchmark.Parameters;

public class LockRemovalSkipList<K,V> implements CompositionalMap<K, V> {
	
	private final Comparator<? super K> comparator;
	private final int maxKey;
	private final int maxLevel;
	private final int maxHeight;
	private final Node<K, V> root; 
	private final int min;
	private final int max;
	private final Node<K,V> sentenial;
	//private final int maxRangeSize;	
	private final long LIMIT = 2000; 
	
	
	/*Constructor*/
	public LockRemovalSkipList(){
		this.maxKey = Parameters.range;
		this.maxHeight = (int) Math.ceil(Math.log(maxKey) / Math.log(2));
		this.maxLevel = maxHeight-1; 
		this.max = Integer.MAX_VALUE; 
		this.min = Integer.MIN_VALUE;
		this.sentenial = new Node(max,null,maxHeight);
		this.root = new Node(min,null,maxHeight,sentenial);
		//this.maxRangeSize = maxRangeSize;
        this.comparator = null;      
	}
	
	/*Node*/
	private static class Node<K, V>  extends SpinHeapReentrant{		
		private static final long serialVersionUID = 1L;
		
		public Node(K key, V value, int height) {
			this.key = key;
			this.value = value;
			this.next = new Object[height];
			for (int i=0; i< height; i++){
				this.next[i]=null;
			}
		}
		
		public Node(K key, V value, int height, Node<K,V> next) {
			this.key = key;
			this.value = value;
			this.next = new Object[height];
			for (int i=0; i< height; i++){
				this.next[i]=next;
			}
		}
		
		final K key;
		V value;
		Object[] next;
	}
	
	private static class SkipListRandom {
		int seed; 
		
		public SkipListRandom(){
			seed = new Random(System.currentTimeMillis()).nextInt()
	                | 0x0100;
		}
		
		int randomHeight(int maxHeight) {
	        int x = seed;
	        x ^= x << 13;
	        x ^= x >>> 17;
	        seed = (x ^= x << 5);
	        if ((x & 0x8001) != 0) {
	            return 1;
	        }
	        int level = 1;
	        while (((x >>>= 1) & 1) != 0) {
	            ++level;
	        }
	        return Math.min(level + 1, maxHeight);
	    }
	}
	
	/*Thread Locals*/
	private final ThreadLocal<SkipListRandom> skipListRandom = new ThreadLocal<SkipListRandom>(){
        @Override
        protected SkipListRandom initialValue()
        {
            return new SkipListRandom();
        }
    };
    
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
        	if (Parameters.maxRangeSize == 2000){
        		return new ReadSet<K,V>(2256); 
        	}
            return new ReadSet<K,V>(); 
        }
    };

    //private final ThreadLocal<Object[]> rangeSet = new ThreadLocal<Object[]>();
    //private final ThreadLocal<ReadSet<K,V>> threadLargeReadSet = new ThreadLocal<ReadSet<K,V>>();
	private final ThreadLocal<Object[]> threadPreds = new ThreadLocal<Object[]>();
	private final ThreadLocal<Object[]> threadSuccs = new ThreadLocal<Object[]>();
	
	/*Helper functions*/
	
	@SuppressWarnings("unchecked")
	private Comparable<? super K> comparable(final Object object) {

		if (object == null) throw new NullPointerException();
		if (comparator == null) return (Comparable<? super K>)object;

		return new Comparable<K>() {
			final Comparator<? super K> cmp = comparator;
			final K obj = (K) object;

			public int compareTo(final K other) { 
				return cmp.compare(obj, other); 
			}
		};
	}
	
	public Node<K,V> acquire(Node<K,V> node, Thread self) {
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
	
	@SuppressWarnings("unchecked")
	private boolean fullValidatePhase(Object[] preds, Object[] succs,
			int layer, ReadSet<K, V> readSet, Thread self) {
		for(int i=layer; i>-1 ; i--){
			Node<K,V> pred = (Node<K, V>) preds[i];
			if(!tryAcquire(pred, readSet, self)){
				releaseLevels(preds,succs,layer,i);
				return false;
			}
			Node<K,V> succ = (Node<K, V>) succs[i];
			if(!tryAcquire(succ, readSet, self)){
				releaseLevels(preds,succs,layer,i);
				release(pred);
				return false;
			}
		}
		if(!readSet.validate(self)){
			releaseLevels(preds,succs,layer,-1);
			return false;
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	private boolean smallValidatePhase(Object[] preds, 
			int layer, ReadSet<K, V> readSet, Thread self) {
		for(int i=layer; i>-1 ; i--){
			Node<K,V> pred = (Node<K, V>) preds[i];
			if(!tryAcquire(pred, readSet, self)){
				releaseLevel(preds,layer,i);
				return false;
			}
		}
		if(!readSet.validate(self)){
			releaseLevel(preds,layer,-1);
			return false;
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	private void releaseLevels(Object[] preds, Object[] succs, int top, int bottom) {
			for(int i=top; i>bottom;i--){
				release((Node<K, V>) preds[i]);
				release((Node<K, V>) succs[i]);
			}
		
	}
	
	@SuppressWarnings("unchecked")
	private void releaseLevel(Object[] preds, int top, int bottom) {
			for(int i=top; i>bottom;i--){
				release((Node<K, V>) preds[i]);
			}
		
	}
	
	public boolean validateReadOnly(ReadSet<K,V> readSet, final Thread self) {
		return readSet.validate(self); 
	}
	
	/*Map functions*/
	@Override
	public boolean containsKey(Object arg0) {
		throw new RuntimeException("unimplemented method");
		// TODO Auto-generated method stub
		//return false;
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
		//return false;
	}


	@Override
	public V get(Object key) {
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}		
		final Comparable<? super K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = getImp(k,self.get(),err);
			if(!err.isSet()) break;
		}
		return value; 
	}

	@SuppressWarnings("unchecked")
	private V getImp(Comparable<? super K> cmp, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		V value = null;
		Node<K,V> pred = readRef(root,readSet,err);
		if(err.isSet()) return null;
		
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
			if(err.isSet()) return null;
			
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					value = curr.value;
					if (!validateReadOnly(readSet, self)) err.set();
					return value;
				}
				if(res < 0){ //key < curr.key
					break;
				}
				pred = curr;
				curr = readRef((Node<K, V>) pred.next[layer], readSet, err);
				if(err.isSet()) return null;
				
				if(count++ == LIMIT){
					if (!validateReadOnly(readSet, self)){
						err.set();
						return null;
					}
				}
			}			
			preds[layer] = pred;
			succs[layer] = curr;		
			if(layer != 0){
				//pred = readRef(pred.down,readSet,err);
				//if(err.isSet()) return null;
				
			}
		}
		if (!validateReadOnly(readSet, self)) err.set();
		return value;
	}
	
	@Override
	public boolean isEmpty() {
		return root.next[0]==sentenial;
	}

	@Override
	public Set<K> keySet() {
		Set<K> hash = new HashSet<K>(); 
		Node<K,V> prev = root; 
		
		prev = (Node<K, V>) prev.next[0];
		while(comparable(prev.key).compareTo(sentenial.key)!=0){
			hash.add(prev.key); 
			prev = (Node<K, V>) prev.next[0];
		}
		return hash;
	}

	@Override
	public V put(K key, V val) {
		V value; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
			value = putImpl(comparable(key), key, val, self.get(), err);
			if(!err.isSet()) break; 
		}
		return value;  
	}
	
	@SuppressWarnings("unchecked")
	private V putImpl(final Comparable<? super K> cmp, final K key, final V value, Thread self,Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		V oldValue = null;
		int height = skipListRandom.get().randomHeight(maxHeight-1);
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return null;
		
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>) pred.next[layer],readSet,err);
			if(err.isSet()) return null;
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					if(layerFound==-1){
						layerFound = layer; 				
						oldValue = curr.value;
					}				
					break; 
				}
				if(res < 0){ //key < x.key
					break;
				}
				pred = curr;
				curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
				if(err.isSet()) return null;
				
				if(count++ == LIMIT){
					if (!validateReadOnly(readSet, self)){
						err.set();
						return null;
					}
				}
			}
			preds[layer] = pred;
			succs[layer] = curr;
		}
		
		if( layerFound!= -1 ){ 	//key was found change value only... 
			//VALIDATE!!! 
			if(!smallValidatePhase(succs,layerFound,readSet,self)){
				err.set();
				return null; 
			}
			
			for(int i = layerFound ; i > -1 ; i--){
				Node<K,V> curr = (Node<K,V>)succs[i];
				if( cmp.compareTo(curr.key )!= 0 ){
					assert(false);
				};
				curr.value = value;
				curr.release();
			}
			return oldValue;
		}
		
		
		//no early unlocking 
		if(!smallValidatePhase(preds,height-1,readSet,self)){
			err.set();
			return null; 
		}
		
		Node<K,V> node = new Node<K,V>(key,value,height);
		node.acquire(self);
		for(int i = height-1 ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			node.next[i] = pred.next[i];
			pred.next[i] = node;
			pred.release();
		}
		node.release();
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
		V value; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
			value = removeImpl(comparable(key),self.get(),err);
			if(!err.isSet()) break; 
		}
		return value;  
	}

	@SuppressWarnings("unchecked")
	private V removeImpl(Comparable<? super K> cmp, Thread self,Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		V oldValue = null;
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return null;
		
		for(int layer = maxLevel ; layer > -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
			if(err.isSet()) return null;			
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					oldValue = curr.value;
					if(layerFound == -1){ layerFound = layer; }
					break; 
				}
				if(res < 0){ //key < x.key
					break;
				}							
				pred = curr;
				curr = readRef((Node<K, V>) pred.next[layer],readSet,err);
				if(err.isSet()) return null;
				
				if(count++ == LIMIT){
					if (!validateReadOnly(readSet, self)){
						err.set();
						return null;
					}
				}
			}
			preds[layer] = pred;
			succs[layer] = curr;
		}
		
		if(layerFound == -1){
			if (!validateReadOnly(readSet, self)) err.set();
			return null;
		}
		
		if(!fullValidatePhase(preds,succs,layerFound,readSet,self)){
			err.set();
			return null; 
		}
		
		for(int i = layerFound ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			pred.next[i] = succ.next[i];
			pred.release();
			succ.release();
		}
		
		return oldValue;
	}
	
	@Override
	public Collection<V> values() {
		throw new RuntimeException("unimplemented method");
		// TODO Auto-generated method stub
		//return null;
	}

	@Override
	public V putIfAbsent(K k, V v) {
		V value; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
			value = putIfAbsentImpl(comparable(k), k, v, self.get(), err);
			if(!err.isSet()) break; 
		}
		return value;  
	}

	@SuppressWarnings("unchecked")
	private V putIfAbsentImpl(final Comparable<? super K> cmp, final K key, final V value, Thread self,Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		V oldValue = null;
		int height = skipListRandom.get().randomHeight(maxHeight-1);
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return null;
		
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>) pred.next[layer],readSet,err);
			if(err.isSet()) return null;
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					oldValue = curr.value;
					if (!validateReadOnly(readSet, self)) err.set();
					return oldValue; 
				}
				if(res < 0){ //key < x.key
					break;
				}
				pred = curr;
				curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
				if(err.isSet()) return null;
				
				if(count++ == LIMIT){
					if (!validateReadOnly(readSet, self)){
						err.set();
						return null;
					}
				}
			}
			preds[layer] = pred;
			succs[layer] = curr;
		}
		
		//no early unlocking 
		if(!smallValidatePhase(preds,height-1,readSet,self)){
			err.set();
			return null; 
		}
		
		Node<K,V> node = new Node<K,V>(key,value,height);
		node.acquire(self);
		for(int i = height-1 ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			node.next[i] = pred.next[i];
			pred.next[i] = node;
			pred.release();
		}
		node.release();
		return oldValue;
	}

	@Override
	public void clear() {
		//non-concurrent
		for(int layer = maxLevel; layer>-1; layer--){
			root.next[layer]=sentenial; 
		}
		
	}

	@Override
	public int size() {
		return keySet().size();
	}
	
	
	public int getRange(K min, K max){
		int value; 
		int count = 0; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
//			if(count< 3){
				value = optRangeImpl(comparable(min), comparable(max), self.get(),err);
				if(!err.isSet()) break; 
//				count++;
		//	}else if(count <6 ){
		//		value = lockedRangeImpl(comparable(min), comparable(max), self.get(),err);
		//		if(!err.isSet()) break;
		//		count++;
//			}else{
//				return value = dominationRangeImpl(comparable(min), comparable(max),self.get());
//			}
		}
		return value;  
	}
	
	@SuppressWarnings("unchecked")
	private int dominationRangeImpl(Comparable<? super K> cmpMin,
		Comparable<? super K> cmpMax, Thread self) {
		//Object[] result = rangeSet.get();
		int rangeCount = 0; 
		
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = root; 
		pred.acquire(self);
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			while (true) {
				int res = cmpMin.compareTo(curr.key);
				if(res == 0) {
					if(layerFound==-1){
						layerFound = layer; 				
					}				
					break; 
				}
				if(res < 0){ 
					break;
				}			
				pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				//pred = pred.down;
				pred.acquire(self);
			}
		}
		
		int lockLayer;
		Node<K,V> next;
		if( layerFound!= -1 ){ 	
			//key was found
			lockLayer = layerFound; 
			next = (Node<K, V>) succs[lockLayer]; 
			next = (Node<K, V>) next.next[lockLayer];
		}else{	
			//key was not found
			lockLayer = 0; 
			next = (Node<K, V>) succs[lockLayer];
		}
		
		while(cmpMax.compareTo(next.key) >= 0 ){ // curr.next is inside the range, go up
			lockLayer++;
			next = (Node<K, V>) succs[lockLayer];
		}
		
		for(int i = maxLevel ; i > lockLayer ; i--){
			Node<K,V> curr = (Node<K,V>)succs[i];
			pred = ((Node<K,V>) preds[i]);
			pred.release();
			curr.release();
		}
		
		pred = (Node<K, V>) preds[0];
		pred.acquire(self);
		Node<K, V> curr = (Node<K, V>) succs[0]; 
		curr.acquire(self);
		while(cmpMax.compareTo(curr.key) >= 0){		
			//result[rangeCount] = curr.key;
			rangeCount++;
			pred.release();
			pred = curr; 
			curr = (Node<K, V>) curr.next[0]; 
			curr.acquire(self);	
		}
		curr.release();
		pred.release();
		
		for(int i = lockLayer ; i > -1 ; i--){
			curr = (Node<K,V>)succs[i];
			pred = ((Node<K,V>) preds[i]);
			pred.release();
			curr.release();
		}
		return rangeCount;
	}

	

	@SuppressWarnings("unchecked")
	private int lockedRangeImpl(Comparable<? super K> cmpMin,
		Comparable<? super K> cmpMax, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		int layerFound = -1; 
		int rangeCount = 0; 
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return -1;
		
		for(int layer = maxLevel ; layer > -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
			if(err.isSet()) return -1;			
			while (true) {
				int res = cmpMin.compareTo(curr.key);
				if(res == 0) {
					//key found...
					if(layerFound==-1){
						layerFound = layer; 				
					}				
					break; 
				}
				if(res < 0){ 
					break;
				}							
				pred = curr;
				curr = readRef((Node<K, V>) pred.next[layer],readSet,err);
				if(err.isSet()) return -1;
				
				if(count++ == LIMIT){
					if (!validateReadOnly(readSet, self)){
						err.set();
						return -1;
					}
				}
			}
			preds[layer] = pred;
			succs[layer] = curr;
			
		}
		
		
		int lockLayer;
		Node<K,V> next;
		if( layerFound!= -1 ){ 	
			//key was found
			lockLayer = layerFound; 
			next = (Node<K, V>) succs[lockLayer]; 
			next = (Node<K, V>) next.next[lockLayer];
		}else{	
			//key was not found
			lockLayer = 0; 
			next = (Node<K, V>) succs[lockLayer];
		}
		
		while(cmpMax.compareTo(next.key) >= 0 ){ // curr.next is inside the range, go up
			lockLayer++;
			next = (Node<K, V>) succs[lockLayer];
		}
		
		//VALIDATION lock preds and succs from lockLayer
		if(!fullValidatePhase(preds,succs, lockLayer,readSet,self)){
			err.set();
			return 0; 
		}		
		
		pred = (Node<K, V>) preds[0];
		pred.acquire(self);
		Node<K, V> curr = (Node<K, V>) succs[0]; 
		curr.acquire(self);
		while(cmpMax.compareTo(curr.key) >= 0){		
			//result[rangeCount] = curr.key;
			rangeCount++;
			pred.release();
			pred = curr; 
			curr = (Node<K, V>) curr.next[0]; 
			curr.acquire(self);	
		}
		curr.release();
		pred.release();
		
		for(int i = lockLayer ; i > -1 ; i--){
			curr = (Node<K,V>)succs[i];
			pred = ((Node<K,V>) preds[i]);
			pred.release();
			curr.release();
		}
		return rangeCount;
	
	}
	
	@SuppressWarnings("unchecked")
	private int optRangeImpl(Comparable<? super K> cmpMin,
			Comparable<? super K> cmpMax, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		long count = 0; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return -1;
		
		for(int layer = maxLevel ; layer > -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
			if(err.isSet()) return -1;			
			while (true) {
				int res = cmpMin.compareTo(curr.key);
				if(res == 0) {
					//Object[] result = rangeSet.get();
					int rangeCount = 0; 
					while(cmpMax.compareTo(curr.key) >= 0){
						//result[rangeCount] = curr.key;
						rangeCount++;
						curr = readRef((Node<K, V>)curr.next[0],readSet,err);
						if(err.isSet()) return -1;			
					}
					if (!validateReadOnly(readSet, self)) err.set();
					return rangeCount;
				}
				if(res < 0){ //key < x.key
					break;
				}							
				pred = curr;
				curr = readRef((Node<K, V>) pred.next[layer],readSet,err);
				if(err.isSet()) return -1;
				
				if(count++ == LIMIT){
					if (!validateReadOnly(readSet, self)){
						err.set();
						return -1;
					}
				}
			}
			preds[layer] = pred;
			succs[layer] = curr;
			
		}
		//key not found, start from predecessor 
		//Object[] result = rangeSet.get();
		int rangeCount = 0; 
		Node<K,V> curr = (Node<K, V>) preds[0]; 
		while(cmpMin.compareTo(curr.key)>0){
			curr = readRef((Node<K, V>)curr.next[0],readSet,err);
			if(err.isSet()) return -1;		
		}
		while(cmpMax.compareTo(curr.key) >= 0){
			//result[rangeCount] = curr.key;
			rangeCount++;
			curr = readRef((Node<K, V>)curr.next[0],readSet,err);
			if(err.isSet()) return -1;			
		}
		if (!validateReadOnly(readSet, self)) err.set();
		return rangeCount;
	}
}
