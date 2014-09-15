package trees.skipList;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;
import util.localVersion.SpinHeapReentrant;

public class DominationLockingSkipList<K,V> implements Map<K,V>{
	
	private final Comparator<? super K> comparator;
	private final int maxLevel;
	private final int maxHeight;
	private final Node<K, V> root; 
	private final K min;
	private final K max;
	
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
	
	public DominationLockingSkipList(int max_items, final K min, final K max) {	
		this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
		this.maxLevel = maxHeight-1; 
		this.max = max; this.min = min;
		this.root = initRoot();	
        this.comparator = null;        
    }

    public DominationLockingSkipList(int max_items, final K min, final K max, final Comparator<? super K> comparator) {
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
			Node<K,V> curr = pred.next;
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
				curr = pred.next;
				curr.acquire(self);
			}			
			preds[layer] = pred;
			succs[layer] = curr;		
			if(layer != 0){
				pred = pred.down;
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
			Node<K,V> curr = pred.next;
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
				curr = pred.next;
				curr.acquire(self);
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred = pred.down;
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
			Node<K,V> curr = pred.next;
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
				curr = pred.next;
				curr.acquire(self);
			}
			preds[layer] = pred;
			succs[layer] = curr;
			if(layer != 0){
				pred = pred.down;
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
}
