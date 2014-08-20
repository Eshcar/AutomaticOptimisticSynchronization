package util.globalVersion;

import java.util.concurrent.atomic.AtomicInteger;

public class GlobalVersion {
	
	private static volatile AtomicInteger version = new AtomicInteger(0);
	
	public static int getVersion(){
		return version.get();
	}
	
	public static boolean tryIncrementVersion(int oldVersion , int newVersion){
		//TODO - might want to use test and test and set version. 
		return version.compareAndSet(oldVersion, newVersion);
	}
}


