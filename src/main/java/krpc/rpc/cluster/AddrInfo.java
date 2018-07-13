package krpc.rpc.cluster;

import krpc.common.RetCodes;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AddrInfo implements Addr {

    public static final int MAX_CONNECTIONS = 24; // max connections per addr

    private static int masks[];
    private static int revMasks[];

    static {
        masks = new int[MAX_CONNECTIONS];
        revMasks = new int[MAX_CONNECTIONS];
        int v = 1;
        for (int i = 0; i < MAX_CONNECTIONS; ++i) {
            masks[i] = v;
            revMasks[i] = (~v) & 0xff;
            v *= 2;
        }
    }

    String addr;
    int connections;

    private AtomicInteger status = new AtomicInteger(0); // include all
    // connection
    // status, each bit
    // has a status
    private AtomicInteger current = new AtomicInteger(-1); // current index to
    // get next connect
    private AtomicInteger seq = new AtomicInteger(0);
    private AtomicBoolean removeFlag = new AtomicBoolean(false);

    private HashMap<Integer, Integer> pendings = new HashMap<>(); // serviceId->pendings
    private HashMap<Integer, StatWindow> statWindows = new HashMap<>(); // serviceId->StatWindow

    public AddrInfo(String addr, int connections) {
        this.addr = addr;
        this.connections = connections;
    }

    boolean selectable(int serviceId) {
        synchronized (statWindows) {
            StatWindow w = statWindows.get(serviceId);
            if (w == null)
                return true;
            return w.selectable();
        }
    }

    public void updateResult(ServiceInfo si, int retCode, long timeUsedMicros) {
        synchronized (statWindows) {
            StatWindow w = statWindows.get(si.getServiceId());
            if (w == null) {
                w = new StatWindow(si.getBreakerInfo());
                statWindows.put(si.getServiceId(), w);
            }
            w.incReq(retCode, timeUsedMicros);
        }
    }

    public void incPending(int serviceId) {
        synchronized (pendings) {
            Integer i = pendings.get(serviceId);
            if (i == null)
                pendings.put(serviceId, 1);
            else
                pendings.put(serviceId, i + 1);
        }
    }

    public void decPending(int serviceId) {
        synchronized (pendings) {
            Integer i = pendings.get(serviceId);
            if (i != null) {
                pendings.put(serviceId, i - 1);
            }
        }
    }

    public int getPendingCalls(int serviceId) {
        synchronized (pendings) {
            Integer i = pendings.get(serviceId);
            if (i == null)
                return 0;
            return i;
        }
    }

    public String getAddr() {
        return addr;
    }

    public boolean isConnected() {
        return status.get() != 0;
    }

    public boolean isConnected(int index) {
        int v = status.get();
        return (v & masks[index]) != 0;
    }

    public void setConnected(int index) {
        while (true) {
            int v = status.get();
            int newv = v | masks[index];
            boolean ok = status.compareAndSet(v, newv);
            if (ok)
                break;
        }
    }

    public void setDisConnected(int index) {
        while (true) {
            int v = status.get();
            int newv = v & revMasks[index];
            boolean ok = status.compareAndSet(v, newv);
            if (ok)
                break;
        }
    }

    public int nextConnection() {
        int cur = current.incrementAndGet();
        if (cur >= 10000000)
            current.compareAndSet(cur, 0);
        int index = cur % connections;

        int v = status.get();

        if ((v & masks[index]) != 0)
            return index;
        for (int i = 0; i < connections; ++i) {
            if ((v & masks[i]) != 0)
                return i;
        }

        return 0;
    }

    public void setRemoveFlag(boolean flag) {
        this.removeFlag.set(flag);
    }

    public boolean getRemoveFlag() {
        return this.removeFlag.get();
    }

    public int nextSequence() {
        int v = seq.incrementAndGet();
        if (v >= 10000000)
            seq.compareAndSet(v, 0);
        return v;
    }

    static class StatPerSecond {
        long time;
        int reqs = 0;
        int errors = 0;
        int timeouts = 0;

        StatPerSecond(long time) {
            this.time = time;
        }
    }

    static class StatWindow {
        BreakerInfo bi;

        LinkedList<StatPerSecond> list = new LinkedList<StatPerSecond>();
        boolean closed = false;
        long closedTs = 0;
        int recoverTimes = 0;

        StatWindow(BreakerInfo bi) {
            this.bi = bi;
        }

        boolean selectable() {
            if (!closed)
                return true;

            if (bi.isForceClose()) {
                return false;
            }

            long now = System.currentTimeMillis();
            int n = (int) ((now - closedTs) / bi.getSleepMillis());
            if (recoverTimes >= n)
                return false;
            recoverTimes++;
            return true;
        }

        void incReq(int retCode, long timeUsedMicros) {
            long now = System.currentTimeMillis();
            updateStats(now, retCode, timeUsedMicros);
            updateStatus(now, retCode, timeUsedMicros);
        }

        void updateStatus(long now, int retCode, long timeUsedMicros) {
            if (!closed) {
                int curRate = getCloseRate(now);
                if (curRate >= bi.getCloseRate()) {
                    closed = true;
                    closedTs = now;
                    recoverTimes = 0;
                }
            } else {
                if (retCode != 0)
                    return;
                if (now - closedTs < bi.getSleepMillis())
                    return;
                if (timeUsedMicros >= bi.getSuccMills() * 1000)
                    return;
                closed = false;
            }
        }

        void updateStats(long now, int retCode, long timeUsedMicros) {
            long nowSeconds = now / 1000;
            if (list.isEmpty() || nowSeconds > list.getFirst().time) {
                list.addFirst(new StatPerSecond(nowSeconds));
            }
            StatPerSecond item = list.getFirst();
            item.reqs++;
            if (retCode != 0)
                item.errors++;
            if (RetCodes.isTimeout(retCode))
                item.timeouts++;

            // clear old data
            long clearTime = nowSeconds - bi.getWindowSeconds();
            while (list.getLast().time < clearTime) {
                list.removeLast();
            }
        }

        int getCloseRate(long now) {
            long beforeTime = now / 1000 - bi.getWindowSeconds();
            int ttlReqs = 0;
            int ttlError = 0;

            for (StatPerSecond item : list) {
                if (item.time >= beforeTime) {
                    ttlReqs += item.reqs;
                    if (bi.getCloseBy() == 1)
                        ttlError += item.errors;
                    else if (bi.getCloseBy() == 2)
                        ttlError += item.timeouts;
                } else {
                    break;
                }
            }

            if (ttlReqs == 0)
                return 0;

            if (ttlReqs < bi.getWindowMinReqs())
                return 0;

            return ttlError * 100 / ttlReqs;
        }

    }

}
