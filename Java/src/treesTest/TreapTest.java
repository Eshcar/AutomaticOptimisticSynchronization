package treesTest;


import static org.junit.Assert.assertEquals;

import java.util.Random;

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
	
	
	@Test
	public void test2() {
		System.out.println("Starting AutoSimpleTreapTest - TestSequential");
		Treap<Integer,Integer> tree = new Treap<Integer,Integer>();
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
		Treap<Integer,Integer> tree = new Treap<Integer,Integer>();
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
		Treap<Integer,Integer> tree = new Treap<Integer,Integer>();
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
