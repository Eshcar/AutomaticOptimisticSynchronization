package treesTest;

import org.junit.Test;

import trees.Map;
import trees.skipList.SkipList;


public class SkipListTest {

	@Test
	public void test() {
		System.out.println("Starting BinaryTreeTest - TestSequential");
		Map<Integer,Integer> tree = new SkipList<Integer,Integer>(50);
		MapTest test = new MapTest(); 
		test.TestSequential(tree);
	}
}
