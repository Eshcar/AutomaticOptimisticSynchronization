package util.globalVersion;

import trees.globalVersion.Node;

public class GlobalVersionReadWritePhase<K,V> {


	public Node<K, V> assign(Node<K, V> prevValue, Node<K, V> newValue,
			Thread self, int newVersion) {
		acquire(newValue, self, newVersion);
        release(prevValue);
        return newValue;
	}


	public Node<K, V> acquire(Node<K, V> node, Thread self, int newVersion) {
		if (node != null) {
            node.acquire(self, newVersion);
        }
        return node;
	}


	public void release(Node<K, V> node) {
		if (node != null){
	           node.release();
		}
	}

}
