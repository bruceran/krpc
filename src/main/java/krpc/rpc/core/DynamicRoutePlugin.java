package krpc.rpc.core;

import krpc.common.Plugin;

public interface DynamicRoutePlugin extends Plugin {

    int getRefreshIntervalSeconds();

    DynamicRouteConfig getConfig(int serviceId, String serviceName, String group);

}
