package util.localVersion;

import trees.localVersion.Node;
import util.ReadWritePhaseStrategy;

public class LocalVersionReadWritePhase<K,V> extends ReadWritePhaseStrategy<K,V>{

	@Override
	public Node<K,V> assign(Node<K,V> prevValue,
			Node<K,V> newValue, Thread self) {
		acquire(newValue, self);
        release(prevValue);
        return newValue;
	}

	@Override
	public Node<K,V> acquire(Node<K,V> node, Thread self) {
		if (node != null) {
            node.acquire(self);
        }
        return node;
	}

	@Override
	public void release(Node<K,V> node) {
		if (node != null){
	           node.release();
		}
	}

}
