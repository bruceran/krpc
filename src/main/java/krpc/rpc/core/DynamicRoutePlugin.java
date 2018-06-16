package krpc.rpc.core;

public interface DynamicRoutePlugin extends Plugin {
	
	int getRefreshIntervalSeconds();
	
	DynamicRouteConfig getConfig(int serviceId,String serviceName,String group);

}
