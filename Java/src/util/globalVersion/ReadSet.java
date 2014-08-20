package util.globalVersion;

import trees.globalVersion.Node;

public class ReadSet<K,V> {

private final static int READ_SET_SIZE = 128; 
	
	private Object readSetObjects[];
	private int count; 

	public ReadSet(){
		readSetObjects = new Object[READ_SET_SIZE];
		count = 0; 
	}
	
	public void clear(){
		count = 0;
	}
	
	public void add(Node<K,V> node){
		readSetObjects[count] = node;
		count++;
	}
	
	@SuppressWarnings("unchecked")
	public boolean validate(int startVersion, final Thread self){
		Node<K,V> node; 
		for (int i=0; i<count; i++){
			node = (Node<K, V>) readSetObjects[i];
			int version = node.getVersion();
			if (node.isLocked() && node.lockedBy()!= self){
				return false; 
			}
			if(startVersion <= version ){
				return false;
			}
		}
		return true;
	}
	
}
