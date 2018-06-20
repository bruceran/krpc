package krpc.rpc.core;

import com.google.protobuf.Message;

public interface FallbackPlugin extends Plugin {
	
	Message fallback(RpcContextData ctx,Message req);
	
}
