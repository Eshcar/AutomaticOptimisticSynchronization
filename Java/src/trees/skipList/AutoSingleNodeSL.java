package trees.skipList;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;
import trees.RangeMap;
import util.Error;
import util.localVersion.ReadSet;
import util.localVersion.SpinHeapReentrant;


public class AutoSingleNodeSL<K,V> implements RangeMap<K, V>{
	
	private final Comparator<? super K> comparator;
	private final int maxLevel;
	private final int maxHeight;
	private final Node<K, V> root; 
	private final K min;
	private final K max;
	
	private final int LIMIT = 1700; 
	
	private Node<K,V> initRoot(){
		
		Node<K,V> sentenial = new Node<K,V>(max,null,maxHeight);
		return new Node<K,V>(min,null,maxHeight,sentenial);
	
	}
	
	
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
    
    private final ThreadLocal<Object[]> rangeSet = new ThreadLocal<Object[]>(){
        @Override
        protected Object[] initialValue()
        {
            return new Object[LIMIT]; 
        }
    };
    
    private final ThreadLocal<ReadSet<K,V>> threadReadSet = new ThreadLocal<ReadSet<K,V>>(){
        @Override
        protected ReadSet<K,V> initialValue()
        {
            return new ReadSet<K,V>(); 
        }
    };
    
    private final ThreadLocal<ReadSet<K,V>> threadLargeReadSet = new ThreadLocal<ReadSet<K,V>>(){
        @Override
        protected ReadSet<K,V> initialValue()
        {
            return new ReadSet<K,V>(1024); 
        }
    };


	private final ThreadLocal<Object[]> threadPreds = new ThreadLocal<Object[]>();
	private final ThreadLocal<Object[]> threadSuccs = new ThreadLocal<Object[]>();
	
	public AutoSingleNodeSL(int max_items, final K min, final K max) {	
		this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
		this.maxLevel = maxHeight-1; 
		this.max = max; this.min = min;
		this.root = initRoot();	
        this.comparator = null;        
    }

    public AutoSingleNodeSL(int max_items, final K min, final K max, final Comparator<? super K> comparator) {
    	this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
		this.maxLevel = maxHeight-1;
    	this.max = max; this.min = min;
    	this.root = initRoot();
        this.comparator = comparator;
    }
    
    
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
	private boolean mixedValidatePhase(Object[] preds, Object[] succs,
			int firstLayer, int secondLayer, ReadSet<K, V> readSet, Thread self){
		for(int i=firstLayer; i>secondLayer ; i--){
			Node<K,V> pred = (Node<K, V>) preds[i];
			if(!tryAcquire(pred, readSet, self)){
				releaseLevel(preds,firstLayer,i);
				return false;
			}
		}
		for(int i=secondLayer; i>-1 ; i--){
			Node<K,V> succ = (Node<K, V>) succs[i];
			if(!tryAcquire(succ, readSet, self)){
				releaseLevel(preds,firstLayer,secondLayer);
				releaseLevel(succs,secondLayer,i);
				return false;
			}
		}
		if(!readSet.validate(self)){
			releaseLevel(preds,firstLayer,secondLayer);
			releaseLevel(succs,secondLayer,-1);
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
	
	@Override
	public V get(K key) {
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}		
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = getImp(comparable(key),key,self.get(),err);
			if(!err.isSet()) break;
		}
		return value; 
		
	}

	
	@SuppressWarnings("unchecked")
	private V getImp(Comparable<? super K> cmp, K key, Thread self, Error err) {
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

	public  V put(K key, V val){
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
			if(layer != 0){
				//pred = readRef(pred.down,readSet,err);
				//if(err.isSet()) return null;
			}
		}
		
		if( layerFound!= -1 ){ 	//key was found change value only... 
			
			//No early unlocking... 
			//VALIDATE!!! 
			if(!smallValidatePhase(succs,layerFound,readSet,self)){
				err.set();
				return null; 
			}
			
			for(int i = layerFound ; i > -1 ; i--){
				Node<K,V> curr = (Node<K,V>)succs[i];
				//pred = ((Node<K,V>) preds[i]);
				if( cmp.compareTo(curr.key )!= 0 ){
					assert(false);
				};
				curr.value = value;
				//pred.release();
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
			//Node<K,V> succ = ((Node<K,V>) succs[i]);	
			node.next[i] = pred.next[i];
			pred.next[i] = node;
			pred.release();
			//succ.release();
		}
		node.release();
		return oldValue;	
	}
	
	

	@Override
	public V remove(K key) {
		V value; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
			value = removeImpl(comparable(key),key,self.get(),err);
			if(!err.isSet()) break; 
		}
		return value;  
	}

	@SuppressWarnings("unchecked")
	private V removeImpl(Comparable<? super K> cmp, K key, Thread self,Error err) {
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
			if(layer != 0){
				//pred = readRef(pred.down,readSet,err);
				//if(err.isSet()) return null;
			}
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
	
	public int getRange(K min, K max){
		boolean combinationRange = true; 
		if(combinationRange){
			return getCombinationRange(min, max);
		}
		int value; 		
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
			value = optRangeImpl(comparable(min), comparable(max), self.get(),err);
			if(!err.isSet()) break; 
		}
		return value;  
	}
	


	private int getCombinationRange(K min, K max) {
		int value; 
		int count = 0; 
		Error err = threadError.get();
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		while(true){
			err.clean();
			if(count< 3){
				value = optRangeImpl(comparable(min), comparable(max), self.get(),err);
				if(!err.isSet()) break; 
				count++;
			}else if(count <6 ){
				value = lockedRangeImpl(comparable(min), comparable(max), self.get(),err);
				if(!err.isSet()) break;
				count++;
			}else{
				return value = dominationRangeImpl(comparable(min), comparable(max),self.get());
			}
		}
		return value; 
	}

	@SuppressWarnings("unchecked")
	private int dominationRangeImpl(Comparable<? super K> cmpMin,
			Comparable<? super K> cmpMax, Thread self) {
		Object[] result = rangeSet.get();
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
		
		if( layerFound!= -1 ){ 	
			int lockLayer = layerFound; 
			pred = (Node<K, V>) succs[lockLayer]; 
			Node<K,V> next = (Node<K, V>) pred.next[lockLayer];
			while(cmpMax.compareTo(next.key) >= 0 ){ // curr.next is inside the range, go up
				lockLayer++;
				pred = (Node<K, V>) preds[lockLayer]; 
				next = (Node<K, V>) pred.next[lockLayer];
			}
			
			//unlock uneeded locks in preds and succs... 
			for(int i = maxLevel ; i > lockLayer ; i--){
				Node<K,V> curr = (Node<K,V>)succs[i];
				pred = ((Node<K,V>) preds[i]);
				pred.release();
				curr.release();
			}
			for(int i = lockLayer ; i > layerFound ; i--){
				Node<K,V> curr = (Node<K,V>)succs[i];
				curr.release();
			}
			
			//travel the linked list 
			pred = (Node<K, V>) preds[0];
			Node<K, V> curr = (Node<K, V>) succs[0]; 
			result[rangeCount] = curr.key;
			rangeCount++;
			pred = curr;
			curr = (Node<K, V>) curr.next[0]; 
			curr.acquire(self);
			while(cmpMax.compareTo(curr.key) >= 0){		
				result[rangeCount] = curr.key;
				rangeCount++;
				if ( !pred.equals(succs[0])){ 
					pred.release();
				}
				pred = curr; 
				curr = (Node<K, V>) curr.next[0]; 
				curr.acquire(self);	
			}
			curr.release();
			if ( !pred.equals(succs[0])){ //succs[layer] equals what should be succs[0]
				pred.release();
			}
			for(int i = lockLayer ; i > layerFound ; i--){
				pred = (Node<K,V>)preds[i];
				pred.release();
			}
			
			for(int i = layerFound ; i > -1 ; i--){
				curr = (Node<K,V>)succs[i];
				pred = ((Node<K,V>) preds[i]);
				pred.release();
				curr.release();
			}
			return rangeCount; 
		}
		
		//key was not found
			int lockLayer = 0; 
			Node<K,V> next = (Node<K, V>) succs[lockLayer];
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
			Node<K, V> curr = (Node<K, V>) succs[0]; 
			curr.acquire(self);
			while(cmpMax.compareTo(curr.key) >= 0){		
				result[rangeCount] = curr.key;
				rangeCount++;
				if ( !pred.equals(preds[0])){ 
					pred.release();
				}
				pred = curr; 
				curr = (Node<K, V>) curr.next[0]; 
				curr.acquire(self);	
			}
			curr.release();
			if ( !pred.equals(preds[0])){ //succs[layer] equals what should be succs[0]
				pred.release();
			}
			
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
		ReadSet<K,V> readSet = threadLargeReadSet.get();
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
					Object[] result = rangeSet.get();
					int rangeCount = 0; 
					while(cmpMax.compareTo(curr.key) >= 0){
						result[rangeCount] = curr.key;
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
		Node<K,V> curr = (Node<K, V>) preds[0]; 
		Object[] result = rangeSet.get();
		int rangeCount = 0; 
		while(cmpMin.compareTo(curr.key)>0){
			curr = readRef((Node<K, V>)curr.next[0],readSet,err);
			if(err.isSet()) return -1;		
		}
		while(cmpMax.compareTo(curr.key) >= 0){
			result[rangeCount] = curr.key;
			rangeCount++;
			curr = readRef((Node<K, V>)curr.next[0],readSet,err);
			if(err.isSet()) return -1;			
		}
		if (!validateReadOnly(readSet, self)) err.set();
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
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return -1;
		
		for(int layer = maxLevel ; layer > -1 ; layer-- ){
			Node<K,V> curr = readRef((Node<K, V>)pred.next[layer],readSet,err);
			if(err.isSet()) return -1;			
			while (true) {
				int res = cmpMin.compareTo(curr.key);
				if(res == 0) {
					//key found...
					//Layer is the first level such that curr.key = min
					//if this layer does not cover the range, lockLayer is used
					//In lockLayer preds needs to be used 
					for(int j =layer; j>-1; j--){
						succs[j] = curr;			
					}
					int lockLayer = layer; 
					Node<K,V> next = readRef( (Node<K, V>) curr.next[lockLayer],readSet,err);
					if(err.isSet()) return -1;
					while(cmpMax.compareTo(next.key) >= 0 ){ // curr.next is inside the range, go up
						lockLayer++;
						pred = (Node<K, V>) preds[lockLayer]; 
						next = readRef( (Node<K, V>) pred.next[lockLayer],readSet,err);
						if(err.isSet()) return -1;

					}
					
					//VALIDATION lock only succs from lockedLayer
					if(!mixedValidatePhase(preds,succs,lockLayer,layer,readSet,self)){
						err.set();
						return -1; 
					}
					
					Object[] result = rangeSet.get();
					int rangeCount = 0; 
					pred = curr;
					result[rangeCount] = curr.key;
					rangeCount++;
					curr = (Node<K, V>) curr.next[0]; 
					acquire(curr,self);
					while(cmpMax.compareTo(curr.key) >= 0){		
						result[rangeCount] = curr.key;
						rangeCount++;
						if ( !pred.equals(succs[layer])){ //succs[layer] equals what should be succs[0]
							release(pred);
						}
						pred = curr; 
						curr = (Node<K, V>) curr.next[0]; 
						acquire(curr,self);	
					}
					release(curr);
					if ( !pred.equals(succs[layer])){ //succs[layer] equals what should be succs[0]
						release(pred);
					}
					releaseLevel(preds,lockLayer,layer);
					releaseLevel(succs,layer,-1);
					return rangeCount;
					
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
		
		int lockLayer = 0; 
		Node<K,V> next = (Node<K, V>) succs[lockLayer];
		while(cmpMax.compareTo(next.key) >= 0 ){ // curr.next is inside the range, go up
			lockLayer++;
			next = (Node<K, V>) succs[lockLayer];
		}

		//VALIDATION lock preds from lockLayer
		if(!smallValidatePhase(preds,lockLayer,readSet,self)){
			err.set();
			return -1; 
		}
		
		Object[] result = rangeSet.get();
		int rangeCount = 0; 
		pred = (Node<K, V>) preds[0];
		Node<K,V> curr = (Node<K, V>) pred.next[0]; 
		acquire(curr,self);
		while(cmpMax.compareTo(curr.key) >= 0){		
			if(cmpMin.compareTo(curr.key) < 0){
				result[rangeCount] = curr.key;
				rangeCount++;
			}
			if ( !pred.equals(preds[0])){
				release(pred);
			}
			pred = curr; 
			curr = (Node<K, V>) curr.next[0]; 
			acquire(curr,self);	
		}
		release(curr);
		if ( !pred.equals(preds[0])){
			release(pred);
		}
		releaseLevel(preds,lockLayer,-1);
		return rangeCount;
	}

	
	@Override
	public boolean validate() {
		Node<K,V> prev = root;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			if(!validateList(prev,layer)){
				return false; 
			};
		}
		for(int i=0; i<maxLevel; i++ ){
			HashSet<K> lowerHash = getItemsInLevel(i);
			HashSet<K> hash = getItemsInLevel(i+1);
			for(K k: hash){
				if(!lowerHash.contains(k)){
					System.out.println("key "+k+" in level"+(i+1)+" but not in level" +i);
					return false; 
				}
			}
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	private boolean validateList(Node<K, V> prev, int i){
		if(prev.isLocked()) return false;
		Node<K,V> curr = (Node<K, V>) prev.next[i]; 
		if(curr == null || curr.isLocked()) return false; //missing end...  
		prev = curr; 
		curr = (Node<K, V>) curr.next[i]; 
		while(curr!=null){
			if(comparable(prev.key).compareTo(curr.key) >= 0){
				return false;
			}
			if(curr.isLocked()){
				return false; 
			}
			prev = curr; 
			curr = (Node<K, V>) curr.next[i];
		}
		return true;
	}

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


	@SuppressWarnings("unchecked")
	@Override
	public HashSet<K> getAllKeys() {
		HashSet<K> hash = new HashSet<K>(); 
		Node<K,V> prev = root; 
		
		prev = (Node<K, V>) prev.next[0];
		while(prev.key!=max){
			hash.add(prev.key); 
			prev = (Node<K, V>) prev.next[0];
		}
		return hash;
	}
	
	
	@SuppressWarnings("unchecked")
	public HashSet<K> getItemsInLevel(int level){
		HashSet<K> hash = new HashSet<K>();
		Node<K,V> prev = root; 
		prev= (Node<K, V>) prev.next[level];
		while(prev.key!=max){
			hash.add(prev.key); 
			prev = (Node<K, V>) prev.next[level];
		}
		return hash;
	}
}