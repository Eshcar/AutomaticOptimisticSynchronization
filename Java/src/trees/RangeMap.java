package trees;

import java.util.HashSet;

public interface RangeMap<K, V> extends Map<K, V>{
	
	public int getRange(K min, K max);

}
