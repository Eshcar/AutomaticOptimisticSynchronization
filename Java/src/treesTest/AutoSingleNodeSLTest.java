package treesTest;

import org.junit.Test;

import trees.Map;
import trees.RangeMap;
import trees.skipList.AutoSingleNodeSL;
public class AutoSingleNodeSLTest {
	@Test
	public void test() {
		System.out.println("Starting BinaryTreeTest - TestSequential");
		Map<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(50,Integer.MIN_VALUE,Integer.MAX_VALUE);
		MapTest test = new MapTest(); 
		test.TestSequential(tree);
	}
	
	@Test
	public void multiTest1() {
		int numThreads = 1;
		int maxKey = 5000; 
		int insertProbability = 5; 
		int removeProbability = 5; 
		int numOps =2000000; 
		Map<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		MapTest test = new MapTest(); 
		
		test.runTest(tree, numThreads, maxKey, insertProbability,
				removeProbability, numOps);
	}
	
	@Test
	public void multiTest2() {
		int numThreads = 8;
		int maxKey = 50; 
		int insertProbability = 10; 
		int removeProbability = 10; 
		int numOps =100; 
		Map<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		MapTest test = new MapTest(); 
		
		test.runTest(tree,numThreads, maxKey, insertProbability,
				removeProbability, numOps);
	}
	
	@Test
	public void multiTest3() {
		int numThreads = 8;
		int maxKey = 50000; 
		int insertProbability = 50; 
		int removeProbability = 50; 
		int numOps = 2000000; 
		Map<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		MapTest test = new MapTest(); 
		
		test.runTest(tree,numThreads, maxKey, insertProbability,
				removeProbability, numOps);
	}
	
	@Test
	public void smallSequentialRangeTest(){
		int maxKey = 20; 
		int maxRange = 5;  
		int minRange = 2;
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest();
		test.TestSequentialRange(tree, maxKey, minRange, maxRange);
		
	}
	
	@Test
	public void mediumSequentialRangeTest(){
		int maxKey = 120; 
		int maxRange = 10;  
		int minRange = 5;
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest();
		test.TestSequentialRange(tree, maxKey, minRange, maxRange);
		
	}
	
	@Test
	public void largeSequentialRangeTest(){
		int maxKey = 1200; 
		int maxRange = 20;  
		int minRange = 10;
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest();
		test.TestSequentialRange(tree, maxKey, minRange, maxRange);
		
	}
	
	
	@Test
	public void veryLargeSequentialRangeTest(){
		int maxKey = 10000; 
		int maxRange = 500;  
		int minRange = 300;
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest();
		test.TestSequentialRange(tree, maxKey, minRange, maxRange);
		
	}
	
	@Test
	public void smallMultiRangeTest() {
		int numThreads = 4;
		int maxKey = 10000; 
		int rangeProbability = 20;
		int insertProbability = 10; 
		int removeProbability = 10; 
		int numOps = 200000; 
		int maxRange = 200;  
		int minRange = 100;
		
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest(); 
		
		test.TestMultiRange(tree, numThreads, maxKey, rangeProbability, insertProbability, removeProbability, numOps, minRange, maxRange);
	}
	
	@Test
	public void mediumMultiRangeTest() {
		int numThreads = 4;
		int maxKey = 100000; 
		int rangeProbability = 20;
		int insertProbability = 10; 
		int removeProbability = 10; 
		int numOps = 200000; 
		int maxRange = 600;  
		int minRange = 400;
		
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest(); 
		
		test.TestMultiRange(tree, numThreads, maxKey, rangeProbability, insertProbability, removeProbability, numOps, minRange, maxRange);
	}
	
	@Test
	public void largeMultiRangeTest() {
		int numThreads = 4;
		int maxKey = 1000000; 
		int rangeProbability = 20;
		int insertProbability = 10; 
		int removeProbability = 10; 
		int numOps = 200000; 
		int maxRange = 1600;  
		int minRange = 1000;
		
		RangeMap<Integer,Integer> tree = new AutoSingleNodeSL<Integer,Integer>(maxKey,-1,maxKey);
		
		RangeMapTest test = new RangeMapTest(); 
		
		test.TestMultiRange(tree, numThreads, maxKey, rangeProbability, insertProbability, removeProbability, numOps, minRange, maxRange);
	}
	
}
