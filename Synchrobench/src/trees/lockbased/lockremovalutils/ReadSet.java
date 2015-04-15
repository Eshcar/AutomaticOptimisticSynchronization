package trees.lockbased.lockremovalutils;

import java.util.HashMap;
import java.util.Map.Entry;



public class ReadSet<K,V>{
	
	private final static int READ_SET_SIZE = 256; 
	private final int actualSize;
	private SpinHeapReentrant readSetObjects[];
	private int readSetVersions[]; 
	private int count; 
	private HashMap<SpinHeapReentrant,Integer> set = new HashMap<SpinHeapReentrant,Integer>(); 

	public ReadSet(){
		readSetObjects = new SpinHeapReentrant[READ_SET_SIZE];
		readSetVersions = new int[READ_SET_SIZE];
		actualSize = READ_SET_SIZE;
		count = 0; 
	}
	
	public ReadSet(int size){
		readSetObjects = new SpinHeapReentrant[size];
		readSetVersions = new int[size];
		actualSize = size;
		count = 0; 
	}
	
	public void clear(){
		count = 0;
		if (!set.isEmpty()){
			set.clear();
		}
	}
	
	public void add( SpinHeapReentrant node, int version){
		if (count < actualSize){
			readSetObjects[count] = node;
			readSetVersions[count] = version;
			count++;
		}else{
			set.putIfAbsent(node, version); 
		}
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
		if(!set.isEmpty()){
			for(Entry<SpinHeapReentrant, Integer> entry : set.entrySet()) {
				node = entry.getKey();
			    int version = entry.getValue();
			    if (node.lockedBy()!= self && node.isLocked()){
					return false; 
				}
				if(version!= node.getVersion()) return false;
			}
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
		if(!set.isEmpty()){
			for(Entry<SpinHeapReentrant, Integer> entry : set.entrySet()) {
				node = entry.getKey();
			    int version = entry.getValue();
			    version++;
			    set.put(node, version);
			}
		}
	}

	public int getCount() {
		if(set.isEmpty()){
			return count; 
		}else{
			return count + set.size();
		}
	}
	
	public boolean contains(SpinHeapReentrant node){
		for (int i=0; i<count; i++){
			SpinHeapReentrant currNode = readSetObjects[i];
			if( currNode.equals(node)){
				return true; 
			}
		}
		if(set.containsKey(node)){
			return true; 
		}
		return false; 
	}
}
