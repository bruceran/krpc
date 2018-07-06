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
	int queueSize = 10000;
	
	List<String> addrs = new ArrayList<>();
	int addrIndex = 0;
	
	DefaultHttpClient hc;
	CatNettyClient client;
	NamedThreadFactory threadFactory = new NamedThreadFactory("cat_report");
	ThreadPoolExecutor pool;
	Timer timer;
	
	public void config(String paramsStr) {
		
		domain = Trace.getAppName();
		
		initIpInfo();
		
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
	 
		// todo mutiple server addrs
		String url = "http://"+params.get("server")+"/cat/s/router?op=json&domain=%s&ip=%s";
		routeQueryUrl = String.format(url, domain, localIp);

		String s = params.get("queueSize");
		if( !isEmpty(s) ) queueSize = Integer.parseInt(s);	
	}

	private void initIpInfo() {
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

	public void init() {
		hc = new DefaultHttpClient();
		hc.init();
		pool = new ThreadPoolExecutor(1,1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
		
        timer = new Timer("cattimer");
        timer.schedule( new TimerTask() {
            public void run() {
            	queryRoutes();
            }
        },  15000, 15000 );
        
		client = new CatNettyClient(timer);
		client.init();
		queryRoutes();
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

	// todo the last is backup ip, remove unused ip
	synchronized void setAddrs(String[] newAddrs) {
		for(int i=0;i<newAddrs.length;++i) {
			String s = newAddrs[i];
			if( isEmpty(s) ) continue;
			if( addrs.contains(s)) continue;
			addrs.add(s);
			client.connect(s);
		}
		String cur = currentAddr();
		if( isEmpty(cur) ) {
			addrIndex = 0;
			return;
		}
	}
	
	synchronized String currentAddr() {
		if( addrs.isEmpty() ) return "";
		return addrs.get(addrIndex);
	}
	
	synchronized void skipAddr(String addr) {
		if( !currentAddr().equals(addr) ) return;
		addrIndex++;
		if( addrIndex >= addrs.size() )  addrIndex = 0;
	}
	
	/*
	$curl "http://10.241.22.199:8080/cat/s/router?domain=cattest&op=json&ip=127.0.3.3"
	{"kvs":{"routers":"10.241.22.199:2280;127.0.0.1:2280;","sample":"1.0"}}
	*/
	
	@SuppressWarnings("unchecked")
	void queryRoutes() {
		HttpClientReq req = new HttpClientReq("GET",routeQueryUrl);
		HttpClientRes res = hc.call(req);		
		if( res.getRetCode() == 0 && res.getHttpCode() == 200 ) {
			Map<String,Object> map = Json.toMap(res.getContent());
			if( map == null ) return;
			Map<String,Object> kvs = (Map<String,Object>)map.get("kvs");
			if( kvs == null ) return;
			String s = (String)kvs.get("routers");
			if( isEmpty(s) ) return;
			String[] addrs = s.split(";");
			setAddrs(addrs);
			//String sample = (String)kvs.get("sample");
		} else {
			log.error("query cat router failed, retCode="+res.getRetCode()+",content="+res.getContent());
		}
	}

	public void send(TraceContext ctx, Span span) {
		
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

		String addr = currentAddr();
		if( isEmpty(addr) ) {
			log.error("cat routes is null");
			return;
		}
		
		StringBuilder b = new StringBuilder(1024);
		appendHeader(ctx, span, b);
		appendMessage(ctx, span, b);

System.out.println(b.toString());
		
		boolean ok = client.send(addr, b); // send StringBuilder, not String
		if(!ok) {
			skipAddr(addr);
			String newAddr = currentAddr();
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
			String parentSpanId = ctx.getTrace().getParentSpanId();
			b.append(parentSpanId).append(TAB); // parent message id
			b.append(traceId).append(TAB); // root message id
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
		b.append(formatMicros(ts)).append(TAB);		
		b.append("").append(TAB);		
		b.append(key).append(TAB);	
		b.append("0").append(TAB); // status
		b.append(escape(value)).append(TAB);
		b.append(LF);		 
	}	
	
	private void appendMetric(TraceContext ctx, Span span, StringBuilder b, Metric m) {
		b.append("M");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros() - ctx.getStartMicros();
		b.append(formatMicros(ts)).append(TAB);		
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
		b.append(formatMicros(ts)).append(TAB);		
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
		b.append(formatMicros(ts)).append(TAB);				
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
		b.append(formatMicros(ts)).append(TAB);				
		b.append(span.getType()).append(TAB);		
		b.append(span.getAction()).append(TAB); 
		b.append(LF);		 
	}
	
	private void appendEndMessage(TraceContext ctx, Span span, StringBuilder b) {
		b.append("T");
		long ts = ctx.getRequestTimeMicros()+span.getStartMicros()+span.getTimeUsedMicros() - ctx.getStartMicros();
		b.append(formatMicros(ts)).append(TAB);				
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
	
	//  todo  restart
	volatile long savedHour = getHour();
	volatile AtomicInteger indexInHour = new AtomicInteger(getHourOffset());

	public String nextSpanId() {
		long hour = getHour();

		if (hour != savedHour) {
			indexInHour = new AtomicInteger(0); 
			savedHour = hour;
		}

		int index = indexInHour.getAndIncrement();

		StringBuilder sb = new StringBuilder(domain.length() + 32);

		sb.append(domain);
		sb.append('-');
		sb.append(localIpNum);
		sb.append('-');
		sb.append(hour);
		sb.append('-');
		sb.append(index);

		return sb.toString();
	}
	
	// escape \r \n \t \
	public static String escape(String s) {
		StringBuilder b = null;
		char[] chars = s.toCharArray();
		for(int i=0;i<chars.length;++i) {
			char ch = chars[i];
			
			if( ch == '\r' || ch == '\n' ||  ch == '\t' ||  ch == '\\' ) {
				if( b == null )  {
					b = new StringBuilder();
					for(int k=0;k<i;++k) {
						b.append(chars[k]);
					}
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
	
	long getHour() {
		long ts = System.currentTimeMillis();
		return ts / HOUR;
	}
	int getHourOffset() {
		long ts = System.currentTimeMillis();
		return (int)(ts % HOUR);
	}
	
	String formatMicros(long ts) {
		return f.get().format(new Date(ts/1000));
	}

	public TraceIds newStartTraceIds(boolean isServer) {
		String id = nextSpanId();
		return new TraceIds(id,"",id);
	}
	
	public SpanIds newChildSpanIds(String spanId,AtomicInteger subCalls) {
		return new SpanIds("",nextSpanId());
	}

	public SpanIds restore(String parentSpanId, String spanId) {	
		return new SpanIds(parentSpanId,nextSpanId());  // the parentSpanId is the root span id
	}
	
	public TraceIds inject(TraceContext ctx, Span span) {
		String traceId = ctx.getTrace().getTraceId();
		String rootSpanId = span.getRootSpanId();  // escape parent link error in cat ui
		if( !span.getType().equals("RPCSERVER")  ) { // escape root link error in cat ui
			traceId = rootSpanId;
		}
		return new TraceIds(traceId, rootSpanId , span.getSpanId()); // the parentSpanId is the root span id
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}
		
} 
