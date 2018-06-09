package krpc.rpc.core;

import com.google.protobuf.Message;

public interface FlowControl extends Plugin {

	void addLimit(int serviceId,int seconds,int limit); // service level limit
	void addLimit(int serviceId,int msgId,int seconds,int limit); // msg level limit
	
	boolean isAsync();

    boolean exceedLimit(RpcContextData ctx,Message req, Continue<Boolean> cont);
}
