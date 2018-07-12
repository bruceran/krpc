package krpc.trace.adapter;

import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.common.Json;
import krpc.common.NamedThreadFactory;
import krpc.common.Plugin;
import krpc.httpclient.DefaultHttpClient;
import krpc.httpclient.HttpClientReq;
import krpc.httpclient.HttpClientRes;
import krpc.rpc.core.proto.RpcMeta;
import krpc.rpc.util.IpUtils;
import krpc.trace.Event;
import krpc.trace.Span;
import krpc.trace.SpanIds;
import krpc.trace.Trace;
import krpc.trace.TraceIds;
import krpc.trace.TraceAdapter;
import krpc.trace.TraceContext;

/*
curl -i -X GET "http://localhost:10800/agent/jetty"
["localhost:12800/"]
curl -i -X GET "http://localhost:10800/agent/gRPC"
["localhost:11800"]
curl -X POST "http://localhost:12800/application/register" -H "Content-Type: application/json"  --data '["boot2"]'
[{"c":"boot2","i":8}]
i is always 0 for the first time

curl -X POST "http://localhost:12800/instance/register" -H "Content-Type: application/json" --data '{"ai":8,"au":"ff845629c6164d448bdc4785aafbaaf2","rt":1234567890,"oi":{"osName":"linux","hostname":"localhost","processNo":11211,"ipv4s":["127.0.0.1"]} }
{"ai":8,"ii":2}
ii is always 0 for the first time

$curl "http://localhost:9200/_search?pretty"
$curl "http://localhost:9200/application/_search?pretty"
$curl "http://localhost:9200/instance/_search?pretty"

heartbeat not supported by skywalking's jetty provider
curl -X POST "http://localhost:12800/instance/heartbeat" -H "Content-Type: application/json" --data '{"ii":2,"ht":1234567890}'

/segments
curl -i -X POST "http://localhost:12800/segments" -H "Content-Type: application/json" --data '[]'
HTTP/1.1 200 OK

*/
public class SkyWalkingTraceAdapter implements TraceAdapter,InitClose {

	static Logger log = LoggerFactory.getLogger(SkyWalkingTraceAdapter.class);
	
	int queueSize = 1000;
	int retryCount = 3;
	int retryInterval = 1000;

	DefaultHttpClient hc;
	NamedThreadFactory threadFactory = new NamedThreadFactory("skywalking_report");
	ThreadPoolExecutor pool;

	String[] queryAddrs;
	int queryAddrsIndex = 0;

	String[] serverAddrs = new String[0];
	int serverAddrIndex = -1;
	Object serverAddrSyncObj = new Object();
	
	String appName;
	OsInfo osInfo;
	String instanceUuid = "";
	
	int appId = 0; // ai, got from skywalking server
	volatile int instanceId = 0; // ii, got from skywalking server
	Timer timer;

	AtomicInteger seq = new AtomicInteger(0);
	
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
	
		queryAddrs = params.get("server").split(",");

		String s = params.get("queueSize");
		if( !isEmpty(s) ) queueSize = Integer.parseInt(s);
		
		s = params.get("retryCount");
		if( !isEmpty(s) ) retryCount = Integer.parseInt(s);

		s = params.get("retryInterval");
		if( !isEmpty(s) ) retryInterval = Integer.parseInt(s);
	}
	
	public void init() {
		appName = Trace.getAppName();
		
		getOsInfo();
		
		instanceUuid = uuid();
		
		hc = new DefaultHttpClient();
		hc.init();
		pool = new ThreadPoolExecutor(1,1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
		
		boolean ok = register();
		if(!ok) {
	        timer = new Timer("skywalkingtimer");
	        timer.schedule( new TimerTask() {
	            public void run() {
	            	retryRegister();
	            }
	        },  3000, 3000 );			
		} else {
			startQueryRoutesTimer();
		}		
	}

	public void close() {
		if( hc == null ) return;
		
		if( timer != null ) {
			timer.cancel();
			timer = null;
		}
		pool.shutdownNow();
		pool = null;
		hc.close();
		hc = null;
	}
	
	void startQueryRoutesTimer() {
        timer = new Timer("skywalkingtimer");
        timer.schedule( new TimerTask() {
            public void run() {
            	heartBeatAndQueryRoutes();
            }
        },  60000, 60000 );			
	}
		
	void retryRegister() {
		boolean ok = register();	
		if(ok) {
			timer.cancel();
			startQueryRoutesTimer();
		}
	}
	
	boolean register() {
		boolean ok = queryRoutes();
		if(!ok) { return false; }
		ok = registerAppId(0);
		if(!ok) { return false; }
		return registerInstanceId(0);		
	}
	
	void heartBeatAndQueryRoutes() {
		/*
				why skywalking always return failed ?
				
				in apm-collector\apm-collector-agent\agent-jetty\agent-jetty-provider\
				src\main\java\org\apache\skywalking\apm\collector\agent\jetty\provider\AgentModuleJettyProvider.java
				
				heartbeat handler not registered
		 */
		// heartBeat(); 
		queryRoutes();
	}	

	boolean queryRoutes() {

		String queryUrl = "http://%s/agent/jetty";
		String url = String.format(queryUrl, currentQueryAddr());
		HttpClientReq req = new HttpClientReq("GET",url);
		HttpClientRes res = hc.call(req);		
		if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
			List<Object> list = Json.toList(res.getContent());
			if( list == null || list.size() == 0 ) return false;
			List<String> addrs = new ArrayList<>();
			for(Object o:list) {
				String s = (String)o;
				if( s.endsWith("/") ) s = s.substring(0,s.length()-1);
				addrs.add(s);
			}
			setServerAddrs(addrs);
			return true;
		} else {
			nextQueryAddr();
			log.error("query skywalking routes failed, retCode="+res.getRetCode()+",content="+res.getContent());
			return false;
		}
	}
	
	boolean registerAppId(int count) {
		if( appId != 0 ) return true;
		
		String queryUrl = "http://%s/application/register";
		
		String url = String.format(queryUrl, currentServerAddr());
		String json = "[\""+appName+"\"]";
		HttpClientReq req = new HttpClientReq("POST",url).setContent(json);
		HttpClientRes res = hc.call(req);		
		if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
			List<Object> list = Json.toList(res.getContent());
			if( list == null || list.size() == 0 ) return false;
			
			Map<String,Object>  map = (Map<String,Object>)list.get(0);
			String c = (String)map.get("c");
			if( c == null || !c.equals(appName) ) return false;
			int i = (Integer)map.get("i");
			if( i == 0 ) {
				if( count == 0 ) {
					sleep(1000);
					return registerAppId(1);
				}
				return false;
			}
			appId = i;
			log.info("application id="+appId);
			return true;
		} else {
			nextServerAddr();
			log.error("register application failed, retCode="+res.getRetCode()+",content="+res.getContent());
			return false;
		}		
	}
	
	boolean registerInstanceId(int count) {
		if( instanceId != 0 ) return true;
		
		String queryUrl = "http://%s/instance/register";
		InstanceInfo info = new InstanceInfo();
		info.ai = appId;
		info.au = instanceUuid;
		info.rt = System.currentTimeMillis();
		info.oi = osInfo; // TODO skywalking received processNo is always 0, why? 
		
		String url = String.format(queryUrl, currentServerAddr());
		String json = Json.toJson(info);
		HttpClientReq req = new HttpClientReq("POST",url).setContent(json);
		HttpClientRes res = hc.call(req);		
		if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
			Map<String,Object> map = Json.toMap(res.getContent());
			if( map == null ) return false;
			Integer ai = (Integer)map.get("ai");
			if( ai == null || ai != appId ) return false;
			int ii = (Integer)map.get("ii");
			if( ii == 0 ) {
				if( count == 0 ) {
					sleep(1000);
					return registerInstanceId(1);
				}
				return false;
			}
			instanceId = ii;
			log.info("instance id="+instanceId);
			return true;
		} else {
			nextServerAddr();
			log.error("register instance failed, retCode="+res.getRetCode()+",content="+res.getContent());
			return false;
		}		
	}

	void sleep(int milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch(Exception e) {
		}
	}
	boolean heartBeat() {

		String queryUrl = "http://%s/instance/heartbeat";
		String url = String.format(queryUrl, currentServerAddr());
		Map<String,Object> info = new HashMap<>();
		info.put("ii", instanceId);
		info.put("ht", System.currentTimeMillis());
		String json = Json.toJson(info);
		HttpClientReq req = new HttpClientReq("POST",url).setContent(json);
		HttpClientRes res = hc.call(req);		
		if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
			log.info("heartbeat ok");
			return true;
		} else {
			nextServerAddr();
			log.info("heartbeat failed");
			return false;
		}		
	}
	
	public void send(TraceContext ctx, Span span) {
		if( instanceId == 0 ) return;
		
		try {
			pool.execute( new Runnable() {
				public void run() {
					try {
						report(ctx,span);
					} catch(Exception e) {
						log.error("skywalking report exception",e);
					}
				}
			});
		} catch(Exception e) {
			log.error("skywalking report queue is full");
		}
	}
	
	void report(TraceContext ctx, Span span) {
		String json = convert(ctx,span);
		
System.out.println("segments json="+json);		

		String postUrl = "http://%s/segments";
		String url = String.format(postUrl, currentServerAddr());
		HttpClientReq req = new HttpClientReq("POST",url).setContent(json).setGzip(true);
		int i=0;
		String lastContent = null;
		while(true) {
			HttpClientRes res = hc.call(req);
			if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
				log.info("report span ok, content="+lastContent);
				return;
			}
			lastContent = res.getContent();
			nextServerAddr();
			i++;
			if( i >= retryCount ) break;			
			try { Thread.sleep(retryInterval); } catch(Exception e) { break; }
		}
		log.error("report span failed, content="+lastContent);
	}
	
	String convert(TraceContext ctx, Span span) {
		List<TraceSegment> list = new ArrayList<>();
		list.add(  convertSegment(ctx,span) );
		return Json.toJson(list);
	}

	List<Long> toUniqueId(String traceId) {
		String[] ss = traceId.split("\\.");
		List<Long> list = new ArrayList<>(3);
		list.add(Long.parseLong(ss[0]));
		list.add(Long.parseLong(ss[1]));
		list.add(Long.parseLong(ss[2]));
		return list;
	}

	List<Long> toUniqueIdFromSpanId(String spanId) {
		int p = spanId.lastIndexOf(":");
		return toUniqueId(spanId.substring(0,p));
	}
	
	int toIndexFromSpanId(String spanId) {
		if( isEmpty(spanId) ) return -1;
		int p = spanId.lastIndexOf(":");
		return Integer.parseInt( spanId.substring(p+1));
	}
		
	TraceSegment convertSegment(TraceContext ctx, Span span) {
		TraceSegment ts = new TraceSegment();
		ts.gt = Arrays.asList(  toUniqueId(ctx.getTrace().getTraceId()) ) ;
		ts.sg = new TraceSegmentObject();
		ts.sg.ts = toUniqueIdFromSpanId( span.getSpanId() );
		ts.sg.ai = appId;
		ts.sg.ii = instanceId;
		ts.sg.ss = new ArrayList<>();
		convertSpan(ctx,span,ts.sg.ss);
		return ts;
	}

	void convertSpan(TraceContext ctx, Span span,List<SpanObject> list) {
		SpanObject so = new SpanObject();
		
		so.si = toIndexFromSpanId(span.getSpanId());
		so.ps = so.si == 0 ? -1 : toIndexFromSpanId(span.getParentSpanId());
		so.st = ( ctx.getRequestTimeMicros() + ( span.getStartMicros() - ctx.getStartMicros() ) ) / 1000 ;
		so.et = so.st + span.getTimeUsedMicros() / 1000 ;
		so.on = span.getAction();
		if( span.getType().equals("RPCSERVER") ||  span.getType().equals("HTTPSERVER") ) {
			so.on = span.getAction() +"@"+Trace.getAppName();
		} else if( span.getType().equals("RPCCLIENT") ) {
			so.on = span.getAction() +"@rpcclient";
		}
		so.pn = span.getRemoteAddr();
		so.ie = !span.getStatus().equals("SUCCESS");
		
		switch( span.getType() ) {
		case "RPCSERVER": // krpc hard coded
				so.tv = 0;
				so.lv = 2;
				break;

		case "HTTPSERVER": // krpc hard coded
				so.tv = 0;
				so.lv = 2;
				break;
				
		case "RPCCLIENT": // krpc hard coded
				so.tv = 1;
				so.lv = 2;
				break;
				
		case "DB":
				so.tv = 1;
				so.lv = 1;
				break;

		case "HTTP":
				so.tv = 1;
				so.lv = 3;
				break;

		case "MQ":
				so.tv = 1;
				so.lv = 4;
				break;
			
		case "REDIS":
		case "MC":
				so.tv = 1;
				so.lv = 5;
				break;

		default:
				so.tv = 1;
				so.lv = 0;
				break;
		}
		
		List<Event> events = span.getEvents();
		if( events != null ) {
			List<SpanObjectEvent> ss = new ArrayList<>();
			for(Event e:events) {
				SpanObjectEvent s = new SpanObjectEvent();
				s.ti = ctx.getRequestTimeMicros() + ( e.getStartMicros() - ctx.getStartMicros() ) / 1000 ;
				s.addKV("name", e.getType() + ":" + e.getAction());
				s.addKV("status",e.getStatus());
				String data = e.getData();
				if( !isEmpty(data) )
					s.addKV("data",data);
				ss.add(s);
			}
			so.lo = ss;
		}
		
		Map<String,String> tags = span.getTags();
		if( tags != null ) {
			List<SpanObjectKV> ss = new ArrayList<>();
			for(Map.Entry<String, String> e:tags.entrySet()) {
				ss.add(new SpanObjectKV(e.getKey(),e.getValue()));
			}
			so.to = ss;
		}
		
		if( span.getType().equals("RPCSERVER") ) {
			ReferenceObject ro = new ReferenceObject();
			ro.pts = toUniqueIdFromSpanId(ctx.getTrace().getParentSpanId());
			ro.psp = toIndexFromSpanId(ctx.getTrace().getParentSpanId());
			ro.pii = Integer.parseInt( ctx.getTagForRpc("p-instance-id") );
			ro.psn = ctx.getTagForRpc("p-service-name") ;
			ro.eii = Integer.parseInt( ctx.getTagForRpc("e-instance-id") );
			ro.esn = ctx.getTagForRpc("e-service-name") ;
			ro.nn = getRemoteAddr( ctx.getTrace().getPeers() );
			so.rs = Arrays.asList(ro);
		}
		
		list.add(so);
		
		if( span.getChildren() != null ) {
			for(Span child:span.getChildren()) {
				convertSpan(ctx, child, list);
			}
		}		
	}
	
	public String getRemoteAddr(String peers) {
		if( peers.isEmpty() ) return "";
		String[] ss = peers.split(",");
		return ss[ss.length-1];
	}
	
	public boolean useCtxSubCalls() { 
		return true;  
	}

	public void inject(TraceContext ctx, Span span, RpcMeta.Trace.Builder traceBuilder) {

		Span rootSpan = span.getRootSpan();

		String serviceName = rootSpan.getAction() +"@"+Trace.getAppName();
		if( rootSpan.getType().equals("RPCCLIENT") ) {
			serviceName = rootSpan.getAction() +"@rpcclient";
		}
						
		ctx.tagForRpcIfAbsent("e-instance-id", String.valueOf(instanceId)); // entry service name
		ctx.tagForRpcIfAbsent("e-service-name", serviceName); // entry service name
		ctx.tagForRpc("p-instance-id", String.valueOf(instanceId)); // parent service name
		ctx.tagForRpc("p-service-name", serviceName); // parent service name
		
		traceBuilder.setTraceId(ctx.getTrace().getTraceId());
		traceBuilder.setParentSpanId("");
		traceBuilder.setSpanId(span.getSpanId());
		traceBuilder.setTags(ctx.getTagsForRpc());
	}
	
	public SpanIds restore(String parentSpanId, String spanId) {	
		String newSpanId = createUniqueId() + ":0";		
		return new SpanIds(spanId,newSpanId); // only the root span keeps the parent span id
	}	
	
	public TraceIds newStartTraceIds(boolean isServer) {
		String traceId = createUniqueId();
		if( isServer)
			return new TraceIds(traceId,"",traceId+":0");
		else
			return new TraceIds(traceId,"","");
	}

	public SpanIds newChildSpanIds(String spanId,AtomicInteger subCalls) {
		if( isEmpty(spanId) ) {
			return new SpanIds("",createUniqueId()+":0");
		}
		int p = spanId.indexOf(":");
		String childId = spanId.substring(0,p)+":"+subCalls.incrementAndGet(); // just increment span number		
		return new SpanIds(spanId,childId);
	}
	
	String createUniqueId() {
		long part2 = Thread.currentThread().getId();
		int v = seq.incrementAndGet();
		if( v >= 9900 ) seq.compareAndSet(v, 0);  
		long part3 = System.currentTimeMillis()*10000 + v; 
		return instanceId + "." + part2 + "." +  part3; 
	}
	
	void nextQueryAddr() {
		if( queryAddrsIndex + 1 >= queryAddrs.length ) queryAddrsIndex = 0;
		else queryAddrsIndex++;
	}

	String currentQueryAddr() {
		return queryAddrs[queryAddrsIndex];
	}

	void setServerAddrs(List<String> newAddrs) {
		synchronized(serverAddrSyncObj) {
			serverAddrs = newAddrs.toArray(new String[0]);
			if( serverAddrIndex == -1 ) {
				log.info("init addrs="+newAddrs);
				serverAddrIndex = 0;
			}
			if( serverAddrIndex >= serverAddrs.length ) {
				serverAddrIndex = 0;
			}
		}
	}
	
	void nextServerAddr() {
		synchronized(serverAddrSyncObj) {
			serverAddrIndex++;
			if( serverAddrIndex >= serverAddrs.length ) serverAddrIndex = 0;
		}
	}

	String currentServerAddr() {
		synchronized(serverAddrSyncObj) {
			return serverAddrs[serverAddrIndex];
		}
	}

	private void getOsInfo() {
		
		// "oi":{"osName":"linux","hostname":"localhost","processNo":11211,"ipv4s":["127.0.0.1"]}
		
		osInfo = new OsInfo();
		
		osInfo.ipv4s = Arrays.asList(IpUtils.localIp());
	
		try {
			osInfo.hostname = Inet4Address.getLocalHost().getHostName();
		} catch(Exception e) {
			osInfo.hostname = osInfo.ipv4s.get(0);
		}
	
		String os = System.getProperty("os.name").toLowerCase();
		if( os.indexOf("linux") >= 0 ) osInfo.osName = "linux";
		else if( os.indexOf("mac") >= 0 ) osInfo.osName = "mac";
		else if( os.indexOf("windows") >= 0 ) osInfo.osName = "windows";
		else osInfo.osName = "unknown";
				
		String xbeanName = ManagementFactory.getRuntimeMXBean().getName(); 
		osInfo.processNo = Integer.parseInt(  xbeanName.split("@")[0] );  
	}

	String uuid() {
		String s = UUID.randomUUID().toString();
	    return s.replaceAll("-", "");		
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

	static class InstanceInfo {
		int ai;
		String au;
		long rt;
		OsInfo oi;
		
		public int getAi() {
			return ai;
		}
		public void setAi(int ai) {
			this.ai = ai;
		}
		public String getAu() {
			return au;
		}
		public void setAu(String au) {
			this.au = au;
		}
		public long getRt() {
			return rt;
		}
		public void setRt(long rt) {
			this.rt = rt;
		}
		public OsInfo getOi() {
			return oi;
		}
		public void setOi(OsInfo oi) {
			this.oi = oi;
		}
		
	}
	
	static class OsInfo {
		String osName;
		String hostname;
		int processNo;
		List<String> ipv4s;
		
		public String getOsName() {
			return osName;
		}
		public void setOsName(String osName) {
			this.osName = osName;
		}
		public String getHostname() {
			return hostname;
		}
		public void setHostname(String hostname) {
			this.hostname = hostname;
		}
		public int getProcessNo() {
			return processNo;
		}
		public void setProcessNo(int processNo) {
			this.processNo = processNo;
		}
		public List<String> getIpv4s() {
			return ipv4s;
		}
		public void setIpv4s(List<String> ipv4s) {
			this.ipv4s = ipv4s;
		}
	}
		
	static class TraceSegment {
		List<List<Long>> gt;  // globalTraceIds 
		TraceSegmentObject sg; // segments
		
		public List<List<Long>> getGt() {
			return gt;
		}
		public void setGt(List<List<Long>> gt) {
			this.gt = gt;
		}
		public TraceSegmentObject getSg() {
			return sg;
		}
		public void setSg(TraceSegmentObject sg) {
			this.sg = sg;
		}
		
	}
	static class TraceSegmentObject {
		List<Long> ts; // traceSegmentId
		int ai; // application id
		int ii; // instance id
		List<SpanObject> ss; // spans
		
		public List<Long> getTs() {
			return ts;
		}
		public void setTs(List<Long> ts) {
			this.ts = ts;
		}
		public int getAi() {
			return ai;
		}
		public void setAi(int ai) {
			this.ai = ai;
		}
		public int getIi() {
			return ii;
		}
		public void setIi(int ii) {
			this.ii = ii;
		}
		public List<SpanObject> getSs() {
			return ss;
		}
		public void setSs(List<SpanObject> ss) {
			this.ss = ss;
		}
	}

	static class SpanObject {
		int si; // span id
		int ps; // parent span id
		long st; // start time
		long et; // end time

		String on = ""; // operation name
		int oi; // operation id, not used

		String pn = ""; // peer name
		int pi; // peer id, not used
		
		boolean ie; // is error

		int tv; // span type 0=Entry 1=Exit 2=Local
		int lv; // span layer  0=Unknown  1=Database 2=RPCFramework 3=Http 4=MQ 5=Cache
		String cn = ""; // component name, not used
		int ci; // componentId, not used
		
		List<SpanObjectEvent> lo; // logs
		List<SpanObjectKV> to; // tags
		List<ReferenceObject> rs; //  TraceSegmentReference
		
		public int getSi() {
			return si;
		}
		public void setSi(int si) {
			this.si = si;
		}
		public int getPs() {
			return ps;
		}
		public void setPs(int ps) {
			this.ps = ps;
		}
		public int getTv() {
			return tv;
		}
		public void setTv(int tv) {
			this.tv = tv;
		}
		public int getLv() {
			return lv;
		}
		public void setLv(int lv) {
			this.lv = lv;
		}
		public long getSt() {
			return st;
		}
		public void setSt(long st) {
			this.st = st;
		}
		public long getEt() {
			return et;
		}
		public void setEt(long et) {
			this.et = et;
		}
		public int getCi() {
			return ci;
		}
		public void setCi(int ci) {
			this.ci = ci;
		}
		public String getCn() {
			return cn;
		}
		public void setCn(String cn) {
			this.cn = cn;
		}
		public int getOi() {
			return oi;
		}
		public void setOi(int oi) {
			this.oi = oi;
		}
		public String getOn() {
			return on;
		}
		public void setOn(String on) {
			this.on = on;
		}
		public int getPi() {
			return pi;
		}
		public void setPi(int pi) {
			this.pi = pi;
		}
		public String getPn() {
			return pn;
		}
		public void setPn(String pn) {
			this.pn = pn;
		}
		public boolean isIe() {
			return ie;
		}
		public void setIe(boolean ie) {
			this.ie = ie;
		}
		public List<SpanObjectEvent> getLo() {
			return lo;
		}
		public void setLo(List<SpanObjectEvent> lo) {
			this.lo = lo;
		}
		public List<SpanObjectKV> getTo() {
			return to;
		}
		public void setTo(List<SpanObjectKV> to) {
			this.to = to;
		}
		public List<ReferenceObject> getRs() {
			return rs;
		}
		public void setRs(List<ReferenceObject> rs) {
			this.rs = rs;
		}
	}

	static class ReferenceObject {
		
		List<Long> pts; // parentTraceSegmentId
		int psp; // parentSpanId

		int pii; // parentApplicationInstanceId ???
		String psn; // parentServiceName ??? parent app name
		int psi; // parentServiceId, not used

		int eii; // entryApplicationInstanceId ???
		String esn = ""; // entryServiceName ??? root app name
		int esi; // entryServiceId, not used
		
		String nn = ""; // networkAddress
		int ni; // networkAddressId, not used
		int rv;  // RefTypeValue 0=CrossProcess 1=CrossThread, always use 0
		
		public List<Long> getPts() {
			return pts;
		}
		public void setPts(List<Long> pts) {
			this.pts = pts;
		}
		public int getPii() {
			return pii;
		}
		public void setPii(int pii) {
			this.pii = pii;
		}
		public int getPsp() {
			return psp;
		}
		public void setPsp(int psp) {
			this.psp = psp;
		}
		public int getPsi() {
			return psi;
		}
		public void setPsi(int psi) {
			this.psi = psi;
		}
		public String getPsn() {
			return psn;
		}
		public void setPsn(String psn) {
			this.psn = psn;
		}
		public int getNi() {
			return ni;
		}
		public void setNi(int ni) {
			this.ni = ni;
		}
		public String getNn() {
			return nn;
		}
		public void setNn(String nn) {
			this.nn = nn;
		}
		public int getEii() {
			return eii;
		}
		public void setEii(int eii) {
			this.eii = eii;
		}
		public int getEsi() {
			return esi;
		}
		public void setEsi(int esi) {
			this.esi = esi;
		}
		public String getEsn() {
			return esn;
		}
		public void setEsn(String esn) {
			this.esn = esn;
		}
		public int getRv() {
			return rv;
		}
		public void setRv(int rv) {
			this.rv = rv;
		}
	}
	
	static class SpanObjectEvent {
		long ti; // timestamp
		List<SpanObjectKV> ld = new ArrayList<>();
		
		public void addKV(String key,String value) {
			ld.add(new SpanObjectKV(key,value));
		}
		
		public long getTi() {
			return ti;
		}
		public void setTi(long ti) {
			this.ti = ti;
		}
		public List<SpanObjectKV> getLd() {
			return ld;
		}
		public void setLd(List<SpanObjectKV> ld) {
			this.ld = ld;
		}
	}	
	static class SpanObjectKV {
		String k;
		String v;
		
		SpanObjectKV(String k,String v) {
			this.k = k;
			this.v = v;
		}

		public String getK() {
			return k;
		}

		public void setK(String k) {
			this.k = k;
		}

		public String getV() {
			return v;
		}

		public void setV(String v) {
			this.v = v;
		}
	}		 
} 
