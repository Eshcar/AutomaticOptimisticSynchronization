package trees.globalVersion;


import java.util.Comparator;
import java.util.HashSet;


import trees.Map;
import trees.globalVersion.Node;
import trees.globalVersion.Node.Direction;
import util.Error;
import util.globalVersion.GlobalVersion;
import util.globalVersion.GlobalVersionReadPhase;
import util.globalVersion.GlobalVersionReadWritePhase;
import util.globalVersion.GlobalVersionValidationPhase;
import util.globalVersion.ReadSet;

public class GlobalVersionBinaryTree<K,V> implements Map<K,V> {
	
	private final Comparator<K> comparator;
	private final K min; 
	private final Node<K, V> root; 
	
	private final GlobalVersionReadPhase<K, V> readPhaseStrategy = new GlobalVersionReadPhase<K, V>();
	private final GlobalVersionValidationPhase<K, V> validationStrategy = new GlobalVersionValidationPhase<K, V>();
	private final GlobalVersionReadWritePhase<K, V> readWritePhaseStrategy = new GlobalVersionReadWritePhase<K, V>();
	
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
    
	public GlobalVersionBinaryTree(final K min, final K max) {	
		this.min = min;
		this.root = new Node<K, V>(max,null); 
        this.comparator = null;        
    }

    public GlobalVersionBinaryTree(final K min, final K max, final Comparator<K> comparator) {
    	this.min = min;
    	this.root = new Node<K, V>(max,null); 
        this.comparator = comparator;
    }
    
	@SuppressWarnings("unchecked")
	private Comparable<K> comparable(final Object object) {

		if (object == null) throw new NullPointerException();
		if (comparator == null) return (Comparable<K>)object;

		return new Comparable<K>() {
			final Comparator<K> cmp = comparator;
			final K obj = (K) object;

			public int compareTo(final K other) { 
				return cmp.compare(obj, other); 
			}
		};
	}
    
	
	public V get(K key){
		final Comparable<K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = getImpl(k, err);
			if(!err.isSet()) break;
		}
		return value; 
	}
	
	private V getImpl(final Comparable<K> k , Error err){
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		int startVersion = GlobalVersion.getVersion();
		Node<K, V> curr = readPhaseStrategy.readGlobalRef(this.root, readSet, err, startVersion);
		if(err.isSet()) return null;
		while(curr!=null){
			int res = k.compareTo(curr.key);
			readPhaseStrategy.readValFromNode(curr, readSet,err, startVersion);
			if(err.isSet()) return null;			
			if(res == 0) break; 
			if(res > 0){ //key > x.key
				curr=readPhaseStrategy.readRef(curr.right,curr,readSet,err, startVersion);
				if(err.isSet()) return null;
			}else{
				curr=readPhaseStrategy.readRef(curr.left,curr,readSet,err, startVersion);
				if(err.isSet()) return null;
			}
		}
		
		if(curr!=null){
			V value = curr.value;
			readPhaseStrategy.readValFromNode(curr, readSet,err, startVersion);
			if(err.isSet()) return null;	
			//if (!validationStrategy.validateReadOnly(readSet, startVersion)) err.set();
			return value;
		}
		//if (!validationStrategy.validateReadOnly(readSet, startVersion)) err.set();
		return null;
	}
	
	public V put(K key, V val){
		final Comparable<K> k = comparable(key);
		V value;
		Error err = threadError.get();
		while(true){
			err.clean();
			value = putImpl(key, k, val, self.get(), err);
			if(!err.isSet()) break; 
		}
		return value;   
	}
	
	private V putImpl(K key, final Comparable<K> k , V val , final Thread self, Error err) {		
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		V oldValue = null; 
		
		//Read-only phase//
		int startVersion = GlobalVersion.getVersion();
		Node<K, V> prev = null;
		Node<K, V> curr = readPhaseStrategy.readGlobalRef(this.root,readSet,err, startVersion); 
		if(err.isSet()) return null;
		int res = -1;
		while(curr!=null){
			prev = curr;
			res = k.compareTo(curr.key);
			readPhaseStrategy.readValFromNode(curr, readSet,err, startVersion);
			if(err.isSet()) return null;	
			if(res == 0){
				oldValue = prev.value;
				readPhaseStrategy.readValFromNode(prev, readSet,err, startVersion);
				if(err.isSet()) return null;	
				break; 
			}
			if(res > 0){ //key > x.key
				curr= readPhaseStrategy.readRef(curr.right,curr,readSet,err, startVersion);
				if(err.isSet()) return null;
			}else{
				curr= readPhaseStrategy.readRef(curr.left,curr,readSet,err, startVersion);
				if(err.isSet()) return null;
			}
		}
		
		//Validation phase//
		int writeVersion = validationStrategy.validateTwo(readSet, prev, curr, self, startVersion);
		if(writeVersion == -1){
			err.set();
			return null;
		}
				
		//Read-write phase//
		if(res == 0){
			prev.setValue(val,writeVersion);
			readWritePhaseStrategy.release(prev);
			readWritePhaseStrategy.release(curr);
			return oldValue;
		}
		Node<K, V> node = readWritePhaseStrategy.acquire(new Node<K, V>(key,val),self);
		if (res > 0 ) { 
			prev.setChild(Direction.RIGHT,node,self, writeVersion); //true => right
		} else {
			prev.setChild(Direction.LEFT,node,self, writeVersion); 
		}
		readWritePhaseStrategy.release(prev);
		readWritePhaseStrategy.release(curr);
		readWritePhaseStrategy.release(node);
		assert(prev ==null || prev.lockedBy()!=self);
		assert(curr ==null || curr.lockedBy()!=self);
		assert(node ==null || node.lockedBy()!=self);
		return oldValue;
	}
	
	
	public V remove(K key){
		final Comparable<K> k = comparable(key);
		V value; 
		Error err = threadError.get();
		while(true){
			err.clean();
			value = removeImpl(k, self.get(), err);
			if(!err.isSet()) break; 
		}
		return value;  
	}
	
	private V removeImpl(Comparable<K> k, final Thread self, Error err){
		ReadSet<K,V> readSet = threadReadSet.get();
		readSet.clear(); 
		V oldValue = null;		
		int startVersion = GlobalVersion.getVersion();
		
		//Read-only phase//
		Node<K, V> prev = null;
		Node<K, V> curr = readPhaseStrategy.readGlobalRef(this.root,readSet,err, startVersion); 
		int res = -1;
		while(curr!=null){			
			res = k.compareTo(curr.key);
			readPhaseStrategy.readValFromNode(curr, readSet,err, startVersion);
			if(err.isSet()) return null;	
			if(res == 0){
				oldValue = curr.value;
				readPhaseStrategy.readValFromNode(curr, readSet,err, startVersion);
				if(err.isSet()) return null;	
				break; 
			}
			prev=curr;
			if(res > 0){ //key > x.key
				curr= readPhaseStrategy.readRef(curr.right,curr,readSet,err, startVersion); 
				if(err.isSet()) return null;
			}else{
				curr= readPhaseStrategy.readRef(curr.left,curr,readSet,err, startVersion);
				if(err.isSet()) return null;
			}
		}
		if(res!= 0) {
			//if(!validationStrategy.validateReadOnly(readSet, startVersion)) err.set();
			return oldValue;
		}
		
		//Validation phase//
		int writeVersion = validationStrategy.validateTwo(readSet, prev, curr, self, startVersion);
		if(writeVersion == -1){
			err.set();
			return null;
		}
				
		//Read-write phase//
		Node<K, V> currL = readWritePhaseStrategy.acquire(curr.left,self); 
		Node<K, V> currR = readWritePhaseStrategy.acquire(curr.right,self); 
		boolean isLeft = prev.left == curr; 
		if (currL == null){ //no left child
			if(isLeft){
				prev.setChild(Direction.LEFT,currR,self, writeVersion);
			}else {
				prev.setChild(Direction.RIGHT,currR,self, writeVersion);
			}
			curr.setChild(Direction.RIGHT,null,self, writeVersion);
		} else if (currR == null){ //no right child
			if(isLeft){
				prev.setChild(Direction.LEFT,currL,self, writeVersion);
			}else {
				prev.setChild(Direction.RIGHT,currL,self, writeVersion);
			}
			curr.setChild(Direction.LEFT,null,self, writeVersion);
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
				prevSucc.setChild(Direction.LEFT,succR,self, writeVersion);				
				succ.setChild(Direction.RIGHT,currR,self, writeVersion);
				readWritePhaseStrategy.release(succR);
			}
			succ.setChild(Direction.LEFT,currL,self, writeVersion);
			if (isLeft){
				prev.setChild(Direction.LEFT,succ,self, writeVersion); 
			} else{
				prev.setChild(Direction.RIGHT,succ,self, writeVersion); 
			}
			
			curr.setChild(Direction.RIGHT,null,self, writeVersion);
			curr.setChild(Direction.LEFT,null,self, writeVersion);
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
	
	/*for debugging, use serially only */
	public HashSet<K> getAllKeys(){
		HashSet<K> keys = new HashSet<K>();
		doGetAllKeys(this.root.left,keys);
		return keys; 
	}

	private void doGetAllKeys(Node<K, V> node, HashSet<K> keys ){
		if(node!=null){
			keys.add(node.key);
			doGetAllKeys(node.left, keys);
			doGetAllKeys(node.right, keys);
		}
	}
	
	public boolean validate(){		
		return doValidate(this.root.left, this.min , this.root.key); 
	}
	
	boolean doValidate(Node<K, V> node, K min, K max){
		if (node == null) return true; 
		final Comparable<K> k = comparable(node.key);
		if ( k.compareTo(max) >=0 || k.compareTo(min) <= 0 ) return false; 
		return doValidate(node.left,min,node.key) && doValidate(node.right,node.key,max); 
	}

}
