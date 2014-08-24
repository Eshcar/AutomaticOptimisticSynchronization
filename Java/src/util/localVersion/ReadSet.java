package util.localVersion;


import trees.localVersion.Node;

public class ReadSet<K,V>{
	
	private final static int READ_SET_SIZE = 128; 
	
	private Object readSetObjects[];
	private int readSetVersions[]; 
	private int count; 

	public ReadSet(){
		readSetObjects = new Object[READ_SET_SIZE];
		readSetVersions = new int[READ_SET_SIZE];
		count = 0; 
	}
	
	public void clear(){
		count = 0;
	}
	
	public void add(Node<K,V> node, int version){
		readSetObjects[count] = node;
		readSetVersions[count] = version;
		count++;
	}
	
	@SuppressWarnings("unchecked")
	public boolean validate(final Thread self){
		Node<K,V> node; 
		for (int i=0; i<count; i++){
			node = (Node<K, V>) readSetObjects[i];
			if (node.lockedBy()!= self && node.isLocked()){
				return false; 
			}
			if(readSetVersions[i]!= node.getVersion()) return false;
		
		}
		return true;
	}
	
	@SuppressWarnings("unchecked")
	public void incrementLocalVersion(Node<K,V> node ){
		Node<K,V> currNode; 
		for (int i=0; i<count; i++){
			currNode = (Node<K, V>) readSetObjects[i];
			if( currNode == node){
				readSetVersions[i]++;
			}
		}
	}

	public int getCount() {
		return count; 
	}
	
	
}
