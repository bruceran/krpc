package krpc.rpc.cluster.lb;

import com.google.protobuf.Message;
import krpc.rpc.cluster.Addr;
import krpc.rpc.cluster.LoadBalance;
import krpc.rpc.cluster.Weights;
import krpc.rpc.core.ClientContextData;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinWeightLoadBalance implements LoadBalance {

    ConcurrentHashMap<Integer, AtomicInteger> map = new ConcurrentHashMap<>();

    public int select(List<Addr> addrs, Weights wts, ClientContextData ctx, Message req) {

        int serviceId = ctx.getMeta().getServiceId();

        int[] weights = new int[addrs.size()]; // weight may be changed during select
        for (int i = 0; i < weights.length; ++i) {
            weights[i] = wts.getWeight(addrs.get(i).getAddr());
        }

        int max = 0;
        int min = Integer.MAX_VALUE;
        int total = 0;

        for (int i = 0; i < weights.length; ++i) {
            int w = weights[i];
            total += w;
            if (w > max) max = weights[i];
            if (w < min) min = weights[i];
        }

        int index = nextIndex(serviceId);

        if (max == 0 || min == max) {
            return index % weights.length;
        }

        int mod = index % total;
        for (int i = 0; i < max; ++i) {
            for (int j = 0; j < weights.length; ++j) {
                if (mod == 0 && weights[j] > 0) {
                    return j;
                }
                if (weights[j] > 0) {
                    weights[j]--;
                    mod--;
                }
            }
        }

        return 0; // impossible
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