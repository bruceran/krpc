package krpc.trace;

import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.rpc.core.proto.RpcMeta;

public class DefaultTraceContext implements TraceContext {

	static Logger log = LoggerFactory.getLogger(DefaultTraceContext.class);
	
	RpcMeta.Trace trace;
	
	long requestTimeMicros = System.currentTimeMillis()*1000;
	long startMicros = System.nanoTime()/1000;
	AtomicInteger subCalls = new AtomicInteger(); 
	Deque<Span> spans = new ArrayDeque<Span>();

	long threadId;
	String threadName;
	String threadGroupName;
	
	public DefaultTraceContext() {
		initThreadNames();
		
		RpcMeta.Trace.Builder traceBuilder = RpcMeta.Trace.newBuilder();
		
		String newTraceId = Trace.getAdapter().newTraceId();
		String newParentSpanId = Trace.getAdapter().newDefaultSpanId(false,newTraceId);
		String newSpanId = newParentSpanId;
		traceBuilder.setTraceId( newTraceId );
		traceBuilder.setParentSpanId( newParentSpanId );
		traceBuilder.setSpanId( newSpanId );
		
		this.trace = traceBuilder.build();
	}
	
	public DefaultTraceContext(RpcMeta.Trace trace,String type,String action) {
		initThreadNames();
		
		String traceId = trace.getTraceId();
		String parentSpanId = trace.getParentSpanId();
		String spanId = trace.getSpanId();
		
		TraceAdapter.SpanIds ids = new TraceAdapter.SpanIds(parentSpanId, spanId);
		Trace.getAdapter().convertRpcSpanIds(traceId,ids);

		if( !parentSpanId.equals(ids.parentSpanId) ||  !spanId.equals(ids.spanId) ) {
			RpcMeta.Trace.Builder traceBuilder = trace.toBuilder();
			traceBuilder.setParentSpanId( ids.parentSpanId );
			traceBuilder.setSpanId( ids.spanId );
			this.trace = traceBuilder.build();
		} else {
			this.trace = trace;
		}

		Span rootSpan = new DefaultSpan(this, ids.parentSpanId, ids.spanId, type, action, startMicros);
		spans.addLast(rootSpan);		
		
		if( !isEmpty(trace.getTags()) ) {
			addTags(rootSpan,trace.getTags());
		}
	}

	void initThreadNames() {
		Thread t = Thread.currentThread();
		threadId = t.getId();
		threadName = t.getName();
		threadGroupName = t.getThreadGroup().getName();
	}
	
	// push the new span to the stack top
	public void start(String type,String action) {
		Span tail = spans.peekLast();
		if( tail == null ) {
			String childSpanId = Trace.getAdapter().newChildSpanId(trace.getSpanId(),subCalls);
			tail = new DefaultSpan(this, trace.getSpanId(), childSpanId, type, action,-1);
			spans.addLast(tail);
		} else {
			Span newChild = tail.newChild(type,action);
			spans.addLast(newChild);
		}
	}
	
	// donot push the new span to the stack top
	public Span startAsync(String type,String action) {
		Span tail = spans.peekLast();
		if( tail == null ) {
			String childSpanId = Trace.getAdapter().newChildSpanId(trace.getSpanId(),subCalls);
			return new DefaultSpan(this, trace.getSpanId(), childSpanId, type, action,-1);
		} else {
			return tail.newChild(type,action);
		}
	}
	
	public void serverSpanStopped(String result) {

		Span span = spans.peekFirst();
		if( span != null ) {
			span.stop(result);
			if( !spans.isEmpty() ) {
				spans.clear();
				sendToTrace(span);
			}
		}
	}
	
	public void stopped(Span span) {

		if( span == spans.peekLast() ) {
			spans.removeLast();
			if( spans.isEmpty() ) {
				Trace.getAdapter().send(this, span);
				return;
			}
		} else {
			if( span == spans.peekFirst() ) {
				spans.clear();
				sendToTrace(span);
				return;
			} else if( spans.isEmpty() ) {
				sendToTrace(span);
				return;
			}
		}
	}
	
	private void sendToTrace(Span span) {
		stopAsync(span);
		Trace.getAdapter().send(this, span);
	}
	
	private void stopAsync(Span span) {
		((DefaultSpan)span).stopAsyncIfNeeded();
		if( span.getChildren() != null ) {
			for(Span child:span.getChildren()) {
				((DefaultSpan)child).stopAsyncIfNeeded(); 
			}
		}		
	}

	public String getRemoteAddr() {
		String peers = trace.getPeers();
		if( isEmpty(peers) ) return "0.0.0.0:0";
		int p = peers.lastIndexOf(",");
		if( p >= 0 ) return peers.substring(p+1);
		return peers;
	}
	
	public String getRemoteAppName() {
		String apps = trace.getPeers();
		if( isEmpty(apps) ) return "client";
		int p = apps.lastIndexOf(",");
		if( p >= 0 ) return apps.substring(p+1);
		return apps;
	}
	
	public Span currentSpan() {
		Span tail = spans.peekLast();
		return tail;
	}

	void addTags(Span rootSpan, String tags) {
		String[] ss = tags.split("&");
		for(String s: ss) {
			String[] tt = s.split("=");
			String key = tt[0];
			String value = decodeValue(tt[1]);
			rootSpan.tag(key, value);
		}
	}
	
	String decodeValue(String value) {
		try {
			return URLDecoder.decode(value,"utf-8");
		} catch(Exception e) {
			return value;
		}
	}
	
	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}
	
	public String getTraceId() {
		return trace.getTraceId();
	}

	public String getRootParentSpanId() {
		return trace.getParentSpanId();
	}

	public String getRootSpanId() {
		return trace.getSpanId();
	}
			
	public int getSampleFlag() {
		return trace.getSampleFlag();
	}

	public long getRequestTimeMicros() {
		return requestTimeMicros;
	}

	public long getStartMicros() {
		return startMicros;
	}

	public long getThreadId() {
		return threadId;
	}

	public String getThreadName() {
		return threadName;
	}

	public String getThreadGroupName() {
		return threadGroupName;
	}

	public RpcMeta.Trace getTrace() {
		return trace;
	}
} 
