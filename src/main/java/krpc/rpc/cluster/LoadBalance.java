package krpc.rpc.cluster;

import com.google.protobuf.Message;
import krpc.common.Plugin;
import krpc.rpc.core.ClientContextData;

import java.util.List;

public interface LoadBalance extends Plugin {

    default boolean needPendings() {
        return false;
    }

    // at least 2 addrs to select
    // need to return the index of addrs
    int select(List<Addr> addrs, Weights weights, ClientContextData ctx, Message req);
}
