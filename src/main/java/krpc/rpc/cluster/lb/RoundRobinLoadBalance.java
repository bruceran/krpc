package krpc.rpc.cluster.lb;

import com.google.protobuf.Message;
import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.cluster.Weights;
import krpc.rpc.core.ClientContextData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalance implements LoadBalance {

    ConcurrentHashMap<Integer, AtomicInteger> map = new ConcurrentHashMap<>();

    public int select(List<Addr> addrs, Weights weights, ClientContextData ctx, Map<String,Object> req) {

        int serviceId = ctx.getMeta().getServiceId();

        int index = nextIndex(serviceId);
        return index % addrs.size();
    }

    private int nextIndex(int serviceId) {
        AtomicInteger ai = map.get(serviceId);
        if (ai == null) {
            ai = new AtomicInteger(-1);
            AtomicInteger old = map.putIfAbsent(serviceId, ai);
            if (old != null) ai = old;
        }

        int index = ai.incrementAndGet();
        if (index >= 10000000) {
            ai.compareAndSet(index, 0);
        }
        return index;
    }

}