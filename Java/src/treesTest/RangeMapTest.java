package treesTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import trees.Map;
import trees.RangeMap;

public class RangeMapTest {

	public void TestSequentialRange( RangeMap<Integer,Integer> tree, int maxKey, int minRange, int maxRange){
		System.out.println("Starting TestSmallSequentialRange");
		int numElements = maxKey/2; 
		int rangeResult; 
		int[] map = new int[maxKey];
		Random rand = new Random(); 
		int k;
		for (int i=1; i<numElements ; i++){
			k = rand.nextInt(maxKey);
			System.out.print(k+", ");
			if(map[k]!=0){
				Integer oldValue = map[k];
				assertEquals(tree.put(k, i),oldValue); 
				map[k]= i;
			} else{
				assertEquals(tree.put(k, i),null);
				map[k]= i;
			}
		}
		System.out.println("Put finished");
		assertTrue("Validation after inserts failed",tree.validate());
		
		for(int j=1; j<10; j++){
			int rangeSize = rand.nextInt(1+maxRange-minRange)+minRange;
			System.out.println("range size is " + rangeSize);
			int min = rand.nextInt(maxKey-rangeSize); 
			int max = min + rangeSize; 
			System.out.println("Getting range from "+min+ " to "+ max);
			rangeResult = tree.getRange(min, max);
			int count=0; 
			for(int i=min; i<=max; i++){
				if(map[i]!=0){
					count++;
				}
			}
			assertEquals(count,rangeResult);
		}
	} 
	
	public void TestMultiRange( RangeMap<Integer,Integer> tree, int numThreads, int maxKey,
			int rangeProbibility , int insertProbability, int removeProbability, int numOps,
			int minRange, int maxRange){
		Random rand = new Random();
		ReadWriteKeySum[] threads = new ReadWriteKeySum[numThreads]; 
		int k;
		int count = 0;
		int num_elements = maxKey/2;
	
		while(count < num_elements){		
			k = rand.nextInt(maxKey);
			if(tree.put(k, k)==null){
				count++; 
			}	
		}
		
		
		for(int i=0; i<numThreads; i++){
			threads[i] = new ReadWriteKeySum(tree,numOps,rangeProbibility,insertProbability,
					removeProbability,maxKey,minRange,maxRange);
			threads[i].start();
		}
		for(int i=0; i<numThreads; i++){
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		for(int i=0; i<numThreads; i++){
			threads[i] = new ReadWriteKeySum(tree,numOps,rangeProbibility,insertProbability,
					removeProbability,maxKey,minRange,maxRange);
			threads[i].start();
		}
		for(int i=0; i<numThreads; i++){
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertTrue("Validation failed",tree.validate());
		
	}
	
	private class ReadWriteKeySum extends Thread{
		RangeMap<Integer,Integer> tree;
		int count; 
		int insert;
		int remove; 
		int maxKey;
		int range;
		int minRange;
		int maxRange; 
		
		public ReadWriteKeySum(RangeMap<Integer,Integer> tree , int count,int range, 
				int insert , int remove, int maxKey, int minRange, int maxRange){
			this.tree = tree; 
			this.count =count;
			this.range = range;
			this.insert = insert;
			this.remove = remove; 
			this.maxKey= maxKey;
			this.minRange = minRange;
			this.maxRange = maxRange; 
		}
		
		@Override
		public void run() {
			Random rand = new Random(); 
			int k;
			int op;
			
			for (int i=0; i<count ; i++){
				k = rand.nextInt(maxKey); //TODO if minKey!=-1??
				op = rand.nextInt(100); 
				if(op <= range){
					int rangeSize = rand.nextInt(1+maxRange-minRange)+minRange;
					int min = rand.nextInt(maxKey-rangeSize); 
					int max = min + rangeSize; 
					tree.getRange(min, max); 
					for(int j =0; j< rangeSize/2; j++){
						k = rand.nextInt(maxKey); 
						op=rand.nextInt(100);
						if(op<50){
							tree.put(k, 0);
						}else{
							tree.remove(k);
						}
					}
				}
				else if(op <= range + insert) { 
					tree.put(k, 0);
				}
				else if(op <= range+insert+remove){
					tree.remove(k);
				} else{
					tree.get(k);
				}
			}
		}
		
		
	}
	
}
