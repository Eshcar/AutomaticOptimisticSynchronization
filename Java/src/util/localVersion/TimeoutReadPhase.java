package util.localVersion;


import trees.localVersion.Node;
import util.Error;


public class TimeoutReadPhase <K,V>{

	public Node<K,V> readRef(Node<K,V> newNode,ReadSet<K,V> readSet, Error err) {
	
		if(newNode!=null){
			int version = newNode.getVersion();
			if(newNode.isLocked()){
				err.set();
				return null;
			}
			
			readSet.add(newNode, version);
		}
		
		return newNode;
	}

}