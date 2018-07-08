package krpc.trace.adapter;

import java.net.Inet4Address;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
import krpc.trace.Metric;
import krpc.trace.Span;
import krpc.trace.SpanIds;
import krpc.trace.TraceIds;
import krpc.trace.Trace;
import krpc.trace.TraceAdapter;
import krpc.trace.TraceContext;

public class CatTraceAdapter implements TraceAdapter,InitClose {

	static Logger log = LoggerFactory.getLogger(CatTraceAdapter.class);
	
	private static final String TAB = "\t";
	private static final String LF = "\n";
	private static final long HOUR = 3600 * 1000L;
		
	ThreadLocal<SimpleDateFormat> f = new ThreadLocal<SimpleDateFormat>() {
		public SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		}
	};
	
	String domain;
	String localIp;
	String hostName;
	String localIpNum;
	
	String routeQueryUrl;
	int queueSize = 1000;
	
	String[] queryAddrs;
	int queryAddrsIndex = 0;
	
	String[] serverAddrs = new String[0];
	volatile int serverAddrIndex = -1;
	Object serverAddrSyncObj = new Object();
	
	DefaultHttpClient hc;
	CatNettyClient client;
	NamedThreadFactory threadFactory = new NamedThreadFactory("cat_report");
	ThreadPoolExecutor pool;
	Timer timer;

	Object indexSyncObj = new Object();
	long savedHour = 0;
	long indexInHour = 0;
	int indexMultiplier = 100; 

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		
		queryAddrs = params.get("server").split(",");
		
		String s = params.get("queueSize");
		if( !isEmpty(s) ) queueSize = Integer.parseInt(s);	
		
		s = params.get("indexMultiplier");
		if( !isEmpty(s) ) indexMultiplier = Integer.parseInt(s);	
	}

	public void init() {
		
		initHourIndex();
		
		domain = Trace.getAppName();
		
		getHostInfo();
		
		routeQueryUrl =  "http://%s/cat/s/router?op=json&domain="+domain+"&ip="+localIp;

		hc = new DefaultHttpClient();
		hc.init();
		pool = new ThreadPoolExecutor(1,1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
		
		client = new CatNettyClient();
		client.init();   
		
		boolean ok = queryRoutes();
		if( !ok) {
	        timer = new Timer("cattimer");
	        timer.schedule( new TimerTask() {
	            public void run() {
	            	queryRoutesForInit();
	            }
	        },  3000, 3000 );
		} else {
			startQueryRoutesTimer();
		}
	}
	
	public void close() {
		if( hc == null ) return;
		client.close();
		client = null;
		pool.shutdownNow();
		pool = null;
		timer.cancel();
		timer = null;
		hc.close();
		hc = null;
	}
	
	void startQueryRoutesTimer() {
        timer = new Timer("cattimer");
        timer.schedule( new TimerTask() {
            public void run() {
            	queryRoutes();
            }
        },  60000, 60000 );			
        
     
	}
	
	void queryRoutesForInit() {
		boolean ok = queryRoutes();	
		if(ok) {
			timer.cancel();
			startQueryRoutesTimer();
		}
	}
	
	/*
	$curl "http://10.241.22.199:8080/cat/s/router?domain=cattest&op=json&ip=127.0.3.3"
	{"kvs":{"routers":"10.241.22.199:2280;127.0.0.1:2280;","sample":"1.0"}}
	*/

	@SuppressWarnings("unchecked")
	boolean queryRoutes() {
		String url = String.format(routeQueryUrl, currentQueryAddr());
		HttpClientReq req = new HttpClientReq("GET",url);
		HttpClientRes res = hc.call(req);		
		if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
			Map<String,Object> map = Json.toMap(res.getContent());
			if( map == null ) return false;
			Map<String,Object> kvs = (Map<String,Object>)map.get("kvs");
			if( kvs == null ) return false;
			String s = (String)kvs.get("routers");
			if( s == null ) return false;
			if( s.endsWith(";") ) s = s.substring(0, s.length() - 1 );
			if( isEmpty(s) ) return false;
			
			setServerAddrs(s);
			return true;
		} else {
			nextQueryAddr();
			log.error("query cat router failed, retCode="+res.getRetCode()+",content="+res.getContent());
			return false;
		}
	}

	void nextQueryAddr() {
		if( queryAddrsIndex + 1 >= queryAddrs.length ) queryAddrsIndex = 0;
		else queryAddrsIndex++;
	}
	String currentQueryAddr() {
		return queryAddrs[queryAddrsIndex];
	}

	// the last addr is backup addr
	void setServerAddrs(String s) {
		String[] newAddrs = s.split(";");
		synchronized(serverAddrSyncObj) {
			List<String> added = getAdded(serverAddrs,newAddrs);
			List<String> removed = getRemoved(serverAddrs,newAddrs);
			serverAddrs = newAddrs;
			if( serverAddrIndex == -1 ) {
				log.info("init addrs="+s);
				serverAddrIndex = 0;
			}
			if( serverAddrIndex >= serverAddrs.length )  serverAddrIndex = 0;
			
			for(String addr:added) {
				client.connect(addr);
			}
			for(String addr:removed) {
				client.disconnect(addr);
			}
		}
	}

	void nextServerAddr() {
		synchronized(serverAddrSyncObj) {
			serverAddrIndex++;
			if( serverAddrIndex >= serverAddrs.length )  serverAddrIndex = 0;
			if( serverAddrIndex == serverAddrs.length - 1 ) { // the backup addr
				for(int k=0;k<serverAddrs.length - 1;++k) {
					String addr = serverAddrs[k];
					if( client.isAlive(addr) ) { // use the alive addr instead of backup addr
						serverAddrIndex = k;
						return;
					}
				}
			}
		}
	}

	String currentServerAddr() {
		synchronized(serverAddrSyncObj) {
			return serverAddrs[serverAddrIndex];
		}
	}
	
	public void send(TraceContext ctx, Span span) {
		if( serverAddrIndex == -1 ) return;
		
		try {
			pool.execute( new Runnable() {
				public void run() {
					try {
						report(ctx,span);
					} catch(Exception e) {
						log.error("cat report exception",e);
					}
				}
			});
		} catch(Exception e) {
			log.error("cat report queue is full");
		}
	}

	void report(TraceContext ctx, Span span) {

		String addr = currentServerAddr();
		if( isEmpty(addr) ) {
			log.error("cat routes is null");
			return;
		}
		
		StringBuilder b = new StringBuilder(1024);
		appendHeader(ctx, span, b);
		appendMessage(ctx, span, b);

// System.out.println(b.toString());
		
		boolean ok = client.send(addr, b); // send StringBuilder, not String
		if(!ok) {
			nextServerAddr();
			String newAddr = currentServerAddr();
			if( !newAddr.equals(addr) ) {
				ok = client.send(newAddr, b);
			}
		}
		if(!ok) {
			log.error("failed to report cat data");
		}
	}
	
	private void appendHeader(TraceContext ctx, Span span, StringBuilder b) {
		b.append("PT1").append(TAB);
		b.append(domain).append(TAB);
		b.append(hostName).append(TAB);
		b.append(localIp).append(TAB);
		
		b.append(ctx.getThreadGroupName()).append(TAB);
		b.append(ctx.getThreadId()).append(TAB);
		b.append(ctx.getThreadName()).append(TAB);
		
		String spanId = span.getSpanId();
		
		b.append(spanId).append(TAB); // message id
		
		if( !span.getType().equals("RPCSERVER")  ) {
			b.append(spanId).append(TAB); // parent message id
			b.append(spanId).append(TAB); // root message id			
		} else {
			String traceId = ctx.getTrace().getTraceId();
			String rootSpanId = ctx.getTagForRpc("p-root-span-id");
			b.append(rootSpanId).append(TAB);
			b.append(traceId).append(TAB);
		}

		b.append(""); // session token
		b.append(LF);
	}

	private void appendMessage(TraceContext ctx, Span span, StringBuilder b) {
		List<Event> events = span.getEvents();
		List<Span> children = span.getChildren(); 
		Map<String,String> tags = span.getTags();
		List<Metric> metrics = span.getMetrics(); 
		
		if( events == null && children == null && tags == null  && metrics == null  ) {
			appendAtomicMessage(ctx,span,b);
		} else {
			appendStartMessage(ctx,span,b);
			if( tags != null  )  {
				for(Map.Entry<String, String> entry:tags.entrySet()) {
					appendTag(ctx,span,b,entry.getKey(),entry.getValue() ); // tag -> cat trace
				}
			}
			if( metrics != null  )  {
				for(Metric m:metrics) {
					appendMetric(ctx,span,b,m);
				}
			}
			if( events != null  )  {
				for(Event e:events) {
					appendEvent(ctx,span,b,e);
				}
			}
			if( children != null ) {
				for(Span s:children) {
					appendMessage(ctx,s,b);
				}
			}
			appendEndMessage(ctx,span,b);
		}
	}
	
	private void appendTag(TraceContext ctx, Span span, StringBuilder b, String key, String value) {
		b.append("L");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros() - ctx.getStartMicros();
		b.append(formatTimeStampMicros(ts)).append(TAB);		
		b.append("").append(TAB);		
		b.append(key).append(TAB);	
		b.append("0").append(TAB); // status
		b.append(escape(value)).append(TAB);
		b.append(LF);		 
	}	
	
	private void appendMetric(TraceContext ctx, Span span, StringBuilder b, Metric m) {
		b.append("M");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros() - ctx.getStartMicros();
		b.append(formatTimeStampMicros(ts)).append(TAB);		
		b.append("").append(TAB);		
		b.append(m.getKey()).append(TAB);	
		String status = "";
		switch( m.getType() ) {
			case Metric.COUNT: status = "C"; break;
			case Metric.QUANTITY: status = "C"; break;
			case Metric.SUM: status = "S"; break;
			case Metric.QUANTITY_AND_SUM: status = "S,C"; break;
			default: return;
		}
		b.append(status).append(TAB);		 
		String data = m.getValue();
		b.append(data).append(TAB);
		b.append(LF);		 
	}	
	
	private void appendEvent(TraceContext ctx, Span span, StringBuilder b, Event e) {
		b.append("E");
		long ts = ctx.getRequestTimeMicros()+e.getStartMicros() - ctx.getStartMicros();
		b.append(formatTimeStampMicros(ts)).append(TAB);		
		b.append(e.getType()).append(TAB);		
		b.append(e.getAction()).append(TAB);		 
		String status = e.getStatus();
		if( status.equals("SUCCESS")) status = "0";
		b.append(status).append(TAB);		 
		String data = e.getData();
		if( data == null ) data = "";
		b.append(escape(data)).append(TAB);
		b.append(LF);		 
	}	
	
	private void appendAtomicMessage(TraceContext ctx, Span span, StringBuilder b) {
		b.append("A");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros() - ctx.getStartMicros();
		b.append(formatTimeStampMicros(ts)).append(TAB);				
		b.append(span.getType()).append(TAB);		
		b.append(span.getAction()).append(TAB);
		String status = span.getStatus();
		if( status.equals("SUCCESS")) status = "0";
		b.append(status).append(TAB);		 
		long us = span.getTimeUsedMicros();
		b.append(us).append("us").append(TAB);		 
		b.append("").append(TAB); // data
		b.append(LF);		 
	}
	
	private void appendStartMessage(TraceContext ctx, Span span, StringBuilder b) {
		b.append("t");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros() - ctx.getStartMicros();
		b.append(formatTimeStampMicros(ts)).append(TAB);				
		b.append(span.getType()).append(TAB);		
		b.append(span.getAction()).append(TAB); 
		b.append(LF);		 
	}
	
	private void appendEndMessage(TraceContext ctx, Span span, StringBuilder b) {
		b.append("T");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros()+span.getTimeUsedMicros() - ctx.getStartMicros();
		b.append(formatTimeStampMicros(ts)).append(TAB);				
		b.append(span.getType()).append(TAB);		
		b.append(span.getAction()).append(TAB);
		String status = span.getStatus();
		if( status.equals("SUCCESS")) status = "0";
		b.append(status).append(TAB);		 
		long us = span.getTimeUsedMicros();
		b.append(us).append("us").append(TAB);		 
		b.append("").append(TAB); // data
		b.append(LF);		 
	}	

	public boolean needThreadInfo() { 
		return true;  
	}

	public void inject(TraceContext ctx, Span span, RpcMeta.Trace.Builder traceBuilder) {
		String traceId = ctx.getTrace().getTraceId();
		String rootSpanId = span.getRootSpanId();  // escape parent link error in cat ui
		if( !span.getType().equals("RPCSERVER")  ) { // escape root link error in cat ui
			traceId = rootSpanId;
		}
		
		ctx.tagForRpc("p-root-span-id", rootSpanId);
		
		traceBuilder.setTraceId(traceId);
		traceBuilder.setParentSpanId("");
		traceBuilder.setSpanId(span.getSpanId());
		//traceBuilder.setSpanId(nextSpanId());
		traceBuilder.setTags(ctx.getTagsForRpc());
	}
	
	public SpanIds restore(String parentSpanId, String spanId) {	
		return new SpanIds("",nextSpanId());
		//return null;
	}

	public TraceIds newStartTraceIds(boolean isServer) {
		String id = nextSpanId();
		if( isServer ) return new TraceIds(id,"",id);
		else return new TraceIds(id,"","");
	}
	
	public SpanIds newChildSpanIds(String spanId,AtomicInteger subCalls) {
		return new SpanIds(spanId,nextSpanId());
	}

	public String nextSpanId() {
		StringBuilder sb = new StringBuilder(domain.length() + 32);
		sb.append(domain);
		sb.append('-');
		sb.append(localIpNum);
		sb.append('-');
		sb.append(getHourIndex());
	
		return sb.toString();
	}

	// restore index after restart
	// default indexMultiplier=100 means:
	// if qps < 100,000, spanId generated will not be duplicated
	// if qps >= 100,000, must change indexMultiplier to a higher value
	public void initHourIndex() {
		long ts = System.currentTimeMillis();
		savedHour = ts / HOUR;
		indexInHour = ( ts % HOUR ) * indexMultiplier;
	}

	public String getHourIndex() {

		long hour = 0;
		long index = 0;
		
		synchronized(indexSyncObj) {
			
			long ts = System.currentTimeMillis();
			
			hour = ts / HOUR;
			
			if (hour != savedHour) {
				savedHour = hour;
				indexInHour = 0;
			}
			
			indexInHour++;
			index = indexInHour;
		}
		
		return hour + "-" + index;		
	}
	
	String formatTimeStampMicros(long ts) {
		return f.get().format(new Date(ts/1000));
	}

	List<String> getAdded(String[] oldAddrs,String[] newAddrs) {
		List<String> list = new ArrayList<>();
		for(String addr: newAddrs ) {
			if( !contains(oldAddrs,addr) ) list.add(addr);
		}
		return list;
	}

	List<String> getRemoved(String[] oldAddrs,String[] newAddrs) {
		List<String> list = new ArrayList<>();
		for(String addr: oldAddrs ) {
			if( !contains(newAddrs,addr) ) list.add(addr);
		}
		return list;
	}

	boolean contains(String[] newAddrs,String addr) {
		for(String s:newAddrs) {
			if( s.equals(addr) ) return true;
		}
		return false;
	}

	private void getHostInfo() {
		localIp = IpUtils.localIp();
		try {
			hostName = Inet4Address.getLocalHost().getHostName();
		} catch(Exception e) {
			hostName = localIp;
		}
		String[] items = localIp.split("\\.");
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i++) {
			bytes[i] = (byte) Integer.parseInt(items[i]);
		}
		StringBuilder sb = new StringBuilder(bytes.length / 2);
		for (byte b : bytes) {
			sb.append(Integer.toHexString((b >> 4) & 0x0F));
			sb.append(Integer.toHexString(b & 0x0F));
		}
		localIpNum = sb.toString();
	}

	public static String escape(String s) {
		StringBuilder b = null;
		int len = s.length();
		for(int i=0;i<len;++i) {
			char ch = s.charAt(i);
			
			if( ch == '\r' || ch == '\n' ||  ch == '\t' ||  ch == '\\' ) {
				if( b == null )  {
					b = new StringBuilder(s.length()+100);
					b.append(s.substring(0,i));
				}
				b.append("\\");
				switch(ch) {
					case '\r': b.append('r');break;
					case '\n': b.append('n');break;
					case '\t': b.append('t');break;
					case '\\': b.append(ch);break;
				}
			} else {
				if( b != null ) b.append(ch);
			}
			
		}
		return b != null ? b.toString() : s;
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}
		
} 
