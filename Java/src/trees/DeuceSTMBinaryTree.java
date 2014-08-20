package trees;

import java.util.Comparator;
import java.util.HashSet;

import org.deuce.Atomic;



public class DeuceSTMBinaryTree<K, V> implements Map<K, V> {
	
	private final Comparator<? super K> comparator;
	private final K min; 
	private final Node<K, V> root; 
	
	public DeuceSTMBinaryTree(final K min, final K max) {	
		this.min = min;
		this.root = new Node<K, V>(max,null); 
        this.comparator = null;        
    }

    public DeuceSTMBinaryTree(final K min, final K max, final Comparator<? super K> comparator) {
    	this.min = min;
    	this.root = new Node<K, V>(max,null); 
        this.comparator = comparator;
    }
    
    /**
	 * Given some object, returns an appropriate {@link Comparable} object.
	 * If the comparator was initialized upon creating the tree, the 
	 * {@link Comparable} object uses it; otherwise, assume that the given 
	 * object implements {@link Comparable}.
	 *  
	 * @param object The object 
	 * @return The appropriate {@link Comparable} object
	 */
	@SuppressWarnings("unchecked")
	private Comparable<? super K> comparable(final Object object) {

		if (object == null) throw new NullPointerException();
		if (comparator == null) return (Comparable<? super K>)object;

		return new Comparable<K>() {
			final Comparator<? super K> compar = comparator;
			final K obj = (K) object;

			public int compareTo(final K other) { 
				return compar.compare(obj, other); 
			}
		};
	}
    
	public boolean validate(){		
		return doValidate(this.root.left, this.min , this.root.key); 
	}
	
	boolean doValidate(Node<K, V> node, K min, K max){
		if (node == null) return true; 
		final Comparable<? super K> k = comparable(node.key);
		if ( k.compareTo(max) >=0 || k.compareTo(min) <= 0 ) return false; 
		return doValidate(node.left,min,node.key) && doValidate(node.right,node.key,max); 
	}
	
	@Atomic
	public V get(K key){
		final Comparable<? super K> k = comparable(key);
		Node<K, V> x = this.root; 
		while(x!=null){
			int res = k.compareTo(x.key);
			if(res == 0) break; 
			if(res > 0){ //key > x.key
				x=x.right; 
			}else{
				x=x.left;		
			}
		}
		if(x!=null) return x.value;
		return null; 
	}
	
	@Atomic
	public V put(K key, V val){
		final Comparable<? super K> k = comparable(key);	
		V oldValue = null;
		Node<K, V> prev = null;
		Node<K, V> curr = this.root; 
		int res = -1;
		while(curr!=null){
			prev=curr;
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			if(res > 0){ //key > x.key
				curr=curr.right; 
			}else{
				curr=curr.left;		
			}
		}
		if(res == 0) {
			curr.value = val; 
			return oldValue;
		}
		Node<K, V> node = new Node<K, V>(key,val); 
		if (res > 0 ) { 
			prev.right = node; 
		} else {
			prev.left = node; 
		}
		return oldValue;
	}
	
	@Atomic
	public V remove(K key){
		final Comparable<? super K> k = comparable(key);		
		Node<K, V> prev = null;
		Node<K, V> curr = this.root; 
		V oldValue = null;
		int res = -1;
		
		while(curr!=null){			
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			prev=curr;
			if(res > 0){ //key > x.key
				curr=curr.right; 
			}else{
				curr=curr.left;		
			}
		}
		if(res!= 0) {			
			return oldValue;
		}
		Node<K, V> currL = curr.left; 
		Node<K, V> currR = curr.right; 
		Node<K, V> prevL = prev.left;
		boolean isLeft = prevL == curr; 
		if (currL == null){ //no left child
			if(isLeft){
				prev.left = currR;
			}else {
				prev.right= currR;
			}
		} else if (currR == null){ //no right child
			if(isLeft){
				prev.left = currL;
			}else {
				prev.right = currL;
			}
		}else { //both children
			Node<K, V> prevSucc = curr; 
			Node<K, V> succ = currR; 
			Node<K, V> succL = succ.left; 
			while(succL != null){
				prevSucc = succ;
				succ = succL;
				succL = succ.left;
			}
			
			if (prevSucc != curr){	
				Node<K, V> succR= succ.right; 
				prevSucc.left = succR;				
				succ.right = currR;
			}
			succ.left = currL;
			if (isLeft){
				prev.left = succ; 
			} else{
				prev.right = succ;
			}
		}
		return oldValue; 
	}
	
	@Override
	public HashSet<K> getAllKeys() {
		HashSet<K> keys = new HashSet<K>();
		getAllKeysImpl(this.root.left,keys);
		return keys;
	}

	private void getAllKeysImpl(Node<K, V> node, HashSet<K> keys) {
		if(node!=null){
			keys.add(node.key);
			getAllKeysImpl(node.left, keys);
			getAllKeysImpl(node.right, keys);
		}
		
	}

	private static class Node<K, V>{
		
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
		
	}
}
