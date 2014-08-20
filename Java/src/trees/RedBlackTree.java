package trees;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RedBlackTree<K,V>{
	
	private static class Node<K,V>  {
        K key;
        V value;    
        int red; /* 1 is red, 0 is black */
        Node<K,V> left;        
        Node<K,V> right;        

        Node(final K key, final int r) {
            this.key = key;
            this.value = null;
            this.red = r;
            this.left = null;            
            this.right = null;            
        }
        
		public Node<K,V> GetChild(final boolean dir)
		{
			  if(!dir) 
				  return left;
			  else
				  return right;
		}

		void SetChild(final boolean dir, final Node<K,V> n)
		{		  
			  if(!dir) 
				  left = n;
			  else
				  right = n;
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
    final Node<K,V> rootHolder = new Node<K,V>(null, 0);

   	public RedBlackTree() {
   		this(null);
   	}

   	public RedBlackTree(final Comparator<K> cmp) {
   		this.cmp = cmp;
   	}
   	
   	private boolean is_red(Node<K,V> r)
   	{
   	  return r != null && (r.red == 1);
   	}	
   	
   	private Node<K,V> singleRotation( Node<K,V> r, boolean dir )
	{
	  Node<K,V> save=null, tmp1=null;
	  
	  save = r.GetChild(!dir); 	  
	  tmp1 = save.GetChild(dir);

	  r.SetChild(!dir, tmp1);
	  save.SetChild(dir, r);

	  r.red = 1;
	  save.red = 0;

	  return save;
	}
	
	private Node<K,V> doubleRotation (Node<K,V> r, boolean dir )
	{
	  Node<K,V> tmp1=null, tmp2=null, tmp3=null;
	  
	  tmp1 =  r.GetChild(!dir);
	  tmp2 =  singleRotation ( tmp1, !dir );
	  r.SetChild(!dir , tmp2);
	  tmp3 = singleRotation ( r, dir );
	  return tmp3;
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
        boolean retVal = false;
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

        retVal = (node != null);
        return retVal;
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
      Node<K,V> t = rootHolder;
	  if ( t.right == null ) {
	    /* Empty tree case */
		Node<K,V> tmp = null;
		tmp = new Node<K,V> ( key, 1 );
	    t.right = tmp;
	  }
	  else {
	    Node<K,V> g;     /* Grandparent & parent */
	    Node<K,V> p, q;     /* Iterator & parent */
	    boolean dir = false, last = false;

	    /* Set up helpers */	    
	    g = p = null;	
	    q = t.right ;

		/* Make root black */ 
		q.red = 0;

	    /* Search down the tree */
	    for ( ; ; ) {
	      if ( q == null ) {
	        /* Insert new node at the bottom */
	        q = new Node<K,V> ( key, 1 );
	        p.SetChild(dir, q) ;
	      }
	      else if ( is_red ( q.left ) && is_red ( q.right ) ) {
	        /* Color flip */
	    	Node<K,V> qL=null, qR=null;
	        qL = q.left;
	        qR = q.right;	        

	        q.red = 1;
	        qL.red = 0;
	        qR.red = 0;
	      }

	      /* Fix red violation */
	      if ( is_red ( q ) && is_red ( p ) ) {
	        boolean dir2 = (t.right == g);

	        if ( q == p.GetChild(last) )
	        {
	          Node<K,V> tmp = null;
	          tmp = singleRotation ( g, !last );
	          t.SetChild(dir2, tmp);
	        }
	        else
	        {
	          Node<K,V> tmp = null;
	          tmp = doubleRotation ( g, !last );
	          t.SetChild(dir2, tmp);
	        }
	      }

	      final int c = cmp.compareTo(q.key);

	      
	      if ( c == 0 )
	      {
	    	prev = q.value;
	    	q.value = value;
	        break; //  Stop if found 
	      }

	      last = dir;
	      dir = (c > 0) ; // q.key < key;

	      /* Update helpers */
	      if ( g != null )
	        t = g;
	      g = p; p = q;
	      q = q.GetChild(dir);
	    }
	  }
	  
	  return prev;
	}	
    
    public V remove(final Object key) {
    	return removeImpl(comparable(key));
    }   
    private V removeImpl(final Comparable<K> cmp) {   
      V prev = null;
        
  	  Node<K,V> q = rootHolder;
	  if ( q.right != null ) {
	    Node<K,V> p, g; /* Helpers */
	    Node<K,V> q_dir = null;
	    Node<K,V> f = null;  /* Found item */
	    boolean dir = true;

	    /* Set up helpers */	    
	    g = null;    
	    p = null;    

	    q_dir = q.GetChild(dir);
		/* Make root black */ 
	    q_dir.red = 0;

	    /* Search and push a red down */
	    while ( q_dir != null ) {
	      boolean last = dir;

	      /* Update helpers */
	      g = p; 
	      p = q;
	      q = q_dir;
	      
	      final int c = cmp.compareTo(q.key);
	      
	      dir = (c > 0); // q.key < key;

	      /* Save found node */
	      if ( c == 0 ) // q.key == key
	        f = q;

	      /* Push the red node down */
	      if ( !is_red ( q ) && !is_red ( q.GetChild(dir) ) ) {
	        if ( is_red ( q.GetChild(!dir) ) )
			{
			  p.SetChild(last, singleRotation ( q, dir ));
	          p = p.GetChild(last) ;
			}
	        else if ( !is_red ( q.GetChild(!dir) ) ) {
	          Node<K,V> s = p.GetChild(!last);

	          if ( s != null ) {
	            if ( !is_red ( s.GetChild(!last) ) && !is_red ( s.GetChild(last) ) ) {
	              /* Color flip */
	              p.red = 0;
	              s.red = 1;
	              q.red = 1;
	            }
	            else {
	              boolean dir2 = (g.right == p);

	              if ( is_red ( s.GetChild(last) ) )
	                g.SetChild(dir2, doubleRotation ( p, last ));
	              else if ( is_red ( s.GetChild(!last) ) )
	                g.SetChild(dir2, singleRotation ( p, last ));

	              {
	            	  /* Ensure correct coloring */
	            	  Node<K,V> tmp=g.GetChild(dir2);	            	   
	            	  tmp.red = 1;
	            	  q.red = 1 ;
	            	  
	            	  Node<K,V> tmp1_L = tmp.left;
	            	  Node<K,V> tmp1_R = tmp.right;
	            	  tmp1_L.red = 0;
	            	  tmp1_R.red = 0;
	              }
	            }
	          }
	        }
	      }
	      q_dir = q.GetChild(dir);
	    }

	    /* Replace and remove if found */
	    if ( f != null ) {	    	
	      f.key = q.key;
	      prev = q.value;

		  boolean d1 = (p.right == q);
		  boolean d2 = (q.left == null);
	      p.SetChild(d1, q.GetChild(d2));	      
	      // free ( q ); TODO: here we q should be freed
	    }
	  }
	  return prev;
	}
    
    private boolean debugSane ;
 // verifies that the tree is indeed a red-black tree
    public boolean IsSane() // not thread safe !! - for debug 
    {    
    	debugSane = true;
    	verifyRBTreeConditions(rootHolder.right);
    	return debugSane; 
    }  
    
    int verifyRBTreeConditions(final Node<K,V> r)
    {
  	  int lh, rh;

	  if ( r == null )
	    return 1;
	  else {
	    Node<K,V> ln = r.left;
	    Node<K,V> rn = r.right;

	    // Consecutive red links  
	    if ( is_red ( r ) ) {
	      if ( is_red ( ln ) || is_red ( rn ) ) {
	    	  System.out.println ( "\n *********** Red violation" );
	    	  debugSane = false;
	        return 0;
	      }
	    }

	    lh = verifyRBTreeConditions ( ln );
	    rh = verifyRBTreeConditions ( rn );

	    // Invalid binary search tree  
	    if ( ( ln != null && (comparable(ln.key).compareTo(r.key) >= 0) )
	      || ( rn != null && (comparable(rn.key).compareTo(r.key) <= 0) ) )
	    {
	    	System.out.println ( "\n *********** Binary tree violation" );
	    	debugSane = false;
	      return 0;
	    }

	    // Black height mismatch 
	    if ( lh != 0 && rh != 0 && lh != rh ) {
	    	System.out.println ( "\n *********** Black violation" );
	    	debugSane = false;
	      return 0;
	    }

	    // Only count black links 
	    if ( lh != 0 && rh != 0 )
	      return is_red ( r ) ? lh : lh + 1;
	    else
	      return 0;
	  }
    }

    private void append(final Node<K,V> node, final ArrayList<Map.Entry<K,V>> buffer) {
        if (node == null) {
            return;
        } 
 		
        append(node.left, buffer);
        buffer.add(new AbstractMap.SimpleImmutableEntry<K,V>(node.key, node.value));
        final Node<K,V> right = node.right;
   
        append(right, buffer);
	
    }

    public List<Map.Entry<K,V>> toList() // not thread safe !! - for debug 
    {        
		
        final ArrayList<Map.Entry<K,V>> buffer = new ArrayList<Map.Entry<K,V>>();
      
        append(rootHolder.right, buffer);		
        
		return buffer;
    }

    public String toString() // not thread safe !! - for debug 
    {
        return toList().toString();
    }

}
