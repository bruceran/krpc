package krpc.rpc.core;

import com.google.protobuf.Message;

import krpc.common.Plugin;

public interface FallbackPlugin extends Plugin {
	
	Message fallback(RpcContextData ctx,Message req);
	
}
