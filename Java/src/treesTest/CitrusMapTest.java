package treesTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import trees.Map;
import trees.citrus.RCU;


public class CitrusMapTest {

	 public void TestSequential( Map<Integer,Integer> tree){
		System.out.println("Starting TestSequential");
		RCU.initRCU(1); 
		RCU.register(0);
		HashMap<Integer,Integer> map = new HashMap<Integer,Integer>(); 
		Random rand = new Random(); 
		int k;
		for (int i=0; i<20 ; i++){
			k = rand.nextInt(50);
			System.out.print(k+", ");
			if(map.containsKey(k)){
				Integer oldValue = map.get(k);
				assertEquals(tree.put(k, i),oldValue); 
				map.put(k, i);
			} else{
				assertEquals(tree.put(k, i),null);
				map.put(k, i);
			}
		}
		System.out.println("Put finished");
		assertTrue("Validation after inserts failed",tree.validate());
		for (int i=0; i<20 ; i++){
			k = rand.nextInt(50);
			if(!map.containsKey(k)){
				assertEquals(tree.get(k),null);
			}
		}
		for (Integer key : map.keySet() ){
			assertEquals(tree.get(key),map.get(key)); 
		}
		System.out.println("Contains finished");
		for (int i=0; i<20 ; i++){
			k = rand.nextInt(50);
			System.out.print(k+", ");
			if(map.containsKey(k)){
				Integer oldValue = map.get(k);
				assertEquals(tree.remove(k),oldValue); 
				map.remove(k);
			} else {
				assertEquals(tree.remove(k),null);
			}
		}
		System.out.println("Remove finished");
		assertTrue("Validation after removes failed",tree.validate());
		RCU.unregister();
	}

	
	void runTest(Map<Integer,Integer> tree, int numThreads, int maxKey,
			int insertProbability, int removeProbability, int numOps) {
		
		System.out.println("Starting TestMulti with:" + numThreads + " threads");
		RCU.initRCU(numThreads); 
		ReadWriteKeySum[] threads = new ReadWriteKeySum[numThreads]; 
		int sumOperations = 0; 
		for(int i=0; i<numThreads; i++){
			threads[i] = new ReadWriteKeySum(i,tree,numOps,insertProbability,removeProbability,maxKey);
			threads[i].start();
		}
		for(int i=0; i<numThreads; i++){
			try {
				threads[i].join();
				sumOperations += threads[i].getRes(); 
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertTrue("Validation failed",tree.validate());
		int afterOperationsSum = 0;
		HashSet<Integer> allkeys = tree.getAllKeys();
		for(Integer key: allkeys){
			afterOperationsSum+=key; 
		}
		assertEquals(afterOperationsSum,sumOperations);
	}
	
	private class ReadWriteKeySum extends Thread{
		Map<Integer,Integer> tree;
		int count; 
		int insert;
		int remove; 
		int res = 0; 
		int maxKey;
		private int id; 
		
		public ReadWriteKeySum(int id, Map<Integer,Integer> tree , int count, int insert , int remove, int maxKey){
			this.tree = tree; 
			this.count =count;
			this.insert = insert;
			this.remove = remove; 
			this.maxKey= maxKey;
			this.id = id;
		}
		
		@Override
		public void run() {
			RCU.register(id);
			Random rand = new Random(); 
			int k;
			int op;
			int res = 0; 
			for (int i=0; i<count ; i++){
				k = rand.nextInt(maxKey); //TODO if minKey!=-1??
				op = rand.nextInt(100); 
				if(op <= insert) { 
					if(tree.put(k, 0)==null){
						res+=k; 
					};
				}
				else if(op <= insert+remove){
					if(tree.remove(k)!=null){
						res-=k;
					};
				} else{
					tree.get(k);
				}
			}
			this.res = res; 
			RCU.unregister();
		}
		
		int getRes(){
			return res; 
		}
	}
	
	
}
