package trees.localVersion;

import java.util.Comparator;
import java.util.HashSet;

import trees.Map;
import trees.localVersion.Node.Direction;
import util.ReadWritePhaseStrategy;
import util.localVersion.LocalVersionReadWritePhase;


public class DominationLockingBinaryTree<K, V> implements Map<K, V> {
	
	private final ReadWritePhaseStrategy<K, V> readWritePhaseStrategy = new LocalVersionReadWritePhase<K, V>();
	
	private final Comparator<? super K> comparator;
	private final K min; 
	private final Node<K, V> root; 
	
	public DominationLockingBinaryTree(final K min, final K max) {	
		this.min = min;
		this.root = new Node<K, V>(max,null); 
        this.comparator = null;        
    }

    public DominationLockingBinaryTree(final K min, final K max, final Comparator<? super K> comparator) {
    	this.min = min;
    	this.root = new Node<K, V>(max,null); 
        this.comparator = comparator;
    }
    
    private final ThreadLocal<Thread> threadSelf = new ThreadLocal<Thread>(){
        @Override
        protected Thread initialValue()
        {
            return Thread.currentThread();
        }
    };
    
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
		Thread self = threadSelf.get();
		
		Node<K, V> curr = readWritePhaseStrategy.acquire(this.root,self); 
		while(curr!=null){
			int res = k.compareTo(curr.key);
			if(res == 0) break; 
			if(res > 0){ //key > x.key
				curr = readWritePhaseStrategy.assign(curr, curr.right, self); 
			}else{
				curr = readWritePhaseStrategy.assign(curr, curr.left, self); 
			}
		}
		if(curr!=null){
			readWritePhaseStrategy.release(curr);
			return curr.value;
		}
		return null; 
	}
	
	public V put(K key, V val){
		final Comparable<? super K> k = comparable(key);
		Thread self = threadSelf.get();
		
		V oldValue = null;
		Node<K, V> prev = null;
		Node<K, V> curr = readWritePhaseStrategy.acquire(this.root, self); 
		int res = -1;
		while(curr!=null){
			prev = readWritePhaseStrategy.assign(prev,curr,self);
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			if(res > 0){ //key > x.key
				curr = readWritePhaseStrategy.assign(curr, curr.right, self) ; 
			}else{
				curr = readWritePhaseStrategy.assign(curr, curr.left, self) ; 
			}
		}
		if(res == 0) {
			curr.value = val; 
			readWritePhaseStrategy.release(prev);
			readWritePhaseStrategy.release(curr);
			return oldValue;
		}
		Node<K, V> node = readWritePhaseStrategy.acquire(new Node<K, V>(key,val),self);
		if (res > 0 ) { 
			prev.setChild(Direction.RIGHT,node,self); 
		} else {
			prev.setChild(Direction.LEFT,node,self); 
		}
		readWritePhaseStrategy.release(prev);
		readWritePhaseStrategy.release(curr);
		readWritePhaseStrategy.release(node);
		return oldValue;
	}
	
	public V remove(K key){
		final Comparable<? super K> k = comparable(key);
		Thread self = threadSelf.get();
		
		V oldValue = null;
		Node<K, V> prev = null;
		Node<K, V> curr = readWritePhaseStrategy.acquire(this.root, self); 
		int res = -1;
		while(curr!=null){
			res = k.compareTo(curr.key);
			if(res == 0) {
				oldValue = curr.value;
				break; 
			}
			prev = readWritePhaseStrategy.assign(prev,curr,self);
			if(res > 0){ //key > x.key
				curr = readWritePhaseStrategy.assign(curr, curr.right, self) ; 
			}else{
				curr = readWritePhaseStrategy.assign(curr, curr.left, self) ; 
			}
		}
		
		if(res!= 0) {	
			readWritePhaseStrategy.release(prev);
			readWritePhaseStrategy.release(curr);
			return oldValue;
		}
		Node<K, V> currL = readWritePhaseStrategy.acquire(curr.left,self); 
		Node<K, V> currR = readWritePhaseStrategy.acquire(curr.right,self); 
		boolean isLeft = prev.left == curr; 
		if (currL == null){ //no left child
			if(isLeft){
				prev.setChild(Direction.LEFT,currR,self);
			}else {
				prev.setChild(Direction.RIGHT,currR,self);
			}
			curr.setChild(Direction.RIGHT,null,self);
		} else if (currR == null){ //no right child
			if(isLeft){
				prev.setChild(Direction.LEFT,currL,self);
			}else {
				prev.setChild(Direction.RIGHT,currL,self);
			}
			curr.setChild(Direction.LEFT,null,self);
		}else { //both children
			Node<K, V> prevSucc =  readWritePhaseStrategy.acquire(curr,self); //TODO re-acquire ?? 
			Node<K, V> succ = readWritePhaseStrategy.acquire(currR,self);  //TODO re-acquire ?? 
			Node<K, V> succL =  readWritePhaseStrategy.acquire(succ.left,self); 
			while(succL != null){
				prevSucc =readWritePhaseStrategy.assign(prevSucc,succ,self);
				succ = readWritePhaseStrategy.assign(succ,succL,self);
				succL =  readWritePhaseStrategy.assign(succL,succ.left,self);
			}
			
			if (prevSucc != curr){	
				Node<K, V> succR=  readWritePhaseStrategy.acquire(succ.right,self); 
				prevSucc.setChild(Direction.LEFT,succR,self);				
				succ.setChild(Direction.RIGHT,currR,self);
				readWritePhaseStrategy.release(succR);
			}
			succ.setChild(Direction.LEFT,currL,self);
			if (isLeft){
				prev.setChild(Direction.LEFT,succ,self); 
			} else{
				prev.setChild(Direction.RIGHT,succ,self); 
			}
			
			curr.setChild(Direction.RIGHT,null,self);
			curr.setChild(Direction.LEFT,null,self);
			readWritePhaseStrategy.release(prevSucc);
			readWritePhaseStrategy.release(succ);
			readWritePhaseStrategy.release(succL);
			
		}
		
		readWritePhaseStrategy.release(prev);
		readWritePhaseStrategy.release(curr);
		readWritePhaseStrategy.release(currL);
		readWritePhaseStrategy.release(currR);
		assert(prev ==null || prev.lockedBy()!=self );
		assert(curr ==null || curr.lockedBy()!=self);
		assert(currL ==null || currL.lockedBy()!=self);
		assert(currR ==null || currR.lockedBy()!=self);
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
}
