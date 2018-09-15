package krpc.rpc.cluster;

import com.google.protobuf.Message;
import krpc.common.InitClose;
import krpc.rpc.core.*;
import krpc.rpc.core.DynamicRouteConfig.AddrWeight;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultClusterManager implements ClusterManager, RegistryManagerCallback, DynamicRouteManagerCallback, InitClose {

    static Logger log = LoggerFactory.getLogger(DefaultClusterManager.class);

    int waitMillis = 500;
    int connections = 1;
    TransportChannel transportChannel;

    Map<Integer, LoadBalance> lbs = new HashMap<>(); // serviceId->LoadBalance
    Map<Integer, Router> routers = new HashMap<>(); // serviceId->Router
    Map<Integer, BreakerInfo> breakers = new HashMap<>(); // serviceId->BreakerInfo

    Set<String> lastAddrs = new HashSet<String>();
    ConcurrentHashMap<Integer, ServiceInfo> serviceMap = new ConcurrentHashMap<Integer, ServiceInfo>();
    ConcurrentHashMap<String, AddrInfo> addrMap = new ConcurrentHashMap<String, AddrInfo>();

    public DefaultClusterManager(TransportChannel transportChannel) {
        this.transportChannel = transportChannel;
    }

    public void init() {
        if (connections <= 0 || connections > AddrInfo.MAX_CONNECTIONS)
            throw new RuntimeException("connections is not allowed");
    }

    public void close() {

    }

    public void routeConfigChanged(DynamicRouteConfig c) {
        ServiceInfo si = serviceMap.get(c.getServiceId());

        if (si == null) return;

        si.setDisabled(c.isDisabled());

        List<AddrWeight> weights = c.getWeights();
        si.configWeights(weights);

        List<RouteRule> rules = c.getRules();
        si.configRules(rules);
    }

    public void addrChanged(Map<Integer, String> addrsMap) {
        doAdd(addrsMap);
        doRemove(addrsMap);
    }

    void doAdd(Map<Integer, String> addrsMap) {
        boolean hasNewAddr = false;
        for (Map.Entry<Integer, String> en : addrsMap.entrySet()) {
            boolean ok = doAdd(en.getKey(), en.getValue());
            if (ok) hasNewAddr = true;
        }
        if (hasNewAddr) {
            try {
                Thread.sleep(waitMillis); // wait for connection established
            } catch (Exception e) {
            }
        }
    }

    boolean doAdd(int serviceId, String addrs) {

        ServiceInfo si = serviceMap.get(serviceId);
        if (si == null) {
            LoadBalance lb = getLbPolicy(serviceId);
            Router r = getRouter(serviceId);
            BreakerInfo bi = getBreakerInfo(serviceId);
            si = new ServiceInfo(serviceId, lb, r, bi);
            serviceMap.put(serviceId, si);
        }
        HashSet<String> newSet = splitAddrs(addrs);
        HashSet<String> toBeAdded = si.mergeFrom(newSet);

        boolean hasNewAddr = false;
        for (String addr : toBeAdded) {
            AddrInfo ai = addrMap.get(addr);
            if (ai == null) {
                ai = new AddrInfo(addr, connections);
                AddrInfo old = addrMap.putIfAbsent(addr, ai);
                if (old == null) {
                    for (int i = 0; i < connections; ++i) {
                        String connId = makeConnId(addr, i);
                        transportChannel.connect(connId, addr);
                    }
                    hasNewAddr = true;
                }
            }
        }

        return hasNewAddr;
    }

    void doRemove(Map<Integer, String> addrsMap) {

        Set<String> newAddrs = new HashSet<String>();
        for (String s : addrsMap.values()) {
            newAddrs.addAll(splitAddrs(s));
        }

        Set<String> allAddrs = new HashSet<String>();
        for (ServiceInfo si : serviceMap.values()) {
            si.copyTo(allAddrs);
        }

        // set removeFlag or removeConn
        Set<String> toBeRemoved = sub(allAddrs, newAddrs);  // not used by any service
        for (String addr : toBeRemoved) {
            AddrInfo ai = addrMap.get(addr);
            if (ai != null) {
                if (ai.isConnected()) {
                    ai.setRemoveFlag(true);
                } else {
                    removeAddr(ai);
                }
            }
        }

        // recover removeFlag if set by last time
        Set<String> toBeRecover = sub(newAddrs, lastAddrs);
        for (String addr : toBeRecover) {
            AddrInfo ai = addrMap.get(addr);
            if (ai != null) {
                if (ai.getRemoveFlag()) {
                    ai.setRemoveFlag(false);
                }
            }
        }

        lastAddrs = newAddrs;
    }

    public String nextConnId(ClientContextData ctx, Message req) {
        int serviceId = ctx.getMeta().getServiceId();
        ServiceInfo si = serviceMap.get(serviceId);
        if (si == null) return null;
        if (si.isDisabled()) return null;
        AddrInfo ai = si.nextAddr(ctx, req);
        if (ai == null) return null;
        if (si.getLoadBalance().needPendings()) {
            ai.incPending(serviceId);
        }
        int index = ai.nextConnection();
        return makeConnId(ai.addr, index);
    }

    public int nextSequence(String connId) {
        String addr = getAddr(connId);
        AddrInfo ai = addrMap.get(addr);
        if (ai == null) return 0;
        return ai.nextSequence();
    }

    public boolean isConnected(String connId) {
        String addr = getAddr(connId);
        AddrInfo ai = addrMap.get(addr);
        if (ai == null) return false;
        int index = getIndex(connId);
        return ai.isConnected(index);
    }

    public void connected(String connId, String localAddr) {
        String addr = getAddr(connId);
        AddrInfo ai = addrMap.get(addr);
        if (ai == null) return;
        int index = getIndex(connId);
        ai.setConnected(index);
        updateAliveConn(ai, true);
    }

    public void disconnected(String connId) {
        String addr = getAddr(connId);
        AddrInfo ai = addrMap.get(addr);
        if (ai == null) return;
        int index = getIndex(connId);
        ai.setDisConnected(index);
        boolean connected = ai.isConnected();
        if (!connected && ai.getRemoveFlag()) {
            removeAddr(ai);
            return;
        }
        updateAliveConn(ai, connected);
    }

    void removeAddr(AddrInfo ai) {
        for (int i = 0; i < ai.connections; ++i)
            transportChannel.disconnect(makeConnId(ai.addr, i));
        addrMap.remove(ai.addr);
        for (ServiceInfo si : serviceMap.values()) {
            si.remove(ai);
        }
    }

    void updateAliveConn(AddrInfo ai, boolean connected) {
        for (ServiceInfo si : serviceMap.values()) {
            si.updateAliveConn(ai, connected);
        }
    }

    public void updateStats(RpcClosure closure) {
        int serviceId = closure.getCtx().getMeta().getServiceId();
        ServiceInfo si = serviceMap.get(serviceId);
        if (si == null) return;
        String connId = closure.getCtx().getConnId();
        String addr = getAddr(connId);
        AddrInfo ai = addrMap.get(addr);
        if (ai == null) return;
        if (si.getLoadBalance().needPendings()) {
            ai.decPending(serviceId);
        }

        BreakerInfo bi = getBreakerInfo(serviceId);
        if (!bi.isEnabled()) return;

        int retCode = ReflectionUtils.getRetCode(closure.getRes());
        long ts = closure.getCtx().getTimeUsedMicros();
        ai.updateResult(si, retCode, ts);
    }

    String getAddr(String connId) {
        int p = connId.lastIndexOf(":");
        return connId.substring(0, p);
    }

    int getIndex(String connId) {
        int p = connId.lastIndexOf(":");
        return Integer.parseInt(connId.substring(p + 1)) - 1;
    }

    String makeConnId(String addr, int index) {
        return addr + ":" + (index + 1);
    }

    HashSet<String> splitAddrs(String s) {
        HashSet<String> newSet = new HashSet<String>();

        if (s != null && s.length() > 0) {
            String[] ss = s.split(",");
            for (String t : ss) newSet.add(t);
        }

        return newSet;
    }

    public HashSet<String> sub(Set<String> a, Set<String> b) {
        HashSet<String> result = new HashSet<String>();
        result.addAll(a);
        result.removeAll(b);
        return result;
    }

    public void addServiceInfo(int serviceId, LoadBalance policy, Router r, BreakerInfo bi) {
        lbs.put(serviceId, policy);
        routers.put(serviceId, r);
        breakers.put(serviceId, bi);
    }

    LoadBalance getLbPolicy(int serviceId) {
        LoadBalance p = lbs.get(serviceId);
        return p;
    }

    Router getRouter(int serviceId) {
        Router r = routers.get(serviceId);
        return r;
    }

    BreakerInfo getBreakerInfo(int serviceId) {
        BreakerInfo bi = breakers.get(serviceId);
        return bi;
    }

    public TransportChannel getTransportChannel() {
        return transportChannel;
    }

    public void setTransportChannel(TransportChannel transportChannel) {
        this.transportChannel = transportChannel;
    }

    public int getWaitMillis() {
        return waitMillis;
    }

    public void setWaitMillis(int waitMillis) {
        this.waitMillis = waitMillis;
    }

    public int getConnections() {
        return connections;
    }

    public void setConnections(int connections) {
        this.connections = connections;
    }

}
