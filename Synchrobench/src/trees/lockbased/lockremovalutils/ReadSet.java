package trees.lockbased.lockremovalutils;

public class ReadSet<K,V>{
	
	private final static int READ_SET_SIZE = 256; 
	
	private SpinHeapReentrant readSetObjects[];
	private int readSetVersions[]; 
	private int count; 

	public ReadSet(){
		readSetObjects = new SpinHeapReentrant[READ_SET_SIZE];
		readSetVersions = new int[READ_SET_SIZE];
		count = 0; 
	}
	
	public ReadSet(int size){
		readSetObjects = new SpinHeapReentrant[size];
		readSetVersions = new int[size];
		count = 0; 
	}
	
	public void clear(){
		count = 0;
	}
	
	public void add( SpinHeapReentrant node, int version){
		readSetObjects[count] = node;
		readSetVersions[count] = version;
		count++;
	}
	

	public boolean validate(final Thread self){
		SpinHeapReentrant node; 
		for (int i=0; i<count; i++){
			node =  readSetObjects[i];
			if (node.lockedBy()!= self && node.isLocked()){
				return false; 
			}
			if(readSetVersions[i]!= node.getVersion()) return false;
		
		}
		return true;
	}
	
	public void incrementLocalVersion( SpinHeapReentrant node ){
		SpinHeapReentrant currNode; 
		for (int i=0; i<count; i++){
			currNode = readSetObjects[i];
			if( currNode == node){
				readSetVersions[i]++;
			}
		}
	}

	public int getCount() {
		return count; 
	}
	
	public boolean contains(SpinHeapReentrant node){
		for (int i=0; i<count; i++){
			SpinHeapReentrant currNode = readSetObjects[i];
			if( currNode.equals(node)){
				return true; 
			}
		}
		return false; 
	}
}
