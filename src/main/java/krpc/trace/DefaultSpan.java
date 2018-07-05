package krpc.trace;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultSpan implements Span {

	private TraceContext ctx;
	private String parentSpanId;
	private String spanId;
	private String type;
	private String action;
	long startMicros;
	long timeUsedMicros;
	String status = "unset";
	String remoteAddr;
	Map<String,String> tags;
	List<Event> events = null;
	List<Span> children = null;
	AtomicInteger subCalls = null;
	
	AtomicInteger completed = new AtomicInteger(0); // 0=pending 1=stopped
	
	DefaultSpan(TraceContext ctx, String parentSpanId, String spanId, String type,String action,long startMicros) {
		this.ctx = ctx;
		this.parentSpanId = parentSpanId;
		this.spanId = spanId;
		this.type = type;
		this.action = action;
		if( startMicros <= 0 ) this.startMicros = System.nanoTime()/1000;
		else this.startMicros = startMicros;
	}
	
	public Span newChild(String type,String action) {
		if( subCalls == null ) subCalls = new AtomicInteger();
		String childSpanId = Trace.getAdapter().newChildSpanId(spanId,subCalls);
		Span child = new DefaultSpan(ctx, spanId, childSpanId, type, action,-1);
		if( children == null ) children = new ArrayList<>();
		children.add(child);		
		return child;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("parentSpanId=").append(parentSpanId).append(",");
		b.append("spanId=").append(spanId).append(",");
		b.append("type=").append(type).append(",");
		b.append("action=").append(action).append(",");
		b.append("startMicros=").append(startMicros).append(",");
		b.append("timeUsedMicros=").append(timeUsedMicros).append(",");
		b.append("status=").append(status).append(",");
		b.append("remoteAddr=").append(remoteAddr).append(",");
		b.append("tags=").append(tags).append(",");
		b.append("events=").append(events).append(",");
		return b.toString();
	}
	
    public long stop() {
    	return stop("SUCCESS");
    }
    
    public long stop(boolean ok) {
    	return stop(ok?"SUCCESS":"ERROR");
    }
    
	public long stop(String status) {
		if( !completed.compareAndSet(0, 1) ) {
			return 0;
		}
		this.status = status;
		timeUsedMicros = System.nanoTime()/1000 - startMicros;
		ctx.stopped(this);
		return getTimeUsedMicros();
	}
	
	public void stopAsyncIfNeeded() {
		if( !completed.compareAndSet(0, 2) ) {
			return;
		}
		this.status = "ASYNC";
		timeUsedMicros = System.nanoTime()/1000 - startMicros;
	}
	
	public void logEvent(String type,String action,String status,String data) {
		if( events == null ) events = new ArrayList<>();
		Event e = new Event(type,action,status,data);
		events.add(e);
	}
	
	public void logException(Throwable cause) {
		logException(null,cause);	
	}
	
	public void logException(String message, Throwable cause) {
		if( events == null ) events = new ArrayList<>();
		StringWriter sw = new StringWriter(1024);
		if (message != null) {
			sw.write(message);
			sw.write(' ');
		}
		cause.printStackTrace(new PrintWriter(sw));
		logEvent("EXCEPTION",cause.getClass().getName(),"ERROR",sw.toString());	
	}

	public void tag(String key,String value) {
		if( tags == null ) tags = new HashMap<>();
		tags.put(key, value);
	}

	public void setRemoteAddr(String addr) {
		this.remoteAddr = addr;
	}
	
	public AtomicInteger getCompleted() {
		return completed;
	}

	public String getType() {
		return type;
	}

	public String getStatus() {
		return status;
	}

	public List<Event> getEvents() {
		return events;
	}

	public String getAction() {
		return action;
	}

	public List<Span> getChildren() {
		return children;
	}

	public long getStartMicros() {
		return startMicros;
	}

	public long getTimeUsedMicros() {
		return timeUsedMicros;
	}

	public Map<String, String> getTags() {
		return tags;
	}

	public String getRemoteAddr() {
		return remoteAddr;
	}

	public String getParentSpanId() {
		return parentSpanId;
	}
 
	public String getSpanId() {
		return spanId;
	}
 
}
