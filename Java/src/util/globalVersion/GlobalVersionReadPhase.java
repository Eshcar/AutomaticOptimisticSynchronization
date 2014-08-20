package util.globalVersion;

import trees.globalVersion.Node;
import util.Error;

public class GlobalVersionReadPhase<K, V>{

	public Node<K, V> readRef(Node<K, V> newNode, Node<K, V> parentNode,
			ReadSet<K, V> readSet, Error err, int startVersion) {
		
		assert(parentNode!=null);
		int version = parentNode.getVersion();
		if(parentNode.isLocked()){
			err.set();
			return null;
		} 
		if(version> startVersion){
			err.set();
			return null;
		}
		if(newNode!=null){
			readSet.add(newNode);
		}
		return newNode;
	}


	public Node<K, V> readGlobalRef(Node<K, V> newNode,
			ReadSet<K, V> readSet, Error err, int startVersion) {
		if(newNode!=null){
			readSet.add(newNode);
		}
		return newNode;
	}

	public void readValFromNode(Node<K, V> node,
			ReadSet<K, V> readSet, Error err, int startVersion) {
		assert(node!=null);
		int version = node.getVersion();
		if(node.isLocked()){
			err.set(); 
		}
		if(version > startVersion) {
			err.set(); 
		}
		
	}

}
