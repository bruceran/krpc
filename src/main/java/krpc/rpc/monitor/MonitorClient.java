package krpc.rpc.monitor;

import com.google.protobuf.Message;
import krpc.common.InitClose;
import krpc.common.StartStop;
import krpc.rpc.core.*;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.impl.transport.NettyClient;
import krpc.rpc.monitor.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MonitorClient implements TransportCallback, InitClose, StartStop {

    static Logger log = LoggerFactory.getLogger(MonitorClient.class);

    static final public int MONITOR_SERVICEID = 2;
    static final public int MONITOR_RPCSTATS_MSGID = 1;
    static final public int MONITOR_SYSTEMINFO_MSGID = 2;
    static final public int MONITOR_ALARM_MSGID = 3;
    static final public int MONITOR_METAINFO_MSGID = 4;

    static class AddrInfo {
        String addr;
        String connId;
        AtomicBoolean connected = new AtomicBoolean(false);

        AddrInfo(String addr) {
            this.addr = addr;
            connId = addr + ":1";
        }
    }

    int maxRetryTime = 3; // seconds
    int retryInterval = 1; // seconds
    int sendTimeout = 1; // seconds

    ArrayList<AddrInfo> addrList = new ArrayList<>();
    AtomicInteger addrIndex = new AtomicInteger(-1);
    AtomicInteger seq = new AtomicInteger(0);
    Timer timer;
    NettyClient nettyClient;

    ConcurrentHashMap<Integer, Message> dataManager = new ConcurrentHashMap<>();

    AtomicInteger errorCount = new AtomicInteger();

    public MonitorClient(String addrs, RpcCodec codec, ServiceMetas serviceMetas, Timer timer) {

        serviceMetas.addDirect(MONITOR_SERVICEID, MONITOR_RPCSTATS_MSGID,
                ReportRpcStatReq.class, ReportRpcStatRes.class);
        serviceMetas.addDirect(MONITOR_SERVICEID, MONITOR_SYSTEMINFO_MSGID,
                ReportSystemInfoReq.class, ReportSystemInfoRes.class);
        serviceMetas.addDirect(MONITOR_SERVICEID, MONITOR_ALARM_MSGID,
                ReportAlarmReq.class, ReportAlarmRes.class);
        serviceMetas.addDirect(MONITOR_SERVICEID, MONITOR_METAINFO_MSGID,
                ReportMetaReq.class, ReportMetaRes.class);

        this.timer = timer;

        String[] ss = addrs.split(",");
        for (int i = 0; i < ss.length; ++i) {
            addrList.add(new AddrInfo(ss[i]));
        }

        nettyClient = new NettyClient(this, codec, serviceMetas);
        nettyClient.setWorkerThreads(1);
    }

    public void init() {

        nettyClient.init();

        for (AddrInfo ai : addrList) {
            nettyClient.connect(ai.connId, ai.addr);
        }

        log.info("monitor client started");
    }

    public void start() {
        nettyClient.start();
    }

    public void stop() {
        nettyClient.stop();
    }

    public void close() {
        for (AddrInfo ai : addrList) {
            nettyClient.disconnect(ai.connId);
        }
        nettyClient.close();

        log.info("monitor client closed");
    }

    AddrInfo findAddrInfo(String connId) {
        for (AddrInfo ai : addrList) {
            if (ai.connId.equals(connId)) return ai;
        }
        return null;
    }

    AddrInfo nextAddrInfo() {
        for (int kk = 0; kk < addrList.size(); ++kk) {
            int i = addrIndex.incrementAndGet();
            if (i >= 10000000) addrIndex.compareAndSet(i, 0);
            int idx = i % addrList.size();
            AddrInfo ai = addrList.get(idx);
            if (ai.connected.get()) return ai;
        }
        return null;
    }

    public void send(ReportRpcStatReq req) {
        timer.schedule(new TimerTask() {
            public void run() {
                doReport(req);
            }
        }, 0);
    }

    public void send(ReportSystemInfoReq req) {
        timer.schedule(new TimerTask() {
            public void run() {
                doReport(req);
            }
        }, 0);
    }

    public void send(ReportAlarmReq req) {
        timer.schedule(new TimerTask() {
            public void run() {
                doReport(req);
            }
        }, 0);
    }

    public void send(ReportMetaReq req) {
        timer.schedule(new TimerTask() {
            public void run() {
                doReport(req);
            }
        }, 0);
    }

    void doReport(Message req) {
        try {
            doReportInternal(req);
        } catch(Throwable e) {
            log.error("doReportInternal exception",e);
        }
    }
    void doReportInternal(Message req) {
        int sequence = nextSequence();
        dataManager.put(sequence, req);
        boolean ok = send(sequence, req);
        if (ok) {
            timer.schedule(new TimerTask() {
                public void run() {
                    try {
                        checkTimeout(sequence);
                    } catch(Throwable e) {
                        log.error("checkTimeout exception",e);
                    }
                }
            }, sendTimeout * 1000);
        } else {
            dataManager.remove(sequence);
            retry(req);
        }
    }

    void retry(Message req) {
        long now = System.currentTimeMillis();
        if (now - getStartTime(req) >= maxRetryTime * 1000) return;

        timer.schedule(new TimerTask() {
            public void run() {
                doReport(req);
            }
        }, retryInterval * 1000);
    }

    void checkTimeout(int sequence) {
        Message req = dataManager.remove(sequence);
        if (req == null) return;
        retry(req);
    }

    long getStartTime(Message req) {
        if( req instanceof  ReportRpcStatReq ) return ((ReportRpcStatReq)req).getTimestamp();
        if( req instanceof  ReportSystemInfoReq ) return ((ReportSystemInfoReq)req).getTimestamp();
        if( req instanceof  ReportAlarmReq ) return ((ReportAlarmReq)req).getTimestamp();
        if( req instanceof  ReportMetaReq ) return ((ReportMetaReq)req).getTimestamp();
        return 0;
    }

    int getMsgId(Message req) {
        if( req instanceof  ReportRpcStatReq ) return MONITOR_RPCSTATS_MSGID;
        if( req instanceof  ReportSystemInfoReq ) return MONITOR_SYSTEMINFO_MSGID;
        if( req instanceof  ReportAlarmReq ) return MONITOR_ALARM_MSGID;
        if( req instanceof  ReportMetaReq ) return MONITOR_METAINFO_MSGID;
        return 0;
    }

    boolean send(int sequence, Message req) {
        boolean ok;

        try {
            ok = sendInternal(sequence, req);
        } catch(Throwable e) {
            log.error("sendInternal exception",e);
            ok = false;
        }
        if(!ok) {
            errorCount.incrementAndGet();
        }
        return ok;
    }

    boolean sendInternal(int sequence, Message req) {
        AddrInfo ai = nextAddrInfo();
        if (ai == null) return false;
        int msgId = getMsgId(req);
        if( msgId == 0 ) return false;
        RpcMeta.Builder builder = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.REQUEST).setServiceId(MONITOR_SERVICEID).setMsgId(msgId).setSequence(sequence);
        RpcMeta meta = builder.build();
        RpcData data = new RpcData(meta, req);
        return nettyClient.send(ai.connId, data);
    }

    public boolean isExchange(String connId, RpcMeta meta) {
        return false;
    }

    public void receive(String connId, RpcData data,long receiveMicros) {
        if (data.getMeta().getDirection() == RpcMeta.Direction.REQUEST) return;
        Message req = dataManager.remove(data.getMeta().getSequence());
        if (req == null) return;
        int retCode = data.getMeta().getRetCode();
        if (retCode == 0) return;
        retry(req);
    }

    public void connected(String connId, String localAddr) {
        AddrInfo ai = findAddrInfo(connId);
        if (ai == null) return;
        ai.connected.set(true);
    }

    public void disconnected(String connId) {
        AddrInfo ai = findAddrInfo(connId);
        if (ai == null) return;
        ai.connected.set(false);
    }

    int nextSequence() {
        int v = seq.incrementAndGet();
        if (v >= 10000000) seq.compareAndSet(v, 0);
        return v;
    }

    int getErrorCount() {
        return errorCount.getAndSet(0);
    }
}
