package trees.citrus;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Citrus<K, V> implements Map<K, V> {
	
	private final Comparator<? super K> comparator;
	private final K min; 
	private final Node<K, V> root; 
	
	public Citrus(final K min, final K max) {	
		this.min = min;
		this.root = new Node<K, V>(max,null); 
        this.comparator = null;        
    }

    public Citrus(final K min, final K max, final Comparator<? super K> comparator) {
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
	
	public V get(K key){
		final Comparable<? super K> k = comparable(key);
		RCU.rcuReadLock();
		Node<K, V> curr = this.root; 
		while(curr!=null){
			int res = k.compareTo(curr.key);
			if(res == 0) break; 
			if(res > 0){ //key > x.key
				curr=curr.right; 
			}else{
				curr=curr.left;		
			}
		}
		RCU.rcuReadUnlock();
		if(curr!=null){
			return curr.value;
		}
		return null; 
	}
	
	private boolean citrusValidate(Node<K,V> prev,int tag ,Node<K,V> curr, boolean isLeft){
		boolean result;     
		if (curr==null){
			if(isLeft){
				result = (!(prev.marked) &&  (prev.left==curr) && (prev.tagLeft==tag));
			}else{
				result = (!(prev.marked) &&  (prev.right==curr) && (prev.tagRight==tag));
			}
		}
		else {
			if(isLeft){
				result = (!(prev.marked) && !(curr.marked) && prev.left==curr);
			}else{
				result = (!(prev.marked) && !(curr.marked) && prev.right==curr);
			}
			
		}
		return result;
	}
	
	public V put(K key, V val){
		final Comparable<? super K> k = comparable(key);	
		V oldValue = null;
		while(true) {
			RCU.rcuReadLock();
			Node<K, V> prev = null;
			Node<K, V> curr = this.root; 
			int res = -1;
			while(curr!=null){
				prev=curr;
				res = k.compareTo(curr.key);
				if(res == 0) {
					//oldValue = curr.value;
					break; 
				}
				if(res > 0){ //key > x.key
					curr=curr.right; 
				}else{
					curr=curr.left;		
				}
			}
			int tag =  res < 0? prev.tagLeft : prev.tagRight;
			RCU.rcuReadUnlock();
			if(res == 0) {
				curr.lock.lock();
				if(!curr.marked){
					oldValue = curr.value; 
					curr.value = val; 
					curr.lock.unlock();
					return oldValue;
				}
				curr.lock.unlock();
				continue;
			}
			prev.lock.lock();
			if(citrusValidate(prev, tag ,curr, res < 0)){
				Node<K, V> node = new Node<K, V>(key,val); 
				assert(curr==null);
				if (res > 0 ) { 
					prev.right = node; 
				} else {
					prev.left = node; 
				}
				prev.lock.unlock();
				return oldValue;
			}
			prev.lock.unlock();	
		}
	}
	
	public V remove(K key){
		final Comparable<? super K> k = comparable(key);		
		while(true){
			RCU.rcuReadLock();
			Node<K, V> prev = null;
			Node<K, V> curr = this.root; 
			V oldValue = null;
			int res = -1;
			boolean isLeft = true; 
			while(curr!=null){			
				res = k.compareTo(curr.key);
				if(res == 0) {
					//oldValue = curr.value;
					break; 
				}
				prev=curr;
				if(res > 0){ //key > x.key
					curr=curr.right; 
					isLeft = false;
				}else{
					curr=curr.left;		
					isLeft = true; 
				}
			}
			RCU.rcuReadUnlock();
			if(res!= 0) {			
				return oldValue;
			}
			prev.lock.lock();
			curr.lock.lock();
			assert(curr!=null);
			oldValue = curr.value; 
			if(!citrusValidate(prev,0,curr,isLeft)){
				prev.lock.unlock();
				curr.lock.unlock();
				continue; 
			}
			if (curr.left == null){ //no left child
				curr.marked = true;
				if(isLeft){
					prev.left = curr.right;
					if(prev.left == null){
						prev.tagLeft++;
					}
				}else {
					prev.right= curr.right;
					if(prev.right == null){
						prev.tagRight++;
					}
				}
				prev.lock.unlock();
				curr.lock.unlock();
				return oldValue;
				
			}
			if (curr.right == null){ //no right child
				curr.marked = true;
				if(isLeft){
					prev.left = curr.left;
					if(prev.left == null){
						prev.tagLeft++;
					}
				}else {
					prev.right =  curr.left;
					if(prev.right == null){
						prev.tagRight++;
					}
				}
				prev.lock.unlock();
				curr.lock.unlock();
				return oldValue;
				
			} 
			//both children
			Node<K, V> prevSucc = curr; 
			Node<K, V> succ = curr.right; 
			Node<K, V> succL = succ.left; 
			while(succL != null){
				prevSucc = succ;
				succ = succL;
				succL = succ.left;
			}
			boolean isSuccLeft = true;
			if (prevSucc == curr){	
				isSuccLeft = false; 
			}
			prevSucc.lock.lock();
			succ.lock.lock();
			if(!(citrusValidate(prevSucc,0,succ,isSuccLeft) && citrusValidate(succ,succ.tagLeft,null,true))){
				prevSucc.lock.unlock();
				succ.lock.unlock();
				prev.lock.unlock();
				curr.lock.unlock();
				continue; 
			}
			curr.marked = true;
			Node<K,V> node = new Node<K,V>(succ.key,succ.value);
			node.left = curr.left;
			node.right = curr.right;
			node.lock.lock();
			if (isLeft){
				prev.left = node; 
			} else{
				prev.right = node;
			}
			RCU.synchronize();
			succ.marked = true;
			if (prevSucc == curr){
                node.right = succ.right;
                if(node.right == null){
                    node.tagRight++;
                }
            }
            else{
                prevSucc.left=succ.right;
                if(prevSucc.left == null){
                    prevSucc.tagLeft++;
                }
            }
			prevSucc.lock.unlock();
			succ.lock.unlock();
			prev.lock.unlock();
			curr.lock.unlock();
			node.lock.unlock();
			return oldValue;
		}
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
			this.marked = false;
			this.tagLeft = 0;
			this.tagRight = 0; 
			this.lock = new ReentrantLock();
		}
		
		final K key;
		V value;
		Node<K, V> left;
		Node<K, V> right; 
		boolean marked; 
		int tagLeft;
		int tagRight; 
		Lock lock; 
		
	}
}
