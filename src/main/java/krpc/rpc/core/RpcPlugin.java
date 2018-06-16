package krpc.rpc.core;

import com.google.protobuf.Message;

import krpc.rpc.core.Plugin;

public interface RpcPlugin extends Plugin {
	int preCall(RpcContextData ctx,Message req);
	default void postCall(RpcContextData ctx,Message req,Message res) {}
}
