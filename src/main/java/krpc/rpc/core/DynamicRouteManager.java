package krpc.rpc.core;

public interface DynamicRouteManager {

    void setDynamicRoutePlugin(DynamicRoutePlugin dynamicRoutePlugin);

    void addConfig(int serviceId, String group, DynamicRouteManagerCallback callback);

}

