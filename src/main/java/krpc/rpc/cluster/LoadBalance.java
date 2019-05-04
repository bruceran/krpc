package krpc.rpc.cluster;

import krpc.common.Plugin;
import krpc.rpc.core.ClientContextData;

import java.util.List;
import java.util.Map;

public interface LoadBalance extends Plugin {

    default boolean needPendings() {
        return false;
    }

    default boolean needReqInfo(int serviceId,int msgId) {
        return false;
    }

    // at least 2 addrs to select
    // need to return the index of addrs
    int select(List<Addr> addrs, Weights weights, ClientContextData ctx, Map<String,Object> req);
}
