package krpc.rpc.cluster;

import java.util.List;

import com.google.protobuf.Message;

import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.Plugin;

public interface LoadBalance extends Plugin {
	
	default boolean needCallStats() {return false;}
	
	// at least 2 addrs to select
	// need to return the index of addrs
	int select(List<Addr> addrs,Weights weights, ClientContextData ctx,Message req); 
}
