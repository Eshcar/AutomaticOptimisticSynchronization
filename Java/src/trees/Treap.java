package trees;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;




import util.FastSimpleRandom;

public class Treap<K,V> implements Map<K, V>{
	enum Direction {
        LEFT, RIGHT
    }

    private static class Node<K,V>  {
        final K key;
        V value;
        final int priority;
        Node<K,V> left;
        Node<K,V> right;

        Node(final K key, final V value, final int priority, final Node<K,V> left, final Node<K,V> right) {
            this.key = key;
            this.value = value;
            this.priority = priority;
            this.left = left;
            this.right = right;
        }

        // for the analysis we have inlined this procedure
        public void setChild(final Direction dir, final Node<K,V> child) {
            if (dir == Direction.LEFT)
                this.left = child;
            else
                this.right = child;
        }
    }

    private static class KeyCmp<K> implements Comparable<K> {
        private final Comparator<K> cmp;
        private final K key;

        private KeyCmp(final Comparator<K> cmp, final K key) {
            this.cmp = cmp;
            this.key = key;
        }

        public int compareTo(final K rhs) {
            return cmp.compare(key, rhs);
        }
    }

    final Comparator<K> cmp;
    final Node<K,V> rootHolder = new Node<K,V>(null, null, 0, null, null);

    public Treap() {
        this(null);
    }

    public Treap(final Comparator<K> cmp) {
        this.cmp = cmp;
    }

    public void clear() {   
        rootHolder.right = null;
    }

    @SuppressWarnings("unchecked")
    private Comparable<K> comparable(final Object key) {
        return (cmp == null) ? (Comparable<K>) key : new KeyCmp<K>(cmp, (K) key);
    }

    public boolean containsKey(final Object key) {
        return containsImpl(comparable(key));
    }

    private boolean containsImpl(final Comparable<K> key) {
        Node<K,V> parent;
        Node<K,V> node;

        parent = this.rootHolder;         
        node = parent.right;
        
        while (node != null) {
            final int c = key.compareTo(node.key);
            if (c == 0) {
                break;
            }
            parent = node;
            if (c < 0) {
                node = node.left;
            }
            else {
                node = node.right;
            }
        }
        return (node != null);
    }

    public V get(final Object key) {
        return getImpl(comparable(key));
    }

    private V getImpl(final Comparable<K> key) {
        Node<K,V> parent;
        Node<K,V> node;

        parent = this.rootHolder;
        node = parent.right;
        
        while (node != null) {
            final int c = key.compareTo(node.key);
            if (c == 0) {
                break;
            }
            parent = node;
            if (c < 0) {
                node = node.left;
            }
            else {
                node = node.right;
            }
        }
        final V v = (node == null) ? null : node.value;
        return v;
    }

    public V put(final K key, final V value) {
        return putImpl(comparable(key), key, value);
    }

    private V putImpl(final Comparable<K> cmp, final K key, final V value) {
        V prev = null;
        
        final int prio = FastSimpleRandom.nextInt();
        Node<K,V> x = new Node<K,V>(key, value, prio, null, null);
        
        Node<K,V> parent;
        Node<K,V> node;
        Direction dir = Direction.RIGHT;
        // parent.[dir] == node

        parent = this.rootHolder;         
        node = parent.right;

        while (node != null && prio <= node.priority) {
            final int c = cmp.compareTo(node.key);
            if (c == 0) {
                break;
            }
        
            parent = node;
            if (c < 0) {
                node = node.left;               
                dir = Direction.LEFT;
            }
            else {
                node = node.right;               
                dir = Direction.RIGHT;
            }
        }

        Node<K,V> lessParent = null;
        Node<K,V> moreParent = null;
        Direction lessDir;
        Direction moreDir;
        if (node == null) {
            // simple
            parent.setChild(dir, x);
      
        }
        else {
            final int c0 = cmp.compareTo(node.key);
            if (c0 == 0) {
                // TODO: remove this node, then insert later with the new priority (prio must be > node.priority)

                // The update logic results in the post-update priority being
                // the minimum of the existing entry's and x's.  This skews the
                // uniform distribution slightly.
                prev = node.value;
                node.value = value;
            }
            else {
                // TODO: update the existing node if it is a child of the current node

                parent.setChild(dir, x); // add the new node
            

                if (c0 < 0) {
                    x.right = node;
                    moreParent = node;
                    moreDir = Direction.LEFT;
                    lessParent = x;
                    lessDir = Direction.LEFT;
                    node = node.left;
                    
                    moreParent.left = null;
                } else {
                    x.left = node;
                    lessParent = node;
                    lessDir = Direction.RIGHT;
                    moreParent = x;
                    moreDir = Direction.RIGHT;
                    node = node.right;
                   
                    lessParent.right = null;
                }

                while (node != null) {
                    final int c = cmp.compareTo(node.key);
                    if (c == 0) {
                        lessParent.setChild(lessDir, node.left);
                        moreParent.setChild(moreDir, node.right);
                        prev = node.value;
                        break;
                    }
                    else if (c < 0) {
                        moreParent.setChild(moreDir, node);
                   
                        moreParent = node;
                        moreDir = Direction.LEFT;
                        node = moreParent.left;
                       
                        moreParent.left = null;
                    }
                    else {
                        lessParent.setChild(lessDir, node);
                       
                        lessParent = node;
                        lessDir = Direction.RIGHT;
                        node = lessParent.right;
                       
                        lessParent.right = null;
                    }
                }
            }
        }
        return prev;
    }

    public V remove(final Object key) {
        return removeImpl(comparable(key));
    }

    private V removeImpl(final Comparable<K> cmp) {
        V prev = null;

        Node<K,V> parent;
        Node<K,V> node;
        Node<K,V> nL = null;
        Node<K,V> nR = null;
        Direction dir = Direction.RIGHT;

        parent = this.rootHolder;       
        node = parent.right;
        
        while (node != null) {
            final int c = cmp.compareTo(node.key);
            if (c == 0) {
                prev = node.value;
                break;
            }
            parent = node;
            if (c < 0) {
                node = node.left;
                dir = Direction.LEFT;
            }
            else {
                node = node.right;
                dir = Direction.RIGHT;
            }
        }

        while (node != null) {
            if (node.left == null) {
                parent.setChild(dir, node.right);
                break;
            }
            else if (node.right == null) {
                parent.setChild(dir, node.left);
                break;
            }
            else {
                nL = node.left;
                nR = node.right;
            
                if (nL.priority > nR.priority) {
                    node.left = nL.right;
                    parent.setChild(dir, nL);
                    nL.right = node;
             
                    parent = nL;
                    dir = Direction.RIGHT;
                }
                else {
                    node.right = nR.left;
                    parent.setChild(dir, nR);
                    nR.left = node;
               
                    parent = nR;
                    dir = Direction.LEFT;
                }
            }
        }

        // code that prevents treeness violation for an object that happens to
        // be unreachable
        if (node != null) {
            node.left = null;
            node.right = null;
        }
        return prev;
    }

    private void append(final Node<K,V> node, final ArrayList<java.util.Map.Entry<K,V>> buffer) {
        if (node == null) {
            return;
        }
     
        append(node.left, buffer);
        buffer.add(new AbstractMap.SimpleImmutableEntry<K,V>(node.key, node.value));
        final Node<K,V> right = node.right;
        append(right, buffer);
	
    }

    public List<java.util.Map.Entry<K,V>> toList() {	
        final ArrayList<java.util.Map.Entry<K,V>> buffer = new ArrayList<java.util.Map.Entry<K,V>>();
        append(rootHolder.right, buffer);
		return buffer;
    }

    public String toString() {
        return toList().toString();
    }
    
    private boolean validatePriority(Node<K,V> node, int priority){
    	if (node!=null){
    		if(node.priority > priority) return false;
    		return validatePriority(node.left,node.priority) && validatePriority(node.right, node.priority);
    	}
    	return true; 
    }
    
    private boolean validateKey(Node<K,V> node){
    	if (node==null) return true; 
    	Node<K,V> ln = node.left;
 	    Node<K,V> rn = node.right;
 	   if ( ( ln != null && (comparable(ln.key).compareTo(node.key) >= 0) )
 	  	      || ( rn != null && (comparable(rn.key).compareTo(node.key) <= 0) ) )
 	  	    {
 		   		return false; 
 	  	    }
    	return validateKey(ln) && validateKey(rn); 
    }   	
    
	@Override
	public boolean validate() {
		// TODO Auto-generated method stub
		return validatePriority(rootHolder.right,Integer.MAX_VALUE) && validateKey(rootHolder.right);
	}

	@Override
	public HashSet<K> getAllKeys() {
		HashSet<K> keys = new HashSet<K>();
		getAllKeysImpl(rootHolder.right,keys);
		return keys;
	}

	private void getAllKeysImpl(Node<K, V> node, HashSet<K> keys) {
		if(node!=null){
			keys.add(node.key);
			getAllKeysImpl(node.left, keys);
			getAllKeysImpl(node.right, keys);
		}
		
	}
	
	
	public int getMedianPath(){
		int[] counters = new int[100]; 
		doGetMedianPath(rootHolder.right, 0, counters);
		int numLeaves = 0; 
		for(int i=0; i<100; i++){
			numLeaves+= counters[i];
		}
		int median = numLeaves/2; 
		int i =0; 
		while( median > 0){
			median-=counters[i];
			i++;
		}
		return i;
	}
	
	private void doGetMedianPath(Node<K,V> node, int depth, int[] counters){
		if(node == null) return; 
		if(node.left == null && node.right == null){
			counters[depth]++;
		}
		if(node.left!= null){
			doGetMedianPath(node.left, depth+1, counters);
		}
		if(node.right != null){
			doGetMedianPath(node.right, depth+1, counters);
		}
	}
}
