package util;

import trees.localVersion.Node;

public abstract class ReadWritePhaseStrategy<K,V> {
	public abstract Node<K,V> assign(final Node<K,V> prevValue, final Node<K,V> newValue, final Thread self);
	public abstract Node<K,V> acquire(final Node<K,V> node, final Thread self);
	public abstract void release(final Node<K,V> node); 
}
