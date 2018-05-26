package krpc.rpc.core;

public interface FlowControl extends Plugin {

	void addLimit(int serviceId,int seconds,int limit); // service level limit
	void addLimit(int serviceId,int msgId,int seconds,int limit); // msg level limit
	
	boolean isAsync();

    boolean exceedLimit(int serviceId,int msgId,Continue<Boolean> cont);
}
