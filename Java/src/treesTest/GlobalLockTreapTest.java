package treesTest;

import org.junit.Test;

import trees.Map;
import trees.GlobalLockTreap;

public class GlobalLockTreapTest {
	@Test
	public void test() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		Map<Integer,Integer> tree = new GlobalLockTreap<Integer,Integer>();
		MapTest test = new MapTest(); 
		test.TestSequential(tree);
	}
	
	@Test
	public void multiTest1() {
		int numThreads = 8;
		int maxKey = 50; 
		int insertProbability = 5; 
		int removeProbability = 5; 
		int numOps =20; 
		Map<Integer,Integer> tree = new GlobalLockTreap<Integer,Integer>();
		
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
		int numOps =20; 
		Map<Integer,Integer> tree = new GlobalLockTreap<Integer,Integer>();
		
		MapTest test = new MapTest(); 
		
		test.runTest(tree,numThreads, maxKey, insertProbability,
				removeProbability, numOps);
	}
	
	
	@Test
	public void multiTest3() {
		int numThreads = 8;
		int maxKey = 5000; 
		int insertProbability = 50; 
		int removeProbability = 50; 
		int numOps = 2000000; 
		Map<Integer,Integer> tree = new GlobalLockTreap<Integer,Integer>();
		
		MapTest test = new MapTest(); 
		
		test.runTest(tree,numThreads, maxKey, insertProbability,
				removeProbability, numOps);
	}
}
