package krpc.rpc.monitor;

import krpc.common.Plugin;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.web.WebClosure;

public interface MonitorPlugin extends Plugin {
    default void rpcReqDone(RpcClosure closure) {
    } // req may be null

    default void rpcCallDone(RpcClosure closure) {
    }

    default void webReqDone(WebClosure closure) {
    }
}
