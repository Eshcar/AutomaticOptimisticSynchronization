package treesTest;

import org.junit.Test;

import trees.BinaryTree;
import trees.Map;

public class BinaryTreeTest {

	@Test
	public void test() {
		System.out.println("Starting BinaryTreeTest - TestSequential");
		Map<Integer,Integer> tree = new BinaryTree<Integer,Integer>(-1,50);
		MapTest test = new MapTest(); 
		test.TestSequential(tree);
	}

}
