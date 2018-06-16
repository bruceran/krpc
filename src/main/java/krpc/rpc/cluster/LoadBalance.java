package krpc.rpc.cluster;

import java.util.List;

import com.google.protobuf.Message;

import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.Plugin;

public interface LoadBalance extends Plugin {
	
	default boolean needCallStats() {return false;}
	
	// addrs have at least 2 items to select
	// need to return the index of addrs
	int select(List<Addr> addrs,ClientContextData ctx,Message req); 
}
