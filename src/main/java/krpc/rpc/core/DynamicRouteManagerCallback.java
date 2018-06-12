package krpc.rpc.core;

import java.util.List;

import krpc.rpc.core.DynamicRoute.AddrWeight;
import krpc.rpc.core.DynamicRoute.RouteRule;

public interface DynamicRouteManagerCallback {
	void configChanged(int serviceId,List<AddrWeight> weight,List<RouteRule> rules);
}
