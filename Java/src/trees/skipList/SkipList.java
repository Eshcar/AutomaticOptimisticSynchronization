package trees.skipList;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;


public class SkipList<K,V> implements Map<K,V>{
	
	private final Comparator<? super K> comparator;
	private final int maxHeight; 
	private final Node<K, V> root; 
	
	private Node<K,V> initRoot(){
		Node<K,V> root = new Node<K,V>(null,null);
		Node<K,V> prev = root;
		Node<K,V> curr = null; 
		for(int i =0; i < maxHeight -1  ; i++){
			curr = new Node<K,V>(null,null);
			prev.down = curr; 
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

	private final ThreadLocal<Object[]> threadPreds = new ThreadLocal<Object[]>();
	private final ThreadLocal<Object[]> threadSuccs = new ThreadLocal<Object[]>();
	
	public SkipList(int max_items) {	
		this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
		this.root = initRoot();	
        this.comparator = null;        
    }

    public SkipList(int max_items, final Comparator<? super K> comparator) {
    	this.maxHeight = (int) Math.ceil(Math.log(max_items) / Math.log(2));
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
		// TODO Auto-generated method stub
		return getImp(comparable(key),key);
	}

	
	private V getImp(Comparable<? super K> cmp, K key) {
		Node<K,V> pred = root; 
		for(int layer = maxHeight-1 ; layer> -1 ; layer-- ){
			Node<K,V> curr = pred.next;
			while (curr!=null) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					return curr.value;
				}
				if(res < 0){ //key < x.key
					break;
				}			
				pred = curr; 		
				curr = pred.next;
			}
			pred = pred.down; 
		}
		return null;
	}

	public  V put(K key, V val){
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		return putImpl(comparable(key),key,val);
	}
	

	@SuppressWarnings("unchecked")
	private V putImpl(final Comparable<? super K> cmp, final K key, final V value) {
		V oldValue = null;
		int height = skipListRandom.get().randomHeight(maxHeight);
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = root; 
		for(int layer = maxHeight-1 ; layer> -1 ; layer-- ){
			Node<K,V> curr = pred.next;
			while (curr!=null) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					oldValue = curr.value;
					break; 
				}
				if(res < 0){ //key < x.key
					break;
				}			
				pred = curr; 		
				curr = pred.next;
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			pred = pred.down; 
		}
		
		if( succs[0]!=null && ((Node<K,V>)succs[0]).key == key ){
			//key was found change value only... 
			for(int i = maxHeight-1 ; i > -1 ; i--){
				Node<K,V> curr = (Node<K,V>)succs[i];
				if( curr !=null && curr.key == key ){
					curr.value = value;
				}
			}
			
			return oldValue;
		}
		
		Node<K,V> prevNode = null;
		for(int i = height-1 ; i > -1 ; i--){
			Node<K,V> node = new Node<K,V>(key,value);
			node.next = (Node<K,V>) succs[i];
			if(prevNode != null) { prevNode.down = node; }
			((Node<K,V>) preds[i]).next = node;
			prevNode = node; 
		}
		
		return oldValue;	
	}
	
	@Override
	public V remove(K key) {
		if(threadPreds.get() == null){
			threadPreds.set(new Object[maxHeight]);
			threadSuccs.set(new Object[maxHeight]);
		}
		return removeImpl(comparable(key),key);
	}

	@SuppressWarnings("unchecked")
	private V removeImpl(Comparable<? super K> cmp, K key) {
		V oldValue = null;
		int layerFound = -1; 
		Object[] preds = threadPreds.get();
		Object[] succs = threadSuccs.get();
		
		Node<K,V> pred = root; 
		for(int layer = maxHeight-1 ; layer> -1 ; layer-- ){
			Node<K,V> curr = pred.next;
			while (curr!=null) {
				int res = cmp.compareTo(curr.key);
				if(res == 0) {
					oldValue = curr.value;
					if(layerFound == -1){ layerFound=layer; }
					break; 
				}
				if(res < 0){ //key < x.key
					break;
				}			
				pred = curr; 		
				curr = pred.next;
	
			}
			preds[layer] = pred;
			succs[layer] = curr;
			pred = pred.down; 
		}
		
		for(int i = layerFound ; i > -1 ; i--){
			pred = ((Node<K,V>) preds[i]);
			Node<K,V> succ = ((Node<K,V>) succs[i]);
			pred.next = succ.next; 
		}
		
		return oldValue;
	}

	@Override
	public boolean validate() {
		Node<K,V> prev = root;
		for(int layer = maxHeight-1 ; layer> -1 ; layer-- ){
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

	private static class Node<K, V>{		
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
		while(prev!=null){
			hash.add(prev.key); 
			prev = prev.next;
		}
		return hash;
	}
	
    

}
