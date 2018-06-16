package krpc.rpc.cluster;

public interface Addr {
	
	public static int MAX_SECONDS_ALLOWED = 15;
	
	String getAddr(); // return ip:port
	int getWeight(int serviceId); // used for weight policy
	int getPendingCalls();  // used for least active policy
	long getAvgTimeUsedMicros(int secondsBefore);  // used for response time policy
    
}
