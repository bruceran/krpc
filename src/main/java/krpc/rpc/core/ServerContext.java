package krpc.rpc.core;

import com.google.protobuf.Message;

import krpc.trace.Trace;

public class ServerContext {
	
	static ThreadLocal<ServerContextData> tlData = new ThreadLocal<ServerContextData>();

    public static ServerContextData get() {
        return tlData.get();
    }
	
    public static void set(ServerContextData data) {
    	tlData.set(data);
    	Trace.setCurrentContext(data.getTraceContext());
    }	

    public static void remove() {
        tlData.remove();
    }    
    
    public static RpcClosure newClosure(Message req) {
    	return new RpcClosure(tlData.get(),req);
    }
}
