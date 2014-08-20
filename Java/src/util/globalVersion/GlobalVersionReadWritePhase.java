package util.globalVersion;

import trees.globalVersion.Node;

public class GlobalVersionReadWritePhase<K,V> {


	public Node<K, V> assign(Node<K, V> prevValue, Node<K, V> newValue,
			Thread self) {
		acquire(newValue, self);
        release(prevValue);
        return newValue;
	}


	public Node<K, V> acquire(Node<K, V> node, Thread self) {
		if (node != null) {
            node.acquire(self);
        }
        return node;
	}


	public void release(Node<K, V> node) {
		if (node != null){
	           node.release();
		}
	}

}
