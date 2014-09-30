package trees;

import java.util.HashSet;

public interface RangeMap<K, V> extends Map<K, V>{
	
	public HashSet<K> getRange(K min, K max);

}
