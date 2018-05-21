package krpc.cluster;

import com.google.protobuf.Message;

import krpc.core.Plugin;

public interface LoadBalance extends Plugin {
	
	default boolean needCallStats() {return false;}
	
	// addrs have at least 2 items to select
	// need to return the index of addrs
	int select(Addr[] addrs,int serviceId,int msgId,Message req); 
}
