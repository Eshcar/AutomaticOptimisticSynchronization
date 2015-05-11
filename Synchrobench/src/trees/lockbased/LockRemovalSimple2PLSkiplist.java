package trees.lockbased;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import trees.lockbased.lockremovalutils.Error;
import trees.lockbased.lockremovalutils.HashLockSet;
import trees.lockbased.lockremovalutils.LockSet;
import trees.lockbased.lockremovalutils.ReadSet;
import trees.lockbased.lockremovalutils.SpinHeapReentrant;
import contention.abstractions.CompositionalMap;
import contention.benchmark.Parameters;

public final class  LockRemovalSimple2PLSkiplist<K,V> implements CompositionalMap<K, V> {
	private final Comparator<? super K> comparator;
	private final int maxKey;
	private final int maxLevel;
	private final int maxHeight;
	private final Node<K, V> root; 
	private final int min;
	private final int max;
	private final Node<K,V> sentenial;
	private final long LIMIT = 2000; 
	
	/*Constructor*/
	public LockRemovalSimple2PLSkiplist(){
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
	private static final class Node<K, V>  extends SpinHeapReentrant{		
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
    
    private final ThreadLocal<HashLockSet> threadLockSet = new ThreadLocal<HashLockSet>(){
        @Override
        protected HashLockSet initialValue()
        {
            return new HashLockSet(); 
        }
    };
    
    /*
    private final ThreadLocal<LockSet> threadLockSet = new ThreadLocal<LockSet>(){
        @Override
        protected LockSet initialValue()
        {
            return new LockSet(maxHeight*10 + Parameters.maxRangeSize); 
        }
    };*/
    
    private final ThreadLocal<Object[]> threadPreds = new ThreadLocal<Object[]>();
	private final ThreadLocal<Object[]> threadSuccs = new ThreadLocal<Object[]>();
	
	private final ThreadLocal<SpinHeapReentrant[]> threadLocked = new ThreadLocal<SpinHeapReentrant[]>(){
		 @Override
        protected SpinHeapReentrant[] initialValue()
        {
            return new SpinHeapReentrant[maxHeight*10+Parameters.maxRangeSize];
        }
	 };
    
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
	
	private void release(SpinHeapReentrant node) {
		if (node != null){
	           node.release();
		}
	}
	
	private Node<K,V> readRef(Node<K,V> newNode,ReadSet<K,V> readSet,Error err) {
		//use for acquire
		if(newNode!=null){
			int version = newNode.getVersion();
			if(newNode.isLocked()){
				err.set();
				return null;
			}
			//lockSet.add(newNode);
			readSet.add(newNode, version);
		}
		return newNode;
	}
	
	private Node<K,V> readRef(Node<K,V> newNode,ReadSet<K,V> readSet,HashLockSet lockSet, Error err) {
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

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}		
		final Comparable<? super K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		int retryCount = 0; 
		while(true){
			err.clean();
			value = getImp(k,(K) key,self.get(),err);
			if(!err.isSet()) break;
			retryCount ++;
			if(retryCount > LIMIT){
				return get2PLImp(k,(K) key,self.get());
			}
		}
		return value; 
	}

	@SuppressWarnings("unchecked")
	private V getImp(Comparable<? super K> cmp, K key, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		//LockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		//lockSet.clear();
		try{
			long count = 0;
			
			Object[] preds = threadPreds.get();
			Object[] succs = threadSuccs.get();
			SpinHeapReentrant[] locked = threadLocked.get();
			int l =0; 
			
			V value = null;
			Node<K,V> pred = readRef(root,readSet,err);
			if(err.isSet()) return null;
			locked[l] = pred;
			l++;
			
			for(int layer = maxLevel ; layer> -1 ; layer-- ){
				Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
				if(err.isSet()) return null;
				locked[l] = curr;
				l++;
				while (true) {
					int res = cmp.compareTo(curr.key);
					if(res == 0) {
						value = curr.value;
						for(int j=0;j<l;j++){
							//lockSet.remove(locked[j]);
						}
						if (!validateReadOnly(readSet, self)) err.set();
						return value;
					}
					if(res < 0){ //key < curr.key
						break;
					}
					//pred.release();
					pred = curr;
					curr = readRef((Node<K, V>) pred.next[layer], readSet, err);
					if(err.isSet()) return null;
					locked[l] = curr;
					l++;
					
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
					pred = readRef(pred,readSet,err);
					if(err.isSet()) return null;
					locked[l] = pred;
					l++;
				}
			}
			for(int j=0;j<l;j++){
				//lockSet.remove(locked[j]);
			}
			if (!validateReadOnly(readSet, self)) err.set();
			return value;
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	@SuppressWarnings("unchecked")
	private V get2PLImp(Comparable<? super K> cmp, K key, Thread self) {
		
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		SpinHeapReentrant[] locked = threadLocked.get();
		int l =0; 
	
		V value = null;
		Node<K,V> pred = root; 
		pred.acquire(self);
		locked[l] = pred;
		l++;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			locked[l] = curr;
			l++;
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					value = curr.value;
					for(int j=0;j<l;j++){
						release(locked[j]);
					}
					return value;
				}
				if(res < 0){ //key < curr.key
					break;
				}
				//pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
				locked[l] = curr;
				l++;
			}			
			preds[layer] = pred;
			succs[layer] = curr;		
			if(layer != 0){
				pred.acquire(self);
				locked[l] = pred;
				l++;
			}
		}
		for(int j=0;j<l;j++){
			release(locked[j]);
		}
		return value;
	}
	
	@Override
	public boolean isEmpty() {
		return root.next[0]==sentenial;
	}

	@SuppressWarnings("unchecked")
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
		int retryCount = 0;
		while(true){
			err.clean();
			value = putImpl(comparable(key), key, val, self.get(), err);
			if(!err.isSet()) break; 
			retryCount ++;
			if(retryCount > LIMIT){
				return put2PLImpl(comparable(key), key, val, self.get());
			}
		}
		return value;
	}
	
	@SuppressWarnings("unchecked")
	private V putImpl(final Comparable<? super K> cmp, final K key, final V value, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		HashLockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			
			V oldValue = null;
			int height = skipListRandom.get().randomHeight(maxHeight-1);
			int layerFound = -1; 
			Object[] preds = threadPreds.get();
			Object[] succs = threadSuccs.get();
			SpinHeapReentrant[] locked = threadLocked.get();
			int l =0; 
			
			Node<K,V> pred = readRef(root,readSet,lockSet,err); 
			if(err.isSet()) return null;
			locked[l] = pred;
			l++;
			for(int layer = maxLevel ; layer> -1 ; layer-- ){ 
				Node<K,V> curr = readRef((Node<K, V>) pred.next[layer],readSet,lockSet,err);
				if(err.isSet()) return null;
				locked[l] = curr;
				l++;
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
					//pred.release();
					pred = curr;
					curr = readRef((Node<K, V>)pred.next[layer],readSet,lockSet,err);
					if(err.isSet()) return null;
					locked[l] = curr;
					l++;
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
					pred = readRef(pred,readSet,lockSet,err);
					if(err.isSet()) return null;
					locked[l] = pred;
					l++;
				}
			}
			
			if( layerFound!= -1 ){ 	//key was found change value only... 
				
				//Lock needed nodes
				for(int i = layerFound ; i > -1 ; i--){
					lockSet.add((Node<K,V>)preds[i]); //re-acquire no need to readref
					lockSet.add((Node<K,V>)succs[i]);
				}
				
				//unlock other nodes
				for(int i=0;i<l;i++){
					lockSet.remove(locked[i]);
				}
				
				//validate
				if(!lockSet.tryLockAll(self)){
					err.set();
					return null; 
				}
				if (!validateReadOnly(readSet, self)){
					lockSet.releaseAll();
					err.set();
					return null;
				}
				
				for(int i = layerFound ; i > -1 ; i--){	
					Node<K,V> curr = (Node<K,V>)succs[i];
					pred = ((Node<K,V>) preds[i]);
					if( cmp.compareTo(curr.key )!= 0 ){
						assert(false);
					};
					curr.value = value;
					pred.release();
					curr.release();
				}
				return oldValue;
			}
			
			//Lock needed nodes
			for(int i = height-1 ; i > -1 ; i--){
				lockSet.add((Node<K,V>)preds[i]); //re-acquire no need to readref
				lockSet.add((Node<K,V>)succs[i]);
			}
			
			//unlock other nodes
			for(int i=0;i<l;i++){
				lockSet.remove(locked[i]);
			}
			
			//validate
			if(!lockSet.tryLockAll(self)){
				err.set();
				return null; 
			}
			if (!validateReadOnly(readSet, self)){
				lockSet.releaseAll();
				err.set();
				return null;
			}
			
			Node<K,V> node = new Node<K,V>(key,value,height);
			node.acquire(self);
			for(int i = height-1 ; i > -1 ; i--){
				pred = ((Node<K,V>) preds[i]);
				Node<K,V> succ = ((Node<K,V>) succs[i]);
				node.next[i] = succ;	
				pred.next[i] = node;
				pred.release();
				succ.release();
			}
			node.release();
			return oldValue;
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	@SuppressWarnings("unchecked")
	private V put2PLImpl(final Comparable<? super K> cmp, final K key, final V value, Thread self) {
		V oldValue = null;
		int height = skipListRandom.get().randomHeight(maxHeight-1);
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		SpinHeapReentrant[] locked = threadLocked.get();
		int l =0; 
		
		Node<K,V> pred = root; 
		pred.acquire(self);
		locked[l] = pred;
		l++;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){ 
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			locked[l] = curr;
			l++;
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
				//pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
				locked[l] = curr;
				l++;
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred.acquire(self);
				locked[l] = pred;
				l++;
			}
		}
		
		if( layerFound!= -1 ){ 	//key was found change value only... 
			
			//Lock needed nodes
			for(int i = layerFound ; i > -1 ; i--){
				((Node<K,V>)preds[i]).acquire(self);
				((Node<K,V>)succs[i]).acquire(self);
			}
			
			//unlock other nodes
			for(int i=0;i<l;i++){
				release(locked[i]);
			}
			
			for(int i = layerFound ; i > -1 ; i--){	
				Node<K,V> curr = (Node<K,V>)succs[i];
				pred = ((Node<K,V>) preds[i]);
				if( cmp.compareTo(curr.key )!= 0 ){
					assert(false);
				};
				curr.value = value;
				pred.release();
				curr.release();
			}
			return oldValue;
		}
		
		//Lock needed nodes
		for(int i = height-1 ; i > -1 ; i--){
			((Node<K,V>)preds[i]).acquire(self);
			((Node<K,V>)succs[i]).acquire(self);
		}
		
		//unlock other nodes
		for(int i=0;i<l;i++){
			release(locked[i]);
		}
		
		Node<K,V> node = new Node<K,V>(key,value,height);
		node.acquire(self);
		for(int i = height-1 ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			node.next[i] = succ;	
			pred.next[i] = node;
			pred.release();
			succ.release();
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

	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		V value; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		int retryCount = 0;
		while(true){
			err.clean();
			value = removeImpl(comparable(key),(K) key, self.get(),err);
			if(!err.isSet()) break; 
			retryCount ++;
			if(retryCount > LIMIT){
				return remove2PLImpl(comparable(key),(K) key,self.get());
			}
		}
		return value;  
	}

	@SuppressWarnings("unchecked")
	private V removeImpl(Comparable<? super K> cmp, K key, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		HashLockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			V oldValue = null;
			int layerFound = -1; 
			Object[] preds = threadPreds.get();
			Object[] succs = threadSuccs.get();
			SpinHeapReentrant[] locked = threadLocked.get();
			int l =0; 
			
			Node<K,V> pred = readRef(root,readSet,lockSet,err); 
			if(err.isSet()) return null;
			locked[l] = pred;
			l++;
			for(int layer = maxLevel ; layer > -1 ; layer-- ){
				Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,lockSet,err);
				if(err.isSet()) return null;	
				locked[l] = curr;
				l++;
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
					//pred.release();
					pred = curr;
					curr = readRef((Node<K, V>) pred.next[layer],readSet,lockSet,err);
					if(err.isSet()) return null;
					locked[l] = curr;
					l++;
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
					pred = readRef(pred,readSet,lockSet,err);
					if(err.isSet()) return null;
					locked[l] = pred;
					l++;
				}
			}
			
			//lock needed nodes
			for(int i = layerFound ; i > -1 ; i--){
				lockSet.add((Node<K,V>)preds[i]); //re-acquire no need to readref
				lockSet.add((Node<K,V>)succs[i]);
			}
			
			//unlock other nodes
			for(int i=0;i<l;i++){
				lockSet.remove(locked[i]);
			}
			
			//validate
			if(!lockSet.tryLockAll(self)){
				err.set();
				return null; 
			}
			if (!validateReadOnly(readSet, self)){
				lockSet.releaseAll();
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
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}
	
	@SuppressWarnings("unchecked")
	private V remove2PLImpl(Comparable<? super K> cmp, K key, Thread self) {
		V oldValue = null;
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		SpinHeapReentrant[] locked = threadLocked.get();
		int l =0; 
		
		Node<K,V> pred = root;
		pred.acquire(self);
		locked[l] = pred;
		l++;
		for(int layer = maxLevel ; layer > -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			locked[l] = curr;
			l++;
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
				//pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
				locked[l] = curr;
				l++;
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred.acquire(self);
				locked[l] = pred;
				l++;
			}
		}
		
		//lock needed nodes
		for(int i = layerFound ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			pred.acquire(self);
			succ.acquire(self);
		}
		
		//unlock other nodes
		for(int i=0;i<l;i++){
			release(locked[i]);
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
		int retryCount = 0;
		while(true){
			err.clean();
			value = putIfAbsentImpl(comparable(k), k, v, self.get(), err);
			if(!err.isSet()) break;
			retryCount ++;
			if(retryCount > LIMIT){
				return putIfAbsent2PLImpl(comparable(k), k, v, self.get());
			}
		}
		return value;  
	}

	@SuppressWarnings("unchecked")
	private V putIfAbsentImpl(Comparable<? super K> cmp, K key, V value,
			Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		HashLockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		lockSet.clear();
		try{
			long count = 0; 
			V oldValue = null;
			int height = skipListRandom.get().randomHeight(maxHeight-1);
			Object[] preds = threadPreds.get();
			Object[] succs = threadSuccs.get();
			SpinHeapReentrant[] locked = threadLocked.get();
			int l =0; 
			
			Node<K,V> pred = readRef(root,readSet,lockSet,err); 
			if(err.isSet()) return null;
			locked[l] = pred;
			l++;
			for(int layer = maxLevel ; layer> -1 ; layer-- ){
				Node<K,V> curr = readRef((Node<K, V>) pred.next[layer],readSet,lockSet,err);
				if(err.isSet()) return null;
				locked[l] = curr;
				l++;
				while (true) {
					int res = cmp.compareTo(curr.key);
					if(res == 0) {	
						oldValue = curr.value;
						for(int i=0;i<l;i++){
							lockSet.remove(locked[i]);
						}
						return oldValue;
					}
					if(res < 0){ //key < x.key
						break;
					}			
					//pred.release();
					pred = curr;
					curr = readRef((Node<K, V>)pred.next[layer],readSet,lockSet,err);
					if(err.isSet()) return null;
					locked[l] = curr;
					l++;
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
					pred = readRef(pred,readSet,lockSet,err);
					if(err.isSet()) return null;
					locked[l] = pred;
					l++;
				}
			}
			
			//Lock needed nodes
			for(int i = height-1 ; i > -1 ; i--){
				lockSet.add((Node<K,V>)preds[i]); //re-acquire no need to readref
				lockSet.add((Node<K,V>)succs[i]);
			}
			
			//unlock other nodes
			for(int i=0;i<l;i++){
				lockSet.remove(locked[i]);
			}
			
			//validate 
			if(!lockSet.tryLockAll(self)){
				err.set();
				return null; 
			}
			if (!validateReadOnly(readSet, self)){
				lockSet.releaseAll();
				err.set();
				return null;
			}
			
			Node<K,V> node = new Node<K,V>(key,value,height);
			node.acquire(self);
			for(int i = height-1 ; i > -1 ; i--){
				pred = ((Node<K,V>) preds[i]);
				Node<K,V> succ = ((Node<K,V>) succs[i]);
				node.next[i] = succ;	
				pred.next[i] = node;
				pred.release();
				succ.release();
			}
			node.release();
			return oldValue;	
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return null;			
		}
	}

	@SuppressWarnings("unchecked")
	private V putIfAbsent2PLImpl(Comparable<? super K> cmp, K key, V value,
			Thread self) {
		V oldValue = null;
		int height = skipListRandom.get().randomHeight(maxHeight-1);
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		SpinHeapReentrant[] locked = threadLocked.get();
		int l =0; 
		
		Node<K,V> pred = root; 
		pred.acquire(self);
		locked[l] = pred;
		l++;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			locked[l] = curr;
			l++;
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {	
					oldValue = curr.value;
					for(int i=0;i<l;i++){
						release(locked[i]);
					}
					return oldValue;
				}
				if(res < 0){ //key < x.key
					break;
				}			
				//pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
				locked[l] = curr;
				l++;
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred.acquire(self);
				locked[l] = pred;
				l++;
			}
		}
		
		//Lock needed nodes
		for(int i = height-1 ; i > -1 ; i--){
			((Node<K,V>)preds[i]).acquire(self);
			((Node<K,V>)succs[i]).acquire(self);
		}
		
		//unlock other nodes
		for(int i=0;i<l;i++){
			release(locked[i]);
		}
		
		Node<K,V> node = new Node<K,V>(key,value,height);
		node.acquire(self);
		for(int i = height-1 ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			node.next[i] = succ;	
			pred.next[i] = node;
			pred.release();
			succ.release();
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
		//use this for ranges! 
		return keySet().size();
	}
	
	@Override
	public int getRange(K[] result, K min, K max) {
		int value; 
		int retryCount = 0; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			
			err.clean();
			value = rangeImpl(result, comparable(min), comparable(max), self.get(),err);
			if(!err.isSet()) break;
			retryCount ++;
			if(retryCount > LIMIT){
				return range2PLImpl(result, comparable(min), comparable(max), self.get());
			}
				
		}
		return value;  
	}
	
	@SuppressWarnings("unchecked")
	private int rangeImpl(K[] result, Comparable<? super K> cmpMin,
		Comparable<? super K> cmpMax, Thread self, Error err) {
		ReadSet<K,V> readSet = threadReadSet.get();
		//LockSet lockSet = threadLockSet.get();
		readSet.clear(); 
		//lockSet.clear(); 
		try{
			long count = 0;
			int rangeCount = 0; 
			int layerFound = -1; 
			Object[] preds = threadPreds.get();
			Object[] succs = threadSuccs.get();
			SpinHeapReentrant[] locked = threadLocked.get();
			int l =0;
			
			Node<K,V> pred = readRef(root,readSet,err); 
			if(err.isSet()) return -1;
			locked[l] = pred;
			l++;
			for(int layer = maxLevel ; layer> -1 ; layer-- ){
				Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
				if(err.isSet()) return -1;	
				locked[l] = curr;
				l++;
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
					//pred.release();
					pred = curr;
					curr = readRef((Node<K, V>) pred.next[layer],readSet,err);
					if(err.isSet()) return -1;
					locked[l] = curr;
					l++;
					if(count++ == LIMIT){
						if (!validateReadOnly(readSet, self)){
							err.set();
							return -1;
						}
					}
		
				}
				preds[layer] = pred;
				succs[layer] = curr;
				if(layer != 0){
					//pred = pred.down;
					pred = readRef(pred,readSet,err);
					if(err.isSet()) return -1;
					locked[l] = pred;
					l++;
				}
			}
			
			pred = (Node<K, V>) preds[0];
			Node<K, V> curr = (Node<K, V>) succs[0]; 
			while(cmpMax.compareTo(curr.key) >= 0){		
				result[rangeCount] = curr.key;
				rangeCount++;
				pred = curr; 
				curr = readRef((Node<K, V>) curr.next[0],readSet,err);
				if(err.isSet()) return -1;
				locked[l] = curr;
				l++;
			}
			
			for(int i=0;i<l;i++){
				//lockSet.remove(locked[i]);
			}
			if (!validateReadOnly(readSet, self)) err.set();
			return rangeCount;
			
		}catch(Exception e){
			if(readSet.validate(self)){
				throw e; 
			}
			err.set();
			return 0;			
		}
	}
	
	@SuppressWarnings("unchecked")
	private int range2PLImpl(K[] result, Comparable<? super K> cmpMin,
		Comparable<? super K> cmpMax, Thread self) {
		//Object[] result = rangeSet.get();
		int rangeCount = 0; 
		
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		SpinHeapReentrant[] locked = threadLocked.get();
		int l =0;
		
		Node<K,V> pred = root; 
		pred.acquire(self);
		locked[l] = pred;
		l++;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			locked[l] = curr;
			l++;
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
				//pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
				locked[l] = curr;
				l++;
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				//pred = pred.down;
				pred.acquire(self);
				locked[l] = pred;
				l++;
			}
		}
		
		pred = (Node<K, V>) preds[0];
		Node<K, V> curr = (Node<K, V>) succs[0]; 
		while(cmpMax.compareTo(curr.key) >= 0){		
			result[rangeCount] = curr.key;
			rangeCount++;
			pred = curr; 
			curr = (Node<K, V>) curr.next[0]; 
			curr.acquire(self);	
			locked[l] = curr;
			l++;
		}
		
		for(int i=0;i<l;i++){
			release(locked[i]);
		}
		return rangeCount;
	}
}
