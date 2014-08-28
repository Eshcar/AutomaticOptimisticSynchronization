package treesTest;

import java.util.Random;

import org.junit.Test;

import trees.Map;
import trees.RedBlackTree;
import trees.Treap;

public class RedBlackTreeTest {
	
	
	@Test
	public void test() {
		System.out.println("Starting BinaryTreeTest - TestSequential");
		Map<Integer,Integer> tree = new RedBlackTree<Integer,Integer>();
		MapTest test = new MapTest(); 
		test.TestSequential(tree);
	}
	
	
	@Test
	public void test2() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		RedBlackTree<Integer,Integer> tree = new RedBlackTree<Integer,Integer>();
		Random rand = new Random(); 
		int k;
		int count = 0;
		while(count < 10000){
			k = rand.nextInt(20000);
			if(tree.put(k, k)==null){
				count++; 
			}	
		}
		System.out.println("Done");
		System.out.println("median is: " + tree.getMedianPath());
	}
	
	@Test
	public void test3() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		RedBlackTree<Integer,Integer> tree = new RedBlackTree<Integer,Integer>();
		Random rand = new Random(); 
		int k;
		int count = 0;
		while(count < 100000){
			k = rand.nextInt(200000);
			if(tree.put(k, k)==null){
				count++; 
			}	
		}
		System.out.println("Done");
		System.out.println("median is: " + tree.getMedianPath());
	}
	
	@Test
	public void test4() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		RedBlackTree<Integer,Integer> tree = new RedBlackTree<Integer,Integer>();
		Random rand = new Random(); 
		int k;
		int count = 0;
		while(count < 1000000){
			k = rand.nextInt(2000000);
			if(tree.put(k, k)==null){
				count++; 
			}	
		}
		System.out.println("Done");
		System.out.println("median is: " + tree.getMedianPath());
	}
}
