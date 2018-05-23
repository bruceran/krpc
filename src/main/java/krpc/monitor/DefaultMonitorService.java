package krpc.monitor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.core.InitClose;
import krpc.core.InitCloseUtils;
import krpc.core.MonitorService;
import krpc.core.RetCodes;
import krpc.core.RpcClosure;
import krpc.core.RpcCodec;
import krpc.core.RpcContextData;
import krpc.core.ServiceMetas;
import krpc.core.proto.RpcMeta;
import krpc.monitor.proto.ReportRpcStatReq;
import krpc.monitor.proto.RpcStat;
import krpc.util.IpUtils;
import krpc.util.NamedThreadFactory;
import krpc.web.WebClosure;
import krpc.web.WebMonitorService;

public class DefaultMonitorService implements MonitorService, WebMonitorService, InitClose {

	static Logger log = LoggerFactory.getLogger(DefaultMonitorService.class);
	
	static final String sep = ",   ";

	String appName;
	ServiceMetas serviceMetas;
	RpcCodec codec;

    int logThreads = 1;
    int logQueueSize = 10000;
    LogFormatter logFormatter = new SimpleLogFormatter();
    ThreadPoolExecutor logPool;
    ConcurrentHashMap<String,Logger> reqLogMap = new ConcurrentHashMap<String,Logger>();
    ConcurrentHashMap<String,Logger> webLogMap = new ConcurrentHashMap<String,Logger>();
    ConcurrentHashMap<String,Logger> callLogMap = new ConcurrentHashMap<String,Logger>();
    
    static public int[] timeSpans = new int[] {10,25,50,100,250,500,1000,3000};
    int statsQueueSize = 10000;
    ThreadPoolExecutor statsPool;
    HashMap<String,StatItem> stats = new HashMap<>();

    Timer timer;
	
    String serverAddr;
    MonitorClient monitorClient;

    DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    DateTimeFormatter statsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ZoneOffset offset = OffsetDateTime.now().getOffset();

    ArrayList<Object> resources = new ArrayList<Object>();

	public DefaultMonitorService(RpcCodec codec,ServiceMetas serviceMetas) {
		this.codec = codec;
		this.serviceMetas = serviceMetas;
	}

    public void init() {

        timer = new Timer("asyncstatstimer");
        timer.schedule( new TimerTask() {
            public void run() {
            	statsTimer();
            }
        },  10000, 1000 );
    	
    	if( serverAddr != null && serverAddr.length() > 0 ) {
    		monitorClient = new MonitorClient(serverAddr,codec,serviceMetas,timer);
    	}
    	
    	resources.add(logFormatter);
    	resources.add(monitorClient);
    	InitCloseUtils.init(resources);
    	
        ThreadFactory logThreadFactory = new NamedThreadFactory("asynclog");
        logPool = new ThreadPoolExecutor(logThreads, logThreads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(logQueueSize),logThreadFactory);
        logPool.prestartAllCoreThreads();
        
        ThreadFactory statsThreadFactory = new NamedThreadFactory("asyncstats");
        statsPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(statsQueueSize),statsThreadFactory);
        statsPool.prestartAllCoreThreads();
        
        log.info("monitor service started");
    }

    public void close() {
	
	    timer.cancel();
	
	    statsPool.shutdown();
	    logPool.shutdown();
	
    	InitCloseUtils.close(resources);
	    
	    log.info("monitor service stopped");
	}

    public void webReqDone(WebClosure closure) {

        try{
            logPool.execute( new Runnable() {
                public void run() {
                    try {
                    	doLog(true,closure);
                    } catch(Exception e) {
                        log.error("asynclog exception",e);
                    }
                }
            });
        } catch(RejectedExecutionException e) {
        	log.error("asynclog queue is full");
        }
        
        try{
        	statsPool.execute( new Runnable() {
                public void run() {
                    try {
                    	doStats(closure);
                    } catch(Exception e) {
                        log.error("asyncstats exception",e);
                    }
                }
            });
        } catch(RejectedExecutionException e) {
        	log.error("asyncstats queue is full");
        }

    }

	@Override
	public void webReqStart(WebClosure closure) {
	}

	@Override
	public void reqStart(RpcClosure closure) {
	}

	@Override
	public void callStart(RpcClosure closure) {
	}
	
    public void reqDone(final RpcClosure closure) {
    	
        try{
            logPool.execute( new Runnable() {
                public void run() {
                    try {
                    	doLog(true,closure);
                    } catch(Exception e) {
                        log.error("asynclog exception",e);
                    }
                }
            });
        } catch(RejectedExecutionException e) {
        	log.error("asynclog queue is full");
        }
        
        try{
        	statsPool.execute( new Runnable() {
                public void run() {
                    try {
                    	doStats(1,closure);
                    } catch(Exception e) {
                        log.error("asyncstats exception",e);
                    }
                }
            });
        } catch(RejectedExecutionException e) {
        	log.error("asyncstats queue is full");
        }

    }
    
    public void callDone(RpcClosure closure) {
        try{
            logPool.execute( new Runnable() {
                public void run() {
                    try {
                    	doLog(false,closure);
                    } catch(Exception e) {
                        log.error("asynclog log exception",e);
                    }
                }
            });
        } catch(RejectedExecutionException e) {
        	log.error("asynclog queue is full");
        }    
        
        try{
        	statsPool.execute( new Runnable() {
                public void run() {
                    try {
                    	doStats(2,closure);
                    } catch(Exception e) {
                        log.error("asyncstats exception",e);
                    }
                }
            });
        } catch(RejectedExecutionException e) {
        	log.error("asyncstats queue is full");
        }
 
    }
    
    void doLog(boolean isReqLog, RpcClosure closure) {
    	RpcMeta meta = closure.getCtx().getMeta();
        Logger log = null;
        if( isReqLog ) log = getReqLog(meta.getServiceId(),meta.getMsgId());
        else log = getCallLog(meta.getServiceId(),meta.getMsgId());
        if( !log.isInfoEnabled() ) return;
    	String msg = getLogStr(closure);
    	log.info(msg);    	
    }

    void doLog(boolean isReqLog, WebClosure closure) {
    	RpcMeta meta = closure.getCtx().getMeta();
        Logger log = getWebLog(meta.getServiceId(),meta.getMsgId());
        if( !log.isInfoEnabled() ) return;
    	String msg = getLogStr(closure);
    	log.info(msg);    	
    }
    
    Logger getWebLog(int serviceId,int msgId)  {
    	String key = serviceId+"."+msgId;
    	Logger log = webLogMap.get(key);
        if( log != null ) return log;
        String key2 = "krpc.weblog."+key;
        log = LoggerFactory.getLogger(key2);
        webLogMap.put(key,log);
        return log;
    }
    
    Logger getReqLog(int serviceId,int msgId)  {
    	String key = serviceId+"."+msgId;
    	Logger log = reqLogMap.get(key);
        if( log != null ) return log;
        String key2 = "krpc.reqlog."+key;
        log = LoggerFactory.getLogger(key2);
        reqLogMap.put(key,log);
        return log;
    }

    Logger getCallLog(int serviceId,int msgId) {
        String key = serviceId+"."+msgId;
        Logger log = callLogMap.get(key);
        if( log != null ) return log;
        String key2 = "krpc.calllog."+key;
        log = LoggerFactory.getLogger(key2);
        callLogMap.put(key,log);
        return log;
    }
    
    String getLogStr(RpcClosure closure) {
    	StringBuilder b = new StringBuilder();
    	RpcContextData ctx = closure.getCtx();
    	RpcMeta meta = ctx.getMeta();
    	
    	String timestamp =  logFormat.format( LocalDateTime.ofEpochSecond(ctx.getResponseTime()/1000,(int)((ctx.getResponseTime()%1000)*1000000),offset) ); 
    	b.append(timestamp);
    	b.append(sep);
    	b.append(ctx.getConnId());
    	b.append(sep);
    	b.append(meta.getSequence());
    	b.append(sep);
    	b.append(meta.getTraceId());
    	b.append(sep);
    	b.append(meta.getSpanId());
    	b.append(sep);
    	b.append(meta.getServiceId());
    	b.append(sep);
    	b.append(meta.getMsgId());
    	b.append(sep);
    	String serviceName = serviceMetas.getName(meta.getServiceId(),meta.getMsgId());
    	b.append(serviceName);
    	b.append(sep);
    	b.append(closure.getRetCode());
    	b.append(sep);
    	b.append(ctx.timeMicrosUsed());
    	b.append(sep);
    	String reqStr = closure.getReq() == null ? "" : logFormatter.toLogStr(true,closure.getReq());
    	if( reqStr.indexOf(sep) >= 0 ) reqStr = reqStr.replace(sep, ",");
    	b.append(reqStr);
    	b.append(sep);
    	String resStr = closure.getRes() == null ? "" : logFormatter.toLogStr(false,closure.getRes());
    	if( resStr.indexOf(sep) >= 0 ) resStr = resStr.replace(sep, ",");
    	b.append(resStr);

    	return b.toString();
    }

    String getLogStr(WebClosure closure) {
    	StringBuilder b = new StringBuilder();
    	RpcContextData ctx = closure.getCtx();
    	RpcMeta meta = ctx.getMeta();

    	String timestamp =  logFormat.format( LocalDateTime.ofEpochSecond(ctx.getResponseTime()/1000,(int)((ctx.getResponseTime()%1000)*1000000),offset) ); 
    	b.append(timestamp);
    	b.append(sep);
    	b.append(ctx.getConnId());
    	b.append(sep);
    	b.append(meta.getSequence());
    	b.append(sep);
    	b.append(meta.getTraceId());
    	b.append(sep);
    	b.append(meta.getSpanId());
    	b.append(sep);
    	b.append(meta.getServiceId());
    	b.append(sep);
    	b.append(meta.getMsgId());
    	b.append(sep);
    	String serviceName = serviceMetas.getName(meta.getServiceId(),meta.getMsgId());
    	b.append(serviceName);
    	b.append(sep);
    	b.append(closure.getRes().getRetCode());
    	b.append(sep);
    	b.append(ctx.timeMicrosUsed());
    	b.append(sep);
    	String reqStr = closure.getReq() == null ? "" : logFormatter.toLogStr(true,closure.getReq());
    	if( reqStr.indexOf(sep) >= 0 ) reqStr = reqStr.replace(sep, ",");
    	b.append(reqStr);
    	b.append(sep);
    	String resStr = closure.getRes() == null ? "" : logFormatter.toLogStr(false,closure.getRes());
    	if( resStr.indexOf(sep) >= 0 ) resStr = resStr.replace(sep, ",");
    	b.append(resStr);

    	return b.toString();
    }
    
    static class MinuteItem {
		long curTime;
		int success;
		int failed;
		int timeout;
		int[] timeSpanCount = new int[DefaultMonitorService.timeSpans.length+1];
		
		MinuteItem(long time) { curTime = time; }
    }
    
    static class StatItem {
    	
    	int serviceId;
    	int msgId;
    	int logType; // 1=req 2=call 3=web
    	long minTime;
    	
    	LinkedList<MinuteItem> items = new LinkedList<MinuteItem>();

    	StatItem(int serviceId, int msgId,int logType) {
			this.serviceId = serviceId;
			this.msgId = msgId;
			this.logType = logType;
		}

		void update(long responseTime, int retCode, long timeUsed) {
			long time = (responseTime / 1000 / 60) * 60;
			MinuteItem item = null;
			if( items.isEmpty() ) {
				item = new MinuteItem(time);
				items.addFirst(item);
			}  else {
				item = findItem(time);
				if( item == null ) return;
			}

			if( retCode == 0 ) item.success++;
			if( retCode < 0 ) item.failed++;
			if( RetCodes.isTimeout(retCode) ) item.timeout++;
			int index = timeToIndex(timeUsed);
			item.timeSpanCount[index]++;
		}

		MinuteItem findItem(long time) {
			for(MinuteItem i:items) {
				if( time == i.curTime ) {
					return i;
				}
			}

			if( time > items.getFirst().curTime ) {
				MinuteItem item = new MinuteItem(time);
				items.addFirst( item );
				return item;
			} else if( time > minTime ){
				MinuteItem item = new MinuteItem(time);
				items.addLast(item);
				return item;
			} else {
				return null;
			}
		}
		
		int timeToIndex(long timeUsed) {
	        int i = 0;
	        while(i<DefaultMonitorService.timeSpans.length) {
	            if( timeUsed <= DefaultMonitorService.timeSpans[i] ) return i;
	            i += 1;
	        }
	        return DefaultMonitorService.timeSpans.length;
	    }
		
		void load(long time, ReportRpcStatReq.Builder builder) {
			
			Iterator<MinuteItem> iter = items.iterator();
			while( iter.hasNext() ) {
				MinuteItem i = iter.next();
				if( i.curTime <= time ) {
					RpcStat.Builder b = RpcStat.newBuilder();
					b.setType(logType).setTime(i.curTime).setServiceId(serviceId).setMsgId(msgId);
					b.setSuccess(i.success).setFailed(i.failed).setTimeout(i.timeout);
					for(int k=0;k<i.timeSpanCount.length;++k)
						b.addTimeUsed(i.timeSpanCount[k]);
					builder.addStats(b.build());
					iter.remove();
				}
			}
			
			minTime = time;
		}
    }

    void doStats(WebClosure closure) {
    	RpcMeta meta = closure.getCtx().getMeta();
    	String key = "webstats:"+meta.getServiceId()+":"+meta.getMsgId();
    	StatItem item = stats.get(key);
    	if( item == null ) {
    		item = new StatItem(meta.getServiceId(),meta.getMsgId(),3);
    		stats.put(key, item);
    	}
    	item.update(closure.getCtx().getResponseTime(),closure.getRes().getRetCode(), closure.getCtx().timeMillisUsed());
    }
    
    void doStats(int logType, RpcClosure closure) {
    	RpcMeta meta = closure.getCtx().getMeta();
    	String key = (logType == 1?"reqstats":"callstats")+":"+meta.getServiceId()+":"+meta.getMsgId();
    	StatItem item = stats.get(key);
    	if( item == null ) {
    		item = new StatItem(meta.getServiceId(),meta.getMsgId(),logType);
    		stats.put(key, item);
    	}
    	item.update(closure.getCtx().getResponseTime(),closure.getRetCode(), closure.getCtx().timeMillisUsed());
    }

    void doStatsCheckPoint(long time) {
    	 // print all stats <= time
    	ReportRpcStatReq.Builder builder = ReportRpcStatReq.newBuilder();
    	builder.setTimestamp(System.currentTimeMillis()).setHost(IpUtils.localIp()).setApp(appName);
    	for(StatItem item : stats.values() ) {
    		item.load(time, builder);
    	}
    	
    	if( builder.getStatsCount() == 0 ) return;
    	
    	ReportRpcStatReq statsReq = builder.build();

    	Logger log = LoggerFactory.getLogger("krpc.statslog");
    	for(RpcStat stat:statsReq.getStatsList()) {
    		log.info(toLogStr(stat));
    	}
    	
    	// report to centered monitor server
    	if( monitorClient != null )
    		monitorClient.send(statsReq);
    }
    
    String toLogStr(RpcStat stat) {
    	StringBuilder b = new StringBuilder();
    	
    	String timestamp =  statsFormat.format( LocalDateTime.ofEpochSecond(stat.getTime(),0,offset) ); 
    	
    	b.append(timestamp);
    	b.append(sep);
    	b.append( stat.getType() == 1 ? "reqstats" : stat.getType() == 2 ? "callstats" : "webstats" );
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

    	for(int i=0;i<stat.getTimeUsedCount();++i) {
        	b.append(sep);
        	b.append(stat.getTimeUsed(i));
    	}

    	return b.toString();
    }
    
    void statsTimer() {
    	long now = System.currentTimeMillis();
		long seconds = (now / 1000) % 60 ;
		if( seconds == 3 ) {
        	final long time = ((now / 1000) / 60) * 60 - 60;

	        try{
	        	statsPool.execute( new Runnable() {
	                public void run() {
	                    try {
	                    	doStatsCheckPoint(time);
	                    } catch(Exception e) {
	                        log.error("asyncstats exception",e);
	                    }
	                }
	            });
	        } catch(RejectedExecutionException e) {
	        	log.error("asyncstats queue is full");
	        }			
		}
    }
	
	boolean isEmpty(String s) {
		return s == null || s.isEmpty() ;
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


}
