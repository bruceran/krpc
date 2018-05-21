package krpc.impl;

import com.google.protobuf.Message;

import krpc.core.MockService;

public class DefaultMockService implements MockService {
	
	public DefaultMockService(String file) {
		
	}
	
	public Message mock(int serviceId,int msgId,Message req) {
		return null;
	}
	
}
