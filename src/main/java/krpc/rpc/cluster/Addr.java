package krpc.rpc.cluster;

public interface Addr {
	
	public static int MAX_SECONDS_ALLOWED = 15;
	
	String getAddr(); // return ip:port
	long getAvgTimeUsed(int secondsBefore); 
    
}
