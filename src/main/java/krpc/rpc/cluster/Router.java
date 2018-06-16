package krpc.rpc.cluster;

import java.util.List;

import com.google.protobuf.Message;

import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;

public interface Router  {
	
	void config(List<RouteRule> rules);
	List<Addr> select(List<Addr> addrs,ClientContextData ctx,Message req); 
}
