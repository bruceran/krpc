package krpc.rpc.core;

import com.google.protobuf.Message;

public interface MockService {
	
	Message mock(int serviceId,int msgId,Message req);
	
}
