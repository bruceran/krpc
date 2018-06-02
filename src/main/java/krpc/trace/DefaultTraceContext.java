package krpc.trace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTraceContext implements TraceContext {

	static Logger log = LoggerFactory.getLogger(DefaultTraceContext.class);
	
	private String traceId;
	private String rootRpcId;
	private String peers = "";
	private String apps = "";
	private int sampled = 0; // 0=yes 1=force 2=no
	
	long requestTimeMicros = System.currentTimeMillis()*1000;
	long startMicros = System.nanoTime()/1000;

	AtomicInteger subCalls = new AtomicInteger(); 
	Deque<Span> spans = new ArrayDeque<Span>();
	
	// for server
	DefaultTraceContext(String traceId,String rpcId,String peers,String apps,int sampled,String type,String action) {
		if( isEmpty(traceId) ) {
			this.traceId = Trace.getAdapter().newTraceId();
		} else {
			this.traceId = traceId;
		}
		if( isEmpty(rpcId) )  {
			this.rootRpcId = Trace.getAdapter().newZeroRpcId(true); 
		} else {
			this.rootRpcId = rpcId;
		}
		this.peers = peers;
		this.apps = apps;
		this.sampled = sampled;
		String entryRpcId = Trace.getAdapter().newEntryRpcId(rootRpcId);
		Span root = new DefaultSpan(this, entryRpcId, type, action, startMicros);
		spans.addLast(root);
	}

	// for client
	DefaultTraceContext() {
		this.traceId = Trace.getAdapter().newTraceId();
		this.rootRpcId = Trace.getAdapter().newZeroRpcId(false);
	}

	// push the new span to the stack top
	public void start(String type,String action) {
		Span tail = spans.peekLast();
		if( tail == null ) {
			String childRpcId = Trace.getAdapter().newChildRpcId(rootRpcId,subCalls);
			tail = new DefaultSpan(this, childRpcId, type, action,-1);
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
			String childRpcId = Trace.getAdapter().newChildRpcId(rootRpcId,subCalls);
			return new DefaultSpan(this, childRpcId, type, action,-1);
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
		if( isEmpty(peers) ) return "0.0.0.0:0";
		int p = peers.lastIndexOf(",");
		if( p >= 0 ) return peers.substring(p+1);
		return peers;
	}
	
	public String getRemoteAppName() {
		if( isEmpty(apps) ) return "client";
		int p = apps.lastIndexOf(",");
		if( p >= 0 ) return apps.substring(p+1);
		return apps;
	}
	
	public Span currentSpan() {
		Span tail = spans.peekLast();
		return tail;
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}
	
	public String getTraceId() {
		return traceId;
	}

	public int getSampled() {
		return sampled;
	}

	public long getRequestTimeMicros() {
		return requestTimeMicros;
	}

	public long getStartMicros() {
		return startMicros;
	}

	public String getRootRpcId() {
		return rootRpcId;
	}
			
} 
