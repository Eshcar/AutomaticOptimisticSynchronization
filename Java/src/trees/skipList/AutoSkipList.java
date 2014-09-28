package trees.skipList;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;
import util.Error;
import util.localVersion.ReadSet;
import util.localVersion.SpinHeapReentrant;

public class AutoSkipList<K,V> implements Map<K,V>{
	
	private final Comparator<? super K> comparator;
	private final int maxLevel;
	private final int maxHeight;
	private final Node<K, V> root; 
	private final K min;
	private final K max;
	
	private final long LIMIT = 2000; 
	
	private Node<K,V> initRoot(){
		Node<K,V> root = new Node<K,V>(min,null);
		root.next = new Node<K,V>(max,null);
		Node<K,V> prev = root;
		Node<K,V> curr = null; 
		for(int i =0; i < maxLevel  ; i++){
			curr = new Node<K,V>(min,null);
			curr.next = new Node<K,V>(max,null);
			prev.down = curr;
			prev.next.down = curr.next;
			prev = curr;
		}
		return root;
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
    
    private final ThreadLocal<ReadSet<K,V>> threadReadSet = new ThreadLocal<ReadSet<K,V>>(){
        @Override
        protected ReadSet<K,V> initialValue()
        {
            return new ReadSet<K,V>(); 
        }
    };


	private final ThreadLocal<Object[]> threadPreds = new ThreadLocal<Object[]>();
	private final ThreadLocal<Object[]> threadSuccs = new ThreadLocal<Object[]>();
	
	public AutoSkipList(int max_items, final K min, final K max) {	
		this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
		this.maxLevel = maxHeight-1; 
		this.max = max; this.min = min;
		this.root = initRoot();	
        this.comparator = null;        
    }

    public AutoSkipList(int max_items, final K min, final K max, final Comparator<? super K> comparator) {
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
	private boolean validatePhase(Object[] preds, Object[] succs,
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
	private void releaseLevels(Object[] preds, Object[] succs, int top, int bottom) {
			for(int i=top; i>bottom;i--){
				release((Node<K, V>) preds[i]);
				release((Node<K, V>) succs[i]);
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
			Node<K,V> curr = readRef(pred.next,readSet,err);
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
				curr = readRef(pred.next, readSet, err);
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
				pred = readRef(pred.down,readSet,err);
				if(err.isSet()) return null;
				
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
		int height = skipListRandom.get().randomHeight(maxHeight);
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = readRef(root,readSet,err); 
		if(err.isSet()) return null;
		
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = readRef(pred.next,readSet,err);
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
				curr = readRef(pred.next,readSet,err);
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
				pred = readRef(pred.down,readSet,err);
				if(err.isSet()) return null;
			}
		}
		
		if( layerFound!= -1 ){ 	//key was found change value only... 
			
			//No early unlocking... 
			//VALIDATE!!! 
			if(!validatePhase(preds,succs,layerFound,readSet,self)){
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
		
		
		//no early unlocking 
		if(!validatePhase(preds,succs,height-1,readSet,self)){
			err.set();
			return null; 
		}
		
		Node<K,V> prevNode = null;
		for(int i = height-1 ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			Node<K,V> node = new Node<K,V>(key,value);
			node.acquire(self);
			node.next = succ;
			if(prevNode != null) { 
				prevNode.down = node; 
				prevNode.release(); 
			}
			pred.next = node;
			prevNode = node; 
			pred.release();
			succ.release();
		}
		prevNode.release();
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
			Node<K,V> curr = readRef(pred.next,readSet,err);
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
				curr = readRef(pred.next,readSet,err);
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
				pred = readRef(pred.down,readSet,err);
				if(err.isSet()) return null;
			}
		}
		
		if(layerFound == -1){
			if (!validateReadOnly(readSet, self)) err.set();
			return null;
		}
		
		if(!validatePhase(preds,succs,layerFound,readSet,self)){
			err.set();
			return null; 
		}
		
		for(int i = layerFound ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			pred.next = succ.next;
			pred.release();
			succ.release();
		}
		
		return oldValue;
	}

	@Override
	public boolean validate() {
		Node<K,V> prev = root;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			if(!validateList(prev)){
				return false; 
			};
			prev= prev.down;
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
	
	private boolean validateList(Node<K, V> prev){
		Node<K,V> curr = prev.next; 
		if(curr == null) return true; //empty list 
		prev = curr; 
		curr = curr.next; 
		while(curr!=null){
			if(comparable(prev.key).compareTo(curr.key) >= 0){
				return false;
			}
			prev = curr; 
			curr = curr.next;
		}
		return true;
	}

	private static class Node<K, V>  extends SpinHeapReentrant{		
		private static final long serialVersionUID = 1L;
		public Node(K key, V value) {
			this.key = key;
			this.value = value;
			this.next = null;
			this.down = null;
		}
		
		final K key;
		V value;
		Node<K, V> next;
		Node<K, V> down; 	
	}


	@Override
	public HashSet<K> getAllKeys() {
		HashSet<K> hash = new HashSet<K>(); 
		Node<K,V> prev = root; 
		while(prev.down != null){
			prev = prev.down;
		}
		prev= prev.next;
		while(prev.key!=max){
			hash.add(prev.key); 
			prev = prev.next;
		}
		return hash;
	}
	
	
	
	public HashSet<K> getItemsInLevel(int level){
		HashSet<K> hash = new HashSet<K>();
		Node<K,V> prev = root; 
		int i = maxLevel; 
		while(i!=level){
			i--;
			prev = prev.down;
		}
		prev= prev.next;
		while(prev.key!=max){
			hash.add(prev.key); 
			prev = prev.next;
		}
		return hash;
	}
}