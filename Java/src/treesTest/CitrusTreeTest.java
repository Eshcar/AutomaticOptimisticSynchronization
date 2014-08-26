package treesTest;

import org.junit.Test;

import trees.Map;
import trees.citrus.Citrus;

public class CitrusTreeTest {
	
		@Test
		public void testSequential() {
			System.out.println("Starting AutoSimpleBinaryTreeTest - TestSequential");
			Map<Integer,Integer> tree = new Citrus<Integer,Integer>(-1,50);
			CitrusMapTest test = new CitrusMapTest(); 
			test.TestSequential(tree);
		}

		
		@Test
		public void multiTest1() {
			int numThreads = 1;
			int maxKey = 5000; 
			int insertProbability = 5; 
			int removeProbability = 5; 
			int numOps =2000000; 
			Map<Integer,Integer> tree = new Citrus<Integer,Integer>(-1,maxKey);
			
			CitrusMapTest test = new CitrusMapTest(); 
			
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
			Map<Integer,Integer> tree = new Citrus<Integer,Integer>(-1,maxKey);
			
			CitrusMapTest test = new CitrusMapTest(); 
			
			test.runTest(tree,numThreads, maxKey, insertProbability,
					removeProbability, numOps);
		}
		
		@Test
		public void multiTest3() {
			int numThreads = 2;
			int maxKey = 500000; 
			int insertProbability = 50; 
			int removeProbability = 0; 
			int numOps = 200000000; 
			Map<Integer,Integer> tree = new Citrus<Integer,Integer>(-1,maxKey);
			
			CitrusMapTest test = new CitrusMapTest(); 
			
			test.runTest(tree,numThreads, maxKey, insertProbability,
					removeProbability, numOps);
		}

}
