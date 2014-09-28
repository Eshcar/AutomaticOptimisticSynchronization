package treesTest;

import org.junit.Test;
import trees.Map;
import trees.skipList.DominationLockingSkipList;


public class DominationLockingSkipListTest {
	@Test
	public void test() {
		System.out.println("Starting BinaryTreeTest - TestSequential");
		Map<Integer,Integer> tree = new DominationLockingSkipList<Integer,Integer>(50,Integer.MIN_VALUE,Integer.MAX_VALUE);
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
		Map<Integer,Integer> tree = new DominationLockingSkipList<Integer,Integer>(maxKey,-1,maxKey);
		
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
		Map<Integer,Integer> tree = new DominationLockingSkipList<Integer,Integer>(maxKey,-1,maxKey);
		
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
		Map<Integer,Integer> tree = new DominationLockingSkipList<Integer,Integer>(maxKey,-1,maxKey);
		
		MapTest test = new MapTest(); 
		
		test.runTest(tree,numThreads, maxKey, insertProbability,
				removeProbability, numOps);
	}
}
