package krpc.rpc.core;

public interface DynamicRouteManager {

    void addConfig(int serviceId,String group,DynamicRouteManagerCallback callback);

}

