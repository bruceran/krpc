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

    public static RpcClosure closure(Message req) {
        return new RpcClosure(tlData.get(), req, false);
    }

    public static void logVar(String key,Object value) {
        ServerContextData data = tlData.get();
        if( data == null ) return;
        data.setAttribute("var:"+key,value);
    }

}
