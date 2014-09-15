package trees.skipList;

import java.util.Random;

public class SkipListRandom {
	int seed; 
	
	public SkipListRandom(){
		seed = new Random(System.currentTimeMillis()).nextInt()
                | 0x0100;
	}
	
	int randomHeight(int maxHeight) {
        int x = seed;
        x ^= x << 13;
        x ^= x >>> 17;
        seed = (x ^= x << 5);
        if ((x & 0x8001) != 0) {
            return 1;
        }
        int level = 1;
        while (((x >>>= 1) & 1) != 0) {
            ++level;
        }
        return Math.min(level + 1, maxHeight);
    }
}
