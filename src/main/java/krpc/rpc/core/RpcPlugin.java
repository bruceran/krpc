package krpc.rpc.core;

import com.google.protobuf.Message;
import krpc.common.Plugin;

public interface RpcPlugin extends Plugin {
    int preCall(RpcContextData ctx, Message req);

    default void postCall(RpcContextData ctx, Message req, Message res) {
    }
}
