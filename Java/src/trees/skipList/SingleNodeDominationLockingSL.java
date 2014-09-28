package trees.skipList;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;
import util.localVersion.SpinHeapReentrant;

public class SingleNodeDominationLockingSL <K,V> implements Map<K,V>{
	
	private final Comparator<? super K> comparator;
	private final int maxLevel;
	private final int maxHeight;
	private final Node<K, V> root; 
	private final K min;
	private final K max;
	
	private Node<K,V> initRoot(){
		
		Node<K,V> sentenial = new Node<K,V>(max,null,maxHeight);
		return new Node<K,V>(min,null,maxHeight,sentenial);
	
	}
	
	private final ThreadLocal<Thread> self = new ThreadLocal<Thread>(){
        @Override
        protected Thread initialValue()
        {
            return Thread.currentThread();
        }
    };
	
	private final ThreadLocal<SkipListRandom> skipListRandom = new ThreadLocal<SkipListRandom>(){
        @Override
        protected SkipListRandom initialValue()
        {
            return new SkipListRandom();
        }
    };

	private final ThreadLocal<Object[]> threadPreds = new ThreadLocal<Object[]>();
	private final ThreadLocal<Object[]> threadSuccs = new ThreadLocal<Object[]>();
	
	public SingleNodeDominationLockingSL(int max_items, final K min, final K max) {	
		this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
		this.maxLevel = maxHeight-1; 
		this.max = max; this.min = min;
		this.root = initRoot();	
        this.comparator = null;        
    }

    public SingleNodeDominationLockingSL(int max_items, final K min, final K max, final Comparator<? super K> comparator) {
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
	
	@Override
	public V get(K key) {
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		return getImp(comparable(key),key,self.get());
	}

	
	@SuppressWarnings("unchecked")
	private V getImp(Comparable<? super K> cmp, K key, Thread self) {
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		V value = null;
		Node<K,V> pred = root; 
		pred.acquire(self);
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
			while (true) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					value = curr.value;
					for(int i= maxLevel; i > layer; i--){
						((Node<K,V>)preds[i]).release();
						((Node<K,V>)succs[i]).release();
					}
					pred.release();
					curr.release();
					return value;
				}
				if(res < 0){ //key < curr.key
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
				pred.acquire(self);
			}
		}
		for(int i= maxLevel; i > -1 ; i--){
			((Node<K,V>)preds[i]).release();
			((Node<K,V>)succs[i]).release();
		}
		return value;
	}

	public  V put(K key, V val){
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		return putImpl(comparable(key),key,val,self.get());
	}
	

	@SuppressWarnings("unchecked")
	private V putImpl(final Comparable<? super K> cmp, final K key, final V value, Thread self) {
		V oldValue = null;
		int height = skipListRandom.get().randomHeight(maxHeight);
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = root; 
		pred.acquire(self);
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
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
				pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred.acquire(self);
			}
		}
		
		if( layerFound!= -1 ){ 	//key was found change value only... 
			
			//No early unlocking... 
			for(int i = maxLevel ; i > layerFound ; i--){
				Node<K,V> curr = (Node<K,V>)succs[i];
				pred = ((Node<K,V>) preds[i]);
				pred.release();
				curr.release();
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
		for(int i = maxLevel ; i > height-1 ; i--){
			Node<K,V> curr = (Node<K,V>)succs[i];
			pred = ((Node<K,V>) preds[i]);
			pred.release();
			curr.release();
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
	public V remove(K key) {
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		return removeImpl(comparable(key),key,self.get());
	}

	@SuppressWarnings("unchecked")
	private V removeImpl(Comparable<? super K> cmp, K key, Thread self) {
		V oldValue = null;
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = root; 
		pred.acquire(self);
		for(int layer = maxLevel ; layer > -1 ; layer-- ){
			Node<K,V> curr = (Node<K, V>) pred.next[layer];
			curr.acquire(self);
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
				pred.release();
				pred = curr;
				curr = (Node<K, V>) pred.next[layer];
				curr.acquire(self);
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred.acquire(self);
			}
		}
		
		for(int i = maxLevel ; i > layerFound ; i--){
			Node<K,V> curr = (Node<K,V>)succs[i];
			pred = ((Node<K,V>) preds[i]);
			pred.release();
			curr.release();
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
	public boolean validate() {
		Node<K,V> prev = root;
		for(int layer = maxLevel ; layer> -1 ; layer-- ){
			if(!validateList(prev,layer)){
				return false; 
			};
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	private boolean validateList(Node<K, V> prev,int i){
		Node<K,V> curr = (Node<K, V>) prev.next[i]; 
		if(curr == null) return true; //empty list 
		prev = curr; 
		curr = (Node<K, V>) curr.next[i]; 
		while(curr!=null){
			if(comparable(prev.key).compareTo(curr.key) >= 0){
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
}
