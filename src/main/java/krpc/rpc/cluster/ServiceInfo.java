package krpc.rpc.cluster;

import com.google.protobuf.Message;
import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.DynamicRouteConfig.AddrWeight;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceInfo {

    private int serviceId;

    private LoadBalance lb;
    private Router router;
    private BreakerInfo bi;

    private HashSet<String> all = new HashSet<String>();
    private List<AddrInfo> alive = new ArrayList<AddrInfo>();

    private AtomicBoolean disabled = new AtomicBoolean(false);
    private AtomicReference<Weights> weights = new AtomicReference<>();

    ServiceInfo(int serviceId, LoadBalance lb, Router router, BreakerInfo bi) {
        this.serviceId = serviceId;
        this.lb = lb;
        this.router = router;
        this.bi = bi;
        weights.set(new Weights());
    }

    public int getServiceId() {
        return serviceId;
    }

    public BreakerInfo getBreakerInfo() {
        return bi;
    }

    public LoadBalance getLoadBalance() {
        return lb;
    }

    public boolean isDisabled() {
        return disabled.get();
    }

    public void setDisabled(boolean disabled) {
        this.disabled.set(disabled);
    }

    public void configRules(List<RouteRule> rules) {
        router.config(rules);
    }

    public void configWeights(List<AddrWeight> list) {
        Weights w = new Weights();

        for (AddrWeight aw : list) {
            w.addWeight(aw.getAddr(), aw.getWeight());
        }

        weights.set(w);
    }

    synchronized AddrInfo nextAddr(ClientContextData ctx, Message req) {

        if (alive.size() == 0)
            return null;

        Set<String> excludeAddrs = ctx.getExcludeAddrs();

        List<Addr> candidates = new ArrayList<>();
        for (AddrInfo ci : alive) {
            if (excludeAddrs != null && excludeAddrs.contains(ci.getAddr()))
                continue;

            if (bi.isEnabled()) {
                if (!ci.selectable(serviceId)) {
                    continue;
                }
            }

            candidates.add(ci);
        }

        if (candidates.size() == 0)
            return null;

        candidates = router.select(candidates, ctx, req);

        if (candidates.size() == 0)
            return null;

        if (candidates.size() == 1)
            return (AddrInfo) candidates.get(0);


        int idx = lb.select(candidates, weights.get(), ctx, req);
        return (AddrInfo) candidates.get(idx);
    }

    String getAddr(String connId) {
        int p = connId.lastIndexOf(":");
        return connId.substring(0, p);
    }

    synchronized void copyTo(Set<String> newSet) {
        newSet.addAll(all);
    }

    synchronized HashSet<String> mergeFrom(Set<String> newSet) {
        HashSet<String> toBeAdded = new HashSet<String>();
        toBeAdded.addAll(newSet);
        toBeAdded.removeAll(all);
        all.addAll(toBeAdded);
        return toBeAdded;
    }

    synchronized int foundAlive(String addr) {
        int size = alive.size();
        for (int i = 0; i < size; ++i) {
            if (alive.get(i).getAddr().equals(addr)) {
                return i;
            }
        }
        return -1;
    }

    synchronized void remove(AddrInfo ai) {
        int idx = foundAlive(ai.getAddr());
        if (idx >= 0)
            alive.remove(idx);
        all.remove(ai.getAddr());
    }

    synchronized void updateAliveConn(AddrInfo ai, boolean connected) {
        if (!all.contains(ai.getAddr()))
            return;
        int idx = foundAlive(ai.getAddr());

        if (connected) {
            if (idx < 0) {
                alive.add(ai);
            }
        } else {
            if (idx >= 0) {
                alive.remove(idx);
            }
        }
    }

    boolean found(String[] ss, String s) {
        for (int i = 0; i < ss.length; ++i) {
            if (s.equals(ss[i])) {
                return true;
            }
        }
        return false;
    }

}
