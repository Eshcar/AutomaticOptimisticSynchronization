package trees.localVersion;

import util.localVersion.SpinHeapReentrant;

public class Node<K, V> extends SpinHeapReentrant {
	/**
	 * Default 
	 */
	private static final long serialVersionUID = 1L;

	enum Direction {
        LEFT, RIGHT
    }

	
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

	void setChild(final Direction dir, final Node<K,V> n, final Thread self) {
        if (n != null)
            n.addIncoming(self);
        if (dir == Direction.LEFT) {
            if (left != null)
                left.removeIncoming(self);
            left = n;
        } else {
            if (right != null)
                right.removeIncoming(self);
            right = n;
        }
    }
}