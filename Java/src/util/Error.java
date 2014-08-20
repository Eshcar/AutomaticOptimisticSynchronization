package util;

public class Error {
	boolean error = false; 
	public void set(){
		error = true; 
	}
	public boolean isSet(){
		return error; 
	}
	
	public void clean(){
		error = false; 
	}
}
