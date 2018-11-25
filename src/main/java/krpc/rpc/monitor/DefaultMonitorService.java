package krpc.rpc.monitor;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultMonitorService implements MonitorService, WebMonitorService, InitClose, Alarm, HealthPlugin {

    static Logger log = LoggerFactory.getLogger(DefaultMonitorService.class);

    static final String sep = ",   ";
    static public int[] timeSpans = new int[]{10, 25, 50, 100, 250, 500, 1000, 3000};

    String tags;

    String appName;
    String localIp;
    ServiceMetas serviceMetas;
    RpcCodec codec;

    int logThreads = 1;
    int logQueueSize = 10000;
    boolean accessLog = true;

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

    static class AlarmInfoInWait {
        String type;
        String msg;
        int count = 1;
        long timestamp = System.currentTimeMillis();

        AlarmInfoInWait(String type, String msg) {
            this.type = type;
            this.msg = msg;
        }
    }

    private ReentrantLock alarmLock = new ReentrantLock();
    private HashMap<String,AlarmInfoInWait> waitAlarms = new HashMap<>();

    DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    DateTimeFormatter statsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ZoneOffset offset = OffsetDateTime.now().getOffset();

    ArrayList<Object> resources = new ArrayList<Object>();

    public DefaultMonitorService(RpcCodec codec, ServiceMetas serviceMetas) {
        this.codec = codec;
        this.serviceMetas = serviceMetas;
    }

    void initTagsMap() {
        String[] ss = tags.split("#");
        for(String s:ss) {
            int p = s.indexOf(":");
            String key = s.substring(0,p);
            String value = s.substring(p+1);
            String[] msgIds = value.split(",");
            for(String msgId:msgIds) {
                String v = tagsMap.get(msgId);
                if( v == null ) v = key;
                else v = v + "," + key;
                tagsMap.put(msgId,v);
            }
        }
    }

    public void init() {

        localIp = IpUtils.localIp();

        if( !isEmpty(tags) ) {
            initTagsMap();
        }

        timer = new Timer("krpc_asyncstats_timer");


        if (serverAddr != null && serverAddr.length() > 0) {
            monitorClient = new MonitorClient(serverAddr, codec, serviceMetas, timer);
        }

        timer.schedule(new TimerTask() {
            public void run() {
                statsTimer();
            }
        }, 10000, 1000);
        timer.schedule(new TimerTask() {
            public void run() {
                reportSystemInfo();
            }
        }, 60000, 60000);

        if (monitorClient != null) {

            timer.schedule(new TimerTask() {
                public void run() {
                    reportAlarm();
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

    public void webReqDone(WebClosure closure) {

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

    public void reqDone(final RpcClosure closure) {

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

    public void callDone(RpcClosure closure) {

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
        String key = meta.getServiceId()+"."+meta.getMsgId();
        String tags = tagsMap.get(key);
        return tags;
    }

    private void appendVars(ServerContextData serverCtx, StringBuilder extraInfo) {
        Map<String, Object> attrs = serverCtx.getAttributes();
        if( attrs == null ) return;
        for(Map.Entry<String,Object> entry:attrs.entrySet()) {
            String key = entry.getKey();
            if( key.startsWith("var:")) {
                key = key.substring(4);
                String value = TypeSafe.anyToString( entry.getValue() );
                if( extraInfo.length() > 0  ) extraInfo.append("^");
                extraInfo.append(key).append(":").append(value);
            }
        }
    }

    private void appendTimeUsed(ServerContextData serverCtx, StringBuilder extraInfo) {
        TraceContext tc = serverCtx.getTraceContext();
        if( tc == null ) return;
        if( !(tc instanceof DefaultTraceContext) ) return;
        DefaultTraceContext dtc = (DefaultTraceContext)tc;
        String timeUsedStr = dtc.getTimeUsedStr();
        if( isEmpty(timeUsedStr) ) return;
        if( extraInfo.length() > 0  ) extraInfo.append("^");
        extraInfo.append(timeUsedStr);
    }

    private void appendTimeUsedInQueue(ServerContextData serverCtx, StringBuilder extraInfo) {
        if( extraInfo.length() > 0  ) extraInfo.append("^");
        extraInfo.append( "Q:"+ serverCtx.getWaitInQueueMicros() );
    }

    private void appendTags(RpcMeta meta, StringBuilder extraInfo) {
        String tags = getTags(meta);
        if(isEmpty(tags)) return;
        if( extraInfo.length() > 0  ) extraInfo.append("^");
        extraInfo.append( "tags:"+ tags );
    }

    private void appendRpcTags(ServerContextData serverCtx, StringBuilder extraInfo) {
        String expDivIds = serverCtx.getTraceContext().getTagForRpc("expDivIds");
        if(expDivIds != null && !expDivIds.isEmpty() ) {
            if( extraInfo.length() > 0  ) extraInfo.append("^");
            extraInfo.append("expDivIds:" + expDivIds);
        }
    }

    String getLogStr(RpcClosure closure) {
        StringBuilder b = new StringBuilder();
        RpcContextData ctx = closure.getCtx();
        RpcMeta meta = ctx.getMeta();
        RpcMeta.Trace trace = meta.getTrace();
        String spanId = trace.getSpanId();
        StringBuilder extraInfo = new StringBuilder();
        if (ctx instanceof ServerContextData) {
            ServerContextData serverCtx = ((ServerContextData) ctx);
            appendTimeUsedInQueue(serverCtx, extraInfo);
            appendTimeUsed(serverCtx, extraInfo);
            appendTags(meta, extraInfo);
            appendVars(serverCtx, extraInfo);
            appendRpcTags(serverCtx, extraInfo);
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
        if( printOriginalMsgName )
            serviceName = serviceMetas.getOriginalName(meta.getServiceId(), meta.getMsgId());
        else
            serviceName = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
        b.append(serviceName);
        b.append(sep);
        b.append(closure.getRetCode());
        b.append(sep);
        b.append(ctx.getTimeUsedMicros());
        b.append(sep);
        String reqStr = closure.getReq() == null ? "" : logFormatter.toLogStr(closure.getReq());
        if (reqStr.indexOf(sep) >= 0) reqStr = reqStr.replace(sep, ",");
        b.append(reqStr);
        b.append(sep);
        String resStr = closure.getRes() == null ? "" : logFormatter.toLogStr(closure.getRes());
        if (resStr.indexOf(sep) >= 0) resStr = resStr.replace(sep, ",");
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

        if (ctx instanceof ServerContextData) {
            ServerContextData serverCtx = ((ServerContextData) ctx);

            appendTimeUsedInQueue(serverCtx, extraInfo);
            appendTimeUsed(serverCtx, extraInfo);
            appendTags(meta, extraInfo);
            appendVars(serverCtx, extraInfo);
            appendRpcTags(serverCtx, extraInfo);
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
        if( printOriginalMsgName )
            serviceName = serviceMetas.getOriginalName(meta.getServiceId(), meta.getMsgId());
        else
            serviceName = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
        b.append(serviceName);
        b.append(sep);
        b.append(closure.getRes().getRetCode());
        b.append(sep);
        b.append(ctx.getTimeUsedMicros());
        b.append(sep);
        String clientIp = ctx.getClientIp();
        b.append("httpClientIp:").append(clientIp).append("^");
        String reqStr = closure.getReq() == null ? "" : logFormatter.toLogStr(closure.getReq());
        if (reqStr.indexOf(sep) >= 0) reqStr = reqStr.replace(sep, ",");
        b.append(reqStr);
        b.append(sep);
        String resStr = closure.getRes() == null ? "" : logFormatter.toLogStr(closure.getRes());
        if (resStr.indexOf(sep) >= 0) resStr = resStr.replace(sep, ",");
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
            if (retCode < 0) item.failed++;
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
                log.error("asyncstats queue is full");
            }
        }
    }

    void reportSystemInfo() {

        Map<String,Object> values = null;
        if( selfCheckHttpServer != null )
            values = selfCheckHttpServer.doDump();
        else {
            values = new LinkedHashMap<>();
            SystemDump.dumpSystemProperties(values);
        }

        if (monitorClient == null) return;

        ReportSystemInfoReq.Builder b = ReportSystemInfoReq.newBuilder();
        b.setTimestamp(System.currentTimeMillis()).setHost(localIp).setApp(appName);
        for(Map.Entry<String,Object> entry:values.entrySet()) {
            String key = entry.getKey();
            String value = TypeSafe.anyToString(entry.getValue());
            b.addKvs(SystemInfoKV.newBuilder().setKey(key).setValue(value));
        }
        b.setAppServiceId(TypeSafe.anyToInt(alarmPrefix));
        ReportSystemInfoReq req = b.build();

        monitorClient.send(req);
    }

    public void reportAlarm(String type,String msg) {
        if (monitorClient == null) return;

        alarmLock.lock();
        try {
            AlarmInfoInWait w = waitAlarms.get(type);
            if( w == null ) {
                w = new AlarmInfoInWait(type,msg);
                waitAlarms.put(type,w);
            } else {
                w.count ++;
            }
        } finally {
            alarmLock.unlock();
        }

    }

    void reportAlarm() {
        if (monitorClient == null) return;

        HashMap<String,AlarmInfoInWait> stats;

        alarmLock.lock();
        try {
            stats = waitAlarms;
            waitAlarms = new HashMap<>();
        } finally {
            alarmLock.unlock();
        }

        if( stats.size() == 0 ) return;

        ReportAlarmReq.Builder b = ReportAlarmReq.newBuilder();
        b.setTimestamp(System.currentTimeMillis()).setHost(localIp).setApp(appName);

        for(AlarmInfoInWait info:stats.values()) {
            b.addInfo(AlarmInfo.newBuilder().setTime(info.timestamp/1000).setType(info.type).setMsg(info.msg).setCount(info.count));
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
        if( monitorClient == null ) return;
        int n = monitorClient.getErrorCount();
        if( n > 0 ) {
            String alarmId = getAlarmId(Alarm.ALARM_TYPE_MONITOR);
            list.add(new HealthStatus(alarmId, false, "cannot connect to monitor service"));
        }
    }

    @Override
    public String getAlarmId(String type) {
        return alarmPrefix + type;
    }

    @Override
    public void alarm(String type, String msg) {
        String alarmId = getAlarmId(type);
        reportAlarm(alarmId,msg);
    }

    public void setSelfCheckPort(int selfCheckPort) {
        String s = String.valueOf(selfCheckPort);
        if( s.length() == 4 ) {
            alarmPrefix = s.substring(1);
        }
    }

}
