package krpc.rpc.monitor;

import com.google.protobuf.Message;
import krpc.common.*;
import krpc.rpc.core.*;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.monitor.proto.*;
import krpc.rpc.util.IpUtils;
import krpc.rpc.util.TypeSafe;
import krpc.rpc.web.WebClosure;
import krpc.rpc.web.WebMonitorService;
import krpc.trace.DefaultTraceContext;
import krpc.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultMonitorService implements MonitorService, WebMonitorService, InitClose, Alarm, HealthPlugin {

    static Logger log = LoggerFactory.getLogger(DefaultMonitorService.class);

    static final String sep = ",   ";
    static public int[] timeSpans = new int[]{10, 25, 50, 100, 250, 500, 1000, 3000};

    String tags;

    String appName;
    String localIp;
    ServiceMetas serviceMetas;
    ErrorMsgConverter errorMsgConverter;

    RpcCodec codec;

    int logThreads = 1;
    int logQueueSize = 10000;
    boolean accessLog = true;
    Set<Integer> auditFreeServiceIds;

    boolean printOriginalMsgName = true;

    HashMap<String, String> tagsMap = new HashMap<>();

    LogFormatter logFormatter = new SimpleLogFormatter();
    ThreadPoolExecutor logPool;
    ConcurrentHashMap<String, Logger> serverLogMap = new ConcurrentHashMap<String, Logger>();
    ConcurrentHashMap<String, Logger> webServerLogMap = new ConcurrentHashMap<String, Logger>();
    ConcurrentHashMap<String, Logger> clientLogMap = new ConcurrentHashMap<String, Logger>();

    int statsQueueSize = 10000;
    ThreadPoolExecutor statsPool;
    HashMap<String, StatItem> stats = new HashMap<>();

    Timer timer;

    String serverAddr;
    MonitorClient monitorClient;

    List<MonitorPlugin> plugins;

    String alarmPrefix = "999";

    SelfCheckHttpServer selfCheckHttpServer;

    volatile int lastErrorCount = 0;

    public static Map<Integer,String> vsServiceNames;  // krpc 自己不初始化，由外部初始化

    Set<Integer> usedVsServiceIds = new CopyOnWriteArraySet<>();
    Map<String,String> usedVsMsgNames = new ConcurrentHashMap<>();

    static class AlarmInfoInWait {
        String type;
        String msg;
        int count = 1;
        long timestamp = System.currentTimeMillis();
        String target;
        String targetAddrs;


        AlarmInfoInWait(String type, String msg, String target, String targetAddrs) {
            this.type = type;
            this.msg = msg;
            this.target = target;
            this.targetAddrs = targetAddrs;
        }
    }

    private ReentrantLock alarmLock = new ReentrantLock();
    private HashMap<String, AlarmInfoInWait> waitAlarms = new HashMap<>();

    DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    DateTimeFormatter statsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ZoneOffset offset = OffsetDateTime.now().getOffset();

    ArrayList<Object> resources = new ArrayList<>();

    long lastReportMetaTime = 0;
    long reportMetaMinute = 0; // 哪一分钟上报，错开时间上报


    public DefaultMonitorService(RpcCodec codec, ServiceMetas serviceMetas,ErrorMsgConverter errorMsgConverter) {
        this.codec = codec;
        this.serviceMetas = serviceMetas;
        this.errorMsgConverter = errorMsgConverter;
    }

    void initTagsMap() {
        String[] ss = tags.split("#");
        for (String s : ss) {
            int p = s.indexOf(":");
            String key = s.substring(0, p);
            String value = s.substring(p + 1);
            String[] msgIds = value.split(",");
            for (String msgId : msgIds) {
                String v = tagsMap.get(msgId);
                if (v == null) v = key;
                else v = v + "," + key;
                tagsMap.put(msgId, v);
            }
        }
    }


    public void init() {

        localIp = IpUtils.localIp();

        if (!isEmpty(tags)) {
            initTagsMap();
        }

        timer = new Timer("krpc_asyncstats_timer");

        reportMetaMinute = 10 + System.currentTimeMillis() % 40;  // 每天 3点： 10 分 - 50 分上报元数据

        if (serverAddr != null && serverAddr.length() > 0) {
            monitorClient = new MonitorClient(serverAddr, codec, serviceMetas, timer);
        }

        timer.schedule(new TimerTask() {
            public void run() {
                try {
                    statsTimer();
                } catch(Throwable e) {
                    log.error("statsTimer exception",e);
                }
            }
        }, 10000, 1000);
        timer.schedule(new TimerTask() {
            public void run() {
                try {
                    reportSystemInfo();
                } catch(Throwable e) {
                    log.error("reportSystemInfo exception",e);
                }
            }
        }, 60000, 60000);

        if (monitorClient != null) {

            timer.schedule(new TimerTask() {
                public void run() {
                    try {
                        reportAlarm();
                    } catch(Throwable e) {
                        log.error("reportAlarm exception",e);
                    }
                }
            }, 5000, 5000);
        }

        resources.add(logFormatter);
        resources.add(monitorClient);

        if (plugins != null) {
            for (MonitorPlugin p : plugins) {
                resources.add(p);
            }
        }

        InitCloseUtils.init(resources);

        if (accessLog) {
            ThreadFactory logThreadFactory = new NamedThreadFactory("krpc_asynclog");
            logPool = new ThreadPoolExecutor(logThreads, logThreads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(logQueueSize), logThreadFactory);
            logPool.prestartAllCoreThreads();
        }

        ThreadFactory statsThreadFactory = new NamedThreadFactory("krpc_asyncstats");
        statsPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(statsQueueSize), statsThreadFactory);
        statsPool.prestartAllCoreThreads();

        log.info("monitor service started");
    }

    public void close() {

        timer.cancel();

        statsPool.shutdown();
        if (logPool != null) {
            logPool.shutdown();
        }

        InitCloseUtils.close(resources);

        log.info("monitor service stopped");
    }

    public void setAuditFreeServiceIds(String s) {
        if( s == null || s.isEmpty() ) return;
        auditFreeServiceIds = new HashSet<>();
        String[] ss = s.split(",");
        for(String t: ss) {
            int serviceId = TypeSafe.anyToInt(t);
            if( serviceId > 0 ) {
                auditFreeServiceIds.add(serviceId);
            }
        }
    }

    boolean ignored(int serviceId) {
        if( auditFreeServiceIds == null ) return false;
        return auditFreeServiceIds.contains(serviceId);
    }

    public void webReqDone(WebClosure closure) {

        if( ignored(closure.getCtx().getMeta().getServiceId())) return;

        if (accessLog) {
            try {
                logPool.execute(new Runnable() {
                    public void run() {
                        try {
                            doLog(true, closure);
                        } catch (Exception e) {
                            log.error("asynclog exception", e);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                log.error("asynclog queue is full");
            }
        }

        try {
            statsPool.execute(new Runnable() {
                public void run() {
                    try {
                        doStats(closure);
                    } catch (Exception e) {
                        log.error("asyncstats exception", e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("asyncstats queue is full");
        }

        if (plugins != null) {
            for (MonitorPlugin p : plugins) {
                p.webReqDone(closure);
            }
        }
    }

    private RpcClosure convertClosure(RpcClosure closure0) {
        if (!closure0.isRaw()) return closure0;
        Message req = codec.decodeRawBody(closure0.getCtx().getMeta(), closure0.asReqByteBuf());
        Message res = codec.decodeRawBody(closure0.getCtx().getMeta(), closure0.asResByteBuf());
        return new RpcClosure(closure0, req, res);
    }

    public void reqDone(final RpcClosure closure0) {

        if( ignored(closure0.getCtx().getMeta().getServiceId())) return;

        RpcClosure closure = convertClosure(closure0);

        if (accessLog) {
            try {
                logPool.execute(new Runnable() {
                    public void run() {
                        try {
                            doLog(true, closure);
                        } catch (Exception e) {
                            log.error("asynclog exception", e);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                log.error("asynclog queue is full");
            }
        }

        try {
            statsPool.execute(new Runnable() {
                public void run() {
                    try {
                        doStats(1, closure);
                    } catch (Exception e) {
                        log.error("asyncstats exception", e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("asyncstats queue is full");
        }

        if (plugins != null) {
            for (MonitorPlugin p : plugins) {
                p.rpcReqDone(closure);
            }
        }
    }

    public void callDone(RpcClosure closure0) {

        if( ignored(closure0.getCtx().getMeta().getServiceId())) return;

        RpcClosure closure = convertClosure(closure0);

        if (accessLog) {
            try {
                logPool.execute(new Runnable() {
                    public void run() {
                        try {
                            doLog(false, closure);
                        } catch (Exception e) {
                            log.error("asynclog log exception", e);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                log.error("asynclog queue is full");
            }
        }

        try {
            statsPool.execute(new Runnable() {
                public void run() {
                    try {
                        doStats(2, closure);
                    } catch (Exception e) {
                        log.error("asyncstats exception", e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("asyncstats queue is full");
        }

        if (plugins != null) {
            for (MonitorPlugin p : plugins) {
                p.rpcCallDone(closure);
            }
        }
    }
    public void virtualServiceDone(VirtualServiceClosure closure) {

        if( statsPool == null ) return; // may be null in SpringBootTest junit test
        if( closure.getServiceId() == 0 || ( closure.getServiceId() % 1000 ) == 999 ) return;

        try {
            statsPool.execute(new Runnable() {
                public void run() {
                    try {
                        doStats(closure);
                        saveUsedVirtualServiceInfo(closure);
                    } catch (Exception e) {
                        log.error("asyncstats exception", e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("asyncstats queue is full");
        }

    }

    void saveUsedVirtualServiceInfo(VirtualServiceClosure closure) {
        usedVsServiceIds.add(closure.getServiceId());
        if( closure.getMsgId() == 999 ) return;
        String msgName = closure.getMsgName();
        if( closure.getMsgId() >= 900 ) {   // xxx.yyyy -> yyy  由于xxx可变，上报的话会一直变, 所以只报yyy部分
            msgName = msgName.substring(msgName.lastIndexOf(".")+1);
        }
        usedVsMsgNames.put(closure.getServiceId() + "." + closure.getMsgId(), msgName);

        // System.out.println("usedVsMsgNames="+usedVsMsgNames.toString());
    }

    void doLog(boolean isServerLog, RpcClosure closure) {
        RpcMeta meta = closure.getCtx().getMeta();
        Logger log = null;
        if (isServerLog) log = getServerLog(meta.getServiceId(), meta.getMsgId());
        else log = getClientLog(meta.getServiceId(), meta.getMsgId());
        if (!log.isInfoEnabled()) return;
        String msg = getLogStr(closure);
        log.info(msg);
    }

    void doLog(boolean isServerLog, WebClosure closure) {
        RpcMeta meta = closure.getCtx().getMeta();
        Logger log = getWebServerLog(meta.getServiceId(), meta.getMsgId());
        if (!log.isInfoEnabled()) return;
        String msg = getLogStr(closure);
        log.info(msg);
    }

    Logger getWebServerLog(int serviceId, int msgId) {
        String key = serviceId + "." + msgId;
        Logger log = webServerLogMap.get(key);
        if (log != null) return log;
        String key2 = "krpc.webserverlog." + key;
        log = LoggerFactory.getLogger(key2);
        webServerLogMap.put(key, log);
        return log;
    }

    Logger getServerLog(int serviceId, int msgId) {
        String key = serviceId + "." + msgId;
        Logger log = serverLogMap.get(key);
        if (log != null) return log;
        String key2 = "krpc.serverlog." + key;
        log = LoggerFactory.getLogger(key2);
        serverLogMap.put(key, log);
        return log;
    }

    Logger getClientLog(int serviceId, int msgId) {
        String key = serviceId + "." + msgId;
        Logger log = clientLogMap.get(key);
        if (log != null) return log;
        String key2 = "krpc.clientlog." + key;
        log = LoggerFactory.getLogger(key2);
        clientLogMap.put(key, log);
        return log;
    }

    String getTags(RpcMeta meta) {
        String key = meta.getServiceId() + "." + meta.getMsgId();
        String tags = tagsMap.get(key);
        return tags;
    }

    private void appendVars(ServerContextData serverCtx, StringBuilder extraInfo) {
        Map<String, Object> attrs = serverCtx.getAttributes();
        if (attrs == null) return;
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("var:")) {
                key = key.substring(4);
                String value = TypeSafe.anyToString(entry.getValue());
                if (extraInfo.length() > 0) extraInfo.append("^");
                extraInfo.append(key).append(":").append(value);
            }
        }
    }

    private void appendTimeUsed(ServerContextData serverCtx, StringBuilder extraInfo,long ttl) {
        TraceContext tc = serverCtx.getTraceContext();
        if (tc == null) return;
        if (!(tc instanceof DefaultTraceContext)) return;
        DefaultTraceContext dtc = (DefaultTraceContext) tc;
        String timeUsedStr = dtc.getTimeUsedStr();
        if (isEmpty(timeUsedStr)) return;
        int p = timeUsedStr.indexOf("^IOSUM:");
        long ot = 0;
        if( p > 0 ) {
            ot = ttl - TypeSafe.anyToLong(timeUsedStr.substring(p+7));
            if( ot < 0 ) ot = 0;
            timeUsedStr = timeUsedStr.substring(0,p);
        }
        if (extraInfo.length() > 0) extraInfo.append("^");
        extraInfo.append(timeUsedStr);
        if( ot >= 0 ) {
            extraInfo.append("^OTS:").append(ot);
        }
    }

    private void appendTimeUsedInQueue(ServerContextData serverCtx, StringBuilder extraInfo) {

        if( serverCtx.getDecodeMicros() > 1000 ) { // >= 1ms
            if (extraInfo.length() > 0) extraInfo.append("^");
            extraInfo.append("INTS:" + serverCtx.getDecodeMicros());
        }

        if (extraInfo.length() > 0) extraInfo.append("^");
        extraInfo.append("Q:" + serverCtx.getWaitInQueueMicros());
    }

    private void appendTags(RpcMeta meta, StringBuilder extraInfo) {
        String tags = getTags(meta);
        if (isEmpty(tags)) return;
        if (extraInfo.length() > 0) extraInfo.append("^");
        extraInfo.append("tags:" + tags);
    }

    private void appendRpcTags(ServerContextData serverCtx, StringBuilder extraInfo) {
        String expDivIds = serverCtx.getTraceContext().getTagForRpc("expDivIds");
        if (expDivIds != null && !expDivIds.isEmpty()) {
            if (extraInfo.length() > 0) extraInfo.append("^");
            extraInfo.append("expDivIds:" + expDivIds);
        }
    }

    private void appendDyeing(ServerContextData serverCtx, StringBuilder extraInfo) {
        String dyeing = serverCtx.getTraceContext().getTrace().getDyeing();
        if (dyeing != null && !dyeing.isEmpty()) {
            if (extraInfo.length() > 0) extraInfo.append("^");
            extraInfo.append("dyeing:" + dyeing);
        }
    }

    private void appendClientDyeing(RpcContextData ctx, StringBuilder extraInfo) {
        String dyeing = ctx.getMeta().getTrace().getDyeing();
        if (dyeing != null && !dyeing.isEmpty()) {
            if (extraInfo.length() > 0) extraInfo.append("^");
            extraInfo.append("dyeing:" + dyeing);
        }
    }

    String getLogStr(RpcClosure closure) {
        StringBuilder b = new StringBuilder();
        RpcContextData ctx = closure.getCtx();
        RpcMeta meta = ctx.getMeta();
        RpcMeta.Trace trace = meta.getTrace();
        String spanId = trace.getSpanId();
        StringBuilder extraInfo = new StringBuilder();
        long timeUsedMicros = ctx.getTimeUsedMicros();
        if (ctx instanceof ServerContextData) {
            ServerContextData serverCtx = ((ServerContextData) ctx);
            trace = serverCtx.getTraceContext().getTrace();
            spanId = trace.getSpanId();

            // timeUsedMicros += serverCtx.getDecodeMicros();

            appendTimeUsedInQueue(serverCtx, extraInfo);
            appendTimeUsed(serverCtx, extraInfo, ctx.getTimeUsedMicros() -serverCtx.getWaitInQueueMicros() );
            appendTags(meta, extraInfo);
            appendVars(serverCtx, extraInfo);
            appendRpcTags(serverCtx, extraInfo);
            appendDyeing(serverCtx, extraInfo);
        } else {
            appendClientDyeing(ctx, extraInfo);
        }

        long responseTime = ctx.getResponseTimeMicros();
        String timestamp = logFormat.format(LocalDateTime.ofEpochSecond(responseTime / 1000000, (int) ((responseTime % 1000000) * 1000), offset));
        b.append(timestamp);
        b.append(sep);
        b.append(ctx.getConnId());
        b.append(sep);
        b.append(meta.getSequence());
        b.append(sep);
        b.append(trace.getTraceId());
        b.append(sep);
        b.append(spanId);
        b.append(sep);
        b.append(meta.getServiceId());
        b.append(sep);
        b.append(meta.getMsgId());
        b.append(sep);
        String serviceName = "";
        if (printOriginalMsgName)
            serviceName = serviceMetas.getOriginalName(meta.getServiceId(), meta.getMsgId());
        else
            serviceName = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
        if (serviceName == null) serviceName = meta.getServiceId() + "." + meta.getMsgId();
        b.append(serviceName);
        b.append(sep);
        b.append(closure.getRetCode());
        b.append(sep);
        b.append(timeUsedMicros);
        b.append(sep);
        String reqStr = closure.reqData() == null ? "" : logFormatter.toLogStr(closure.asReqMessage());
        if (reqStr.contains(sep)) reqStr = reqStr.replace(sep, ",");
        b.append(reqStr);
        b.append(sep);
        String resStr = closure.resData() == null ? "" : logFormatter.toLogStr(closure.asResMessage());
        if (resStr.contains(sep)) resStr = resStr.replace(sep, ",");
        b.append(resStr);
        b.append(sep);
        b.append(extraInfo.toString());

        return b.toString();
    }

    String getLogStr(WebClosure closure) {
        StringBuilder b = new StringBuilder();
        RpcContextData ctx = closure.getCtx();
        RpcMeta meta = ctx.getMeta();
        RpcMeta.Trace trace = meta.getTrace();
        StringBuilder extraInfo = new StringBuilder();
        long timeUsedMicros = ctx.getTimeUsedMicros();
        if (ctx instanceof ServerContextData) {
            ServerContextData serverCtx = ((ServerContextData) ctx);
            // timeUsedMicros += serverCtx.getDecodeMicros();

            appendTimeUsedInQueue(serverCtx, extraInfo);
            appendTimeUsed(serverCtx, extraInfo, ctx.getTimeUsedMicros() -serverCtx.getWaitInQueueMicros() );
            appendTags(meta, extraInfo);
            appendVars(serverCtx, extraInfo);
            appendRpcTags(serverCtx, extraInfo);
            appendDyeing(serverCtx, extraInfo);
        }

        long responseTime = ctx.getResponseTimeMicros();
        String timestamp = logFormat.format(LocalDateTime.ofEpochSecond(responseTime / 1000000, (int) ((responseTime % 1000000) * 1000), offset));
        b.append(timestamp);
        b.append(sep);
        b.append(ctx.getConnId());
        b.append(sep);
        b.append(meta.getSequence());
        b.append(sep);
        b.append(trace.getTraceId());
        b.append(sep);
        b.append(trace.getSpanId());
        b.append(sep);
        b.append(meta.getServiceId());
        b.append(sep);
        b.append(meta.getMsgId());
        b.append(sep);
        String serviceName = "";
        if (printOriginalMsgName)
            serviceName = serviceMetas.getOriginalName(meta.getServiceId(), meta.getMsgId());
        else
            serviceName = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
        b.append(serviceName);
        b.append(sep);
        b.append(closure.getRes().getRetCode());
        b.append(sep);
        b.append(timeUsedMicros);
        b.append(sep);
        String clientIp = ctx.getClientIp();
        b.append("httpClientIp:").append(clientIp).append("^");
        String reqStr = closure.getReq() == null ? "" : logFormatter.toLogStr(closure.getReq());
        if (reqStr.contains(sep)) reqStr = reqStr.replace(sep, ",");
        b.append(reqStr);
        b.append(sep);
        String resStr = closure.getRes() == null ? "" : logFormatter.toLogStr(closure.getRes());
        if (resStr.contains(sep)) resStr = resStr.replace(sep, ",");
        b.append(resStr);
        b.append(sep);
        b.append(extraInfo.toString());

        return b.toString();
    }

    static class MinuteItem {
        long curTime;
        int success;
        int failed;
        int timeout;
        int[] timeSpanCount = new int[DefaultMonitorService.timeSpans.length + 1];
        Map<Integer, Integer> retCodeCount = new LinkedHashMap<>();

        MinuteItem(long time) {
            curTime = time;
        }
    }


    static class StatItem {

        int serviceId;
        int msgId;
        int logType; // 1=req 2=call 3=web
        long minTime;

        LinkedList<MinuteItem> items = new LinkedList<MinuteItem>();

        StatItem(int serviceId, int msgId, int logType) {
            this.serviceId = serviceId;
            this.msgId = msgId;
            this.logType = logType;
        }

        void update(long responseTimeSeconds, int retCode, long timeUsedMillis) {
            long time = (responseTimeSeconds / 60) * 60;
            MinuteItem item = null;
            if (items.isEmpty()) {
                item = new MinuteItem(time);
                items.addFirst(item);
            } else {
                item = findItem(time);
                if (item == null) return;
            }

            if (retCode == 0) item.success++;
            if (retCode < 0) {
                item.failed++;
                int count = item.retCodeCount.getOrDefault(retCode, 0);
                item.retCodeCount.put(retCode, ++count);
            }
            if (RetCodes.isTimeout(retCode)) item.timeout++;
            int index = timeToIndex(timeUsedMillis);
            item.timeSpanCount[index]++;
        }

        MinuteItem findItem(long time) {
            for (MinuteItem i : items) {
                if (time == i.curTime) {
                    return i;
                }
            }

            if (time > items.getFirst().curTime) {
                MinuteItem item = new MinuteItem(time);
                items.addFirst(item);
                return item;
            } else if (time > minTime) {
                MinuteItem item = new MinuteItem(time);
                items.addLast(item);
                return item;
            } else {
                return null;
            }
        }

        int timeToIndex(long timeUsed) {
            int i = 0;
            while (i < DefaultMonitorService.timeSpans.length) {
                if (timeUsed <= DefaultMonitorService.timeSpans[i]) return i;
                i += 1;
            }
            return DefaultMonitorService.timeSpans.length;
        }

        void load(long time, ReportRpcStatReq.Builder builder) {

            Iterator<MinuteItem> iter = items.iterator();
            while (iter.hasNext()) {
                MinuteItem i = iter.next();
                if (i.curTime <= time) {
                    RpcStat.Builder b = RpcStat.newBuilder();
                    b.setType(logType).setTime(i.curTime).setServiceId(serviceId).setMsgId(msgId);
                    b.setSuccess(i.success).setFailed(i.failed).setTimeout(i.timeout);
                    for (int k = 0; k < i.timeSpanCount.length; ++k)
                        b.addTimeUsed(i.timeSpanCount[k]);
                    for (Map.Entry<Integer, Integer> entry : i.retCodeCount.entrySet()) {
                        RetCodeStat.Builder retCodeStat = RetCodeStat.newBuilder();
                        retCodeStat.setRetCode(entry.getKey());
                        retCodeStat.setCount(entry.getValue());
                        b.addRetCodeStat(retCodeStat);
                    }
                    builder.addStats(b.build());
                    iter.remove();
                }
            }

            minTime = time;
        }


    }


    String typeToName(int logType) {
        switch (logType) {
            case 1:
                return "serverstats";
            case 2:
                return "clientstats";
            case 3:
                return "webserverstats";
            default:
                return "unknownstats";
        }
    }

    void doStats(WebClosure closure) {
        RpcMeta meta = closure.getCtx().getMeta();
        String key = typeToName(3) + ":" + meta.getServiceId() + ":" + meta.getMsgId();
        StatItem item = stats.get(key);
        if (item == null) {
            item = new StatItem(meta.getServiceId(), meta.getMsgId(), 3);
            stats.put(key, item);
        }
        item.update(closure.getCtx().getResponseTimeMicros() / 1000000, closure.getRes().getRetCode(), closure.getCtx().getTimeUsedMillis());
    }

    void doStats(int logType, RpcClosure closure) {
        RpcMeta meta = closure.getCtx().getMeta();
        String key = typeToName(logType) + ":" + meta.getServiceId() + ":" + meta.getMsgId();
        StatItem item = stats.get(key);
        if (item == null) {
            item = new StatItem(meta.getServiceId(), meta.getMsgId(), logType);
            stats.put(key, item);
        }
        item.update(closure.getCtx().getResponseTimeMicros() / 1000000, closure.getRetCode(), closure.getCtx().getTimeUsedMillis());
    }

    void doStats(VirtualServiceClosure closure) {
        String key = typeToName(closure.getLogType()) + ":" + closure.getServiceId() + ":" + closure.getMsgId();
        StatItem item = stats.get(key);
        if (item == null) {
            item = new StatItem(closure.getServiceId(), closure.getMsgId(), closure.getLogType());
            stats.put(key, item);
        }
        item.update(closure.getResponseTimeMicros() / 1000000, closure.getRetCode(), closure.getTimeUsedMillis());
    }

    void doStatsCheckPoint(long time) {
        // print all stats <= time
        ReportRpcStatReq.Builder builder = ReportRpcStatReq.newBuilder();
        builder.setTimestamp(System.currentTimeMillis()).setHost(localIp).setApp(appName);
        for (StatItem item : stats.values()) {
            item.load(time, builder);
        }

        if (builder.getStatsCount() == 0) return;

        builder.setAppServiceId(TypeSafe.anyToInt(alarmPrefix));
        ReportRpcStatReq statsReq = builder.build();

        Logger log = LoggerFactory.getLogger("krpc.statslog");
        for (RpcStat stat : statsReq.getStatsList()) {
            log.info(toLogStr(stat));
        }

        // report to centered monitor server
        if (monitorClient != null)
            monitorClient.send(statsReq);
    }

    String toLogStr(RpcStat stat) {
        StringBuilder b = new StringBuilder();

        String timestamp = statsFormat.format(LocalDateTime.ofEpochSecond(stat.getTime(), 0, offset));

        b.append(timestamp);
        b.append(sep);
        b.append(typeToName(stat.getType()));
        b.append(sep);
        b.append(stat.getServiceId());
        b.append(sep);
        b.append(stat.getMsgId());
        b.append(sep);
        b.append(stat.getSuccess());
        b.append(sep);
        b.append(stat.getFailed());
        b.append(sep);
        b.append(stat.getTimeout());

        for (int i = 0; i < stat.getTimeUsedCount(); ++i) {
            b.append(sep);
            b.append(stat.getTimeUsed(i));
        }

        return b.toString();
    }

    void statsTimer() {
        long now = System.currentTimeMillis();
        long seconds = (now / 1000) % 60;
        if (seconds == 3) {
            final long time = ((now / 1000) / 60) * 60 - 60;

            try {
                statsPool.execute(new Runnable() {
                    public void run() {
                        try {
                            doStatsCheckPoint(time);
                        } catch (Exception e) {
                            log.error("asyncstats exception", e);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                log.error("asyncstats queue is full, e="+e.getMessage());
            }
        }
    }

    void reportSystemInfo() {

        logErrorCount();

        Map<String, Object> values = null;
        if (selfCheckHttpServer != null)
            values = selfCheckHttpServer.doDump();
        else {
            values = new LinkedHashMap<>();
            SystemDump.dumpSystemProperties(values);
        }

        if (monitorClient == null) return;

        ReportSystemInfoReq.Builder b = ReportSystemInfoReq.newBuilder();
        b.setTimestamp(System.currentTimeMillis()).setHost(localIp).setApp(appName);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = TypeSafe.anyToString(entry.getValue());
            b.addKvs(SystemInfoKV.newBuilder().setKey(key).setValue(value));
        }
        b.setAppServiceId(TypeSafe.anyToInt(alarmPrefix));
        ReportSystemInfoReq req = b.build();

        monitorClient.send(req);

        // reportMetaData();

        reportMetaDataCheck();
    }

    void addMap(ReportMetaReq.Builder b, int type,Map<String,String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            b.addInfo(MetaInfo.newBuilder().setType(type).setValue(key).setText(value));
        }
    }

    void reportMetaDataCheck() {
        LocalDateTime ldt = LocalDateTime.now();

        if( ldt.getHour() == 2 && ldt.getMinute() >= reportMetaMinute ) {
            long now = System.currentTimeMillis();
            if( now - lastReportMetaTime >= 23 * 60 * 1000L ) { // 一天只一次
                reportMetaData();
                lastReportMetaTime = now;
            }
        }
    }

    Map<String,String> getVsServiceNames() {
        Map<String,String> map = new LinkedHashMap<>();
        if( vsServiceNames != null ) {
            for(Integer serviceId: usedVsServiceIds) {
                String name = vsServiceNames.get(serviceId);
                if( name != null ) {
                    map.put(String.valueOf(serviceId), name);
                }
            }
        }
        return map;
    }

    Map<String,String> getVsMsgNames() {
        Map<String,String> map = new LinkedHashMap<>();
        map.putAll(usedVsMsgNames);
        return map;
    }

    void reportMetaData() {
        ReportMetaReq.Builder b = ReportMetaReq.newBuilder();
        b.setTimestamp(System.currentTimeMillis()).setHost(localIp).setApp(appName).setAppServiceId(TypeSafe.anyToInt(alarmPrefix));
        addMap(b,1,serviceMetas.getServiceMetaInfo());
        addMap(b,2,serviceMetas.getMsgMetaInfo());
        addMap(b,1,getVsServiceNames());
        addMap(b,2,getVsMsgNames());
        addMap(b,3,errorMsgConverter.getAllMsgs());
        ReportMetaReq req = b.build();
        monitorClient.send(req);
    }

    //原来方法
    public void reportAlarm(String type, String msg) {
        reportAlarm(type, msg, "", "");

    }

    void reportAlarm() {
        if (monitorClient == null) return;

        HashMap<String, AlarmInfoInWait> stats;

        alarmLock.lock();
        try {
            stats = waitAlarms;
            waitAlarms = new HashMap<>();
        } finally {
            alarmLock.unlock();
        }

        if (stats.size() == 0) return;

        ReportAlarmReq.Builder b = ReportAlarmReq.newBuilder();
        b.setTimestamp(System.currentTimeMillis()).setHost(localIp).setApp(appName);

        for (AlarmInfoInWait info : stats.values()) {
            b.addInfo(AlarmInfo.newBuilder().setTime(info.timestamp / 1000).setType(info.type).setMsg(info.msg)
                    .setCount(info.count).setTarget(info.target).setTargetAddrs(info.targetAddrs));
        }

        b.setAppServiceId(TypeSafe.anyToInt(alarmPrefix));
        ReportAlarmReq req = b.build();
        monitorClient.send(req);
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }

    public LogFormatter getLogFormatter() {
        return logFormatter;
    }

    public void setLogFormatter(LogFormatter logFormatter) {
        this.logFormatter = logFormatter;
    }

    public int getLogThreads() {
        return logThreads;
    }

    public void setLogThreads(int logThreads) {
        this.logThreads = logThreads;
    }

    public int getLogQueueSize() {
        return logQueueSize;
    }

    public void setLogQueueSize(int logQueueSize) {
        this.logQueueSize = logQueueSize;
    }

    public int getStatsQueueSize() {
        return statsQueueSize;
    }

    public void setStatsQueueSize(int statsQueueSize) {
        this.statsQueueSize = statsQueueSize;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public boolean isAccessLog() {
        return accessLog;
    }

    public void setAccessLog(boolean accessLog) {
        this.accessLog = accessLog;
    }

    public List<MonitorPlugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<MonitorPlugin> plugins) {
        this.plugins = plugins;
    }

    public boolean getPrintOriginalMsgName() {
        return printOriginalMsgName;
    }

    public void setPrintOriginalMsgName(boolean printOriginalMsgName) {
        this.printOriginalMsgName = printOriginalMsgName;
    }

    public SelfCheckHttpServer getSelfCheckHttpServer() {
        return selfCheckHttpServer;
    }

    public void setSelfCheckHttpServer(SelfCheckHttpServer selfCheckHttpServer) {
        this.selfCheckHttpServer = selfCheckHttpServer;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }


    @Override
    public void healthCheck(List<HealthStatus> list) {
        if (monitorClient == null) return;
        if (lastErrorCount > 0) {
            String alarmId = getAlarmId(Alarm.ALARM_TYPE_MONITOR);
            list.add(new HealthStatus(alarmId, false, "cannot connect to monitor service", "monitor", serverAddr));
        }
    }

    void logErrorCount() {
        if (monitorClient == null) return;
        lastErrorCount = monitorClient.getErrorCount();
        if (lastErrorCount > 0) {
            String alarmId = getAlarmId(Alarm.ALARM_TYPE_MONITOR);
            String s = "alarm type=" + alarmId + ",msg=cannot connect to monitor service [target=monitor,addrs=" + serverAddr + "]";
            log.warn(s);
        }
    }

    @Override
    public String getAlarmPrefix() {
        return alarmPrefix;
    }

    @Override
    public void alarm4rpc(String alarmId, String msg, String target, String addrs) {
        reportAlarm(alarmId, msg, target, addrs);
    }

    @Override
    public String getAlarmId(String type) {
        if( type.equals(Alarm.ALARM_TYPE_APM) ) {
            return "3" + Alarm.ALARM_TYPE_APM;
        }
        if( type.equals(Alarm.ALARM_TYPE_APMCFG) ) {
            return "3" + Alarm.ALARM_TYPE_APMCFG;
        }
        return alarmPrefix + type;
    }

    @Override
    @Deprecated
    public void alarm(String type, String msg) {
        String alarmId = getAlarmId(type);
        reportAlarm(alarmId, msg, "", "");
    }

    @Override
    public void alarm(String type, String msg, String target, String addrs) {
        String alarmId = getAlarmId(type);
        reportAlarm(alarmId, msg, target, addrs);
    }

    public void setSelfCheckPort(int selfCheckPort, int stdSelfCheckPort) {
        String s = stdSelfCheckPort > 0 ? String.valueOf(stdSelfCheckPort) : String.valueOf(selfCheckPort);
        if (s.length() == 4 || s.length() == 5) {
            alarmPrefix = s.substring(1);
        }
    }

    public void reportAlarm(String type, String msg, String target, String targetAddrs) {
        String traceId = null;
        ServerContextData ctx = ServerContext.get();
        if (ctx != null && ctx.getMeta() != null) {
            traceId = ctx.getMeta().getTrace().getTraceId();
        }
        String s = "alarm type=" + type + ",msg=" + msg;
        if (target != null && !target.isEmpty()) s += ",target=" + target;
        if (targetAddrs != null && !targetAddrs.isEmpty()) s += ",addrs=" + targetAddrs;
        if (traceId != null && !traceId.isEmpty()) s += ",traceId=" + traceId;
        log.warn(s);

        if (monitorClient == null) return;

        alarmLock.lock();
        try {
            String key = type + ":" + msg + ":" + target + ":" + targetAddrs;
            AlarmInfoInWait w = waitAlarms.get(key);
            if (w == null) {
                w = new AlarmInfoInWait(type, msg, target, targetAddrs);
                waitAlarms.put(key, w);
            } else {
                w.count++;
            }
        } finally {
            alarmLock.unlock();
        }

    }
}
