package krpc.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Trace {

	private static Logger log = LoggerFactory.getLogger(Trace.class);
	private static String appName = "unknown";
	private static TraceAdapter adapter = new DefaultTraceAdapter();
	private static TraceContextFactory factory = new DefaultTraceContextFactory();
	private static ThreadLocal<TraceContext> tlContext = new ThreadLocal<TraceContext>();
	
	public static String getAppName() {
		return appName;
	}

	public static void setAppName(String appName) {
		Trace.appName = appName;
	}	 

	public static TraceAdapter getAdapter() {
		return adapter;
	}

	public static void setAdapter(TraceAdapter adapter) {
		Trace.adapter = adapter;
	}	    
	
	public static TraceContextFactory getFactory() {
		return factory;
	}
	
	public static void setFactory(TraceContextFactory f) {
		factory = f;
	}

    public static TraceContext currentContext() {
        return tlContext.get();
    }
	
    public static void setCurrentContext(TraceContext traceContext) {
    	tlContext.set(traceContext);
    }	

    public static void startServer(String traceId,String rpcId,String peers,String apps, int sampled,String type,String action) {
    	TraceContext ctx = factory.newTraceContext(traceId, rpcId, peers,apps,sampled, type, action);
    	setCurrentContext(ctx);
    }
    
    public static void start(String type,String action) {
    	TraceContext ctx = getOrNew();
		ctx.start(type,action);
	}

    public static Span startAsync(String type,String action) {
    	TraceContext ctx = getOrNew();
    	return ctx.startAsync(type,action);
    }	    
    
	private static TraceContext getOrNew() {
    	TraceContext ctx = tlContext.get();
    	if( ctx != null ) return ctx;
		ctx = factory.newTraceContext();
		setCurrentContext(ctx);
    	return ctx;
    }
    
	public static Span currentSpan() {
    	TraceContext ctx = tlContext.get();
    	if( ctx != null ) return ctx.currentSpan();
    	return null;
    }
	
    public static long stop() {
    	return stop("SUCCESS");
    }
    
    public static long stop(boolean ok) {
    	return stop(ok?"SUCCESS":"ERROR");
    }
        
    public static long stop(String status) {
    	Span span = currentSpan();
    	if( span == null ) {
    		log.error("span not started");
    		return 0;
    	}
    	span.stop(status);
    	return span.getTimeUsedMicros();
    }	    

    
    public static void logEvent(String type,String name) {
    	logEvent(type,name,"SUCCESS",null);
    }
    
    public static void logEvent(String type,String name,String result,String data) {
    	Span span = currentSpan();
    	if( span == null ) {
    		log.error("span not started");
    		return;
    	}
    	span.logEvent(type,name,result,data);
    }	    
    
    public static void logException(Throwable c) {
    	logException(null,c);
    }	     

    public static void logException(String message, Throwable c) {
    	Span span = currentSpan();
    	if( span == null ) {
    		log.error("span not started");
    		return;
    	}
    	span.logException(message, c);
    }	     
        
    public static void tag(String key,String value) {
    	Span span = currentSpan();
    	if( span == null ) {
    		log.error("span not started");
    		return;
    	}
    	span.tag(key,value);
    }

    public static void setRemoteAddr(String addr) {
    	Span span = currentSpan();
    	if( span == null ) {
    		log.error("span not started");
    		return;
    	}
    	span.setRemoteAddr(addr);
    }
    
}
