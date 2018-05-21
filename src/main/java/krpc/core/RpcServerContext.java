package krpc.core;

import com.google.protobuf.Message;

public class RpcServerContext {
	
	static ThreadLocal<RpcServerContextData> tlData = new ThreadLocal<RpcServerContextData>();

    public static RpcServerContextData get() {
        return tlData.get();
    }
	
    public static void set(RpcServerContextData data) {
    	tlData.set(data);
    }	

    public static void remove() {
        tlData.remove();
    }    
    
    public static RpcClosure newClosure(Message req) {
    	return new RpcClosure(tlData.get(),req);
    }
}
