package trees;

import java.util.HashSet;

public interface Map<K,V> {
	/*
	 * returns true if Key is in the Map
	 */
    //boolean containsKey(K key);
    
    /*
	 * returns the value associated with the key, if Key is in the Map
	 * Otherwise returns null
	 */
    V get(K key);
    
    /*
	 * inserts the key and returns old value if key was already in the Map
	 */
    V put(K key, V value);
    
    /*
	 * returns null if key is not previously in the Map
	 * Otherwise removes the key and returns the old value
	 */
    V remove(K key);

    /*
     * For debugging
     */
	boolean validate();
	public HashSet<K> getAllKeys();
}

