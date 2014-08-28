package treesTest;



import org.junit.Test;

import trees.Map;
import trees.Treap;

public class TreapTest {

	@Test
	public void test() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		Map<Integer,Integer> tree = new Treap<Integer,Integer>();
		MapTest test = new MapTest(); 
		test.TestSequential(tree);
	}
	
	@Test
	public void test1() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		Treap<Integer,Integer> tree = new Treap<Integer,Integer>();
		for(int i=0; i<30 ;i++){
			tree.put(i, i);
		}
		System.out.println("Done");
	}

}
