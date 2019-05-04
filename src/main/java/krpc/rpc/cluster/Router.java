package krpc.rpc.cluster;

import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;

import java.util.List;
import java.util.Map;

public interface Router {

    void config(List<RouteRule> rules);

    default boolean needReqInfo(int serviceId,int msgId) {
        return false;
    }

    List<Addr> select(List<Addr> addrs, ClientContextData ctx, Map<String,Object> req);
}
