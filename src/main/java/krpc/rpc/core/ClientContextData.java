package krpc.rpc.core;

import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.Span;
import krpc.trace.TraceContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ClientContextData extends RpcContextData {

    CompletableFuture future;  // used in client side sync/async call
    int retryTimes = 0;
    Set<String> excludeAddrs;
    TraceContext traceContext;
    Span span;

    public ClientContextData(String connId, RpcMeta meta, TraceContext traceContext, Span span) {
        super(connId, meta);
        this.traceContext = traceContext;
        this.span = span;

        if (span != null) {
            startMicros = span.getStartMicros();
        } else {
            startMicros = System.nanoTime();
        }
        if (traceContext != null) {
            requestTimeMicros = traceContext.getRequestTimeMicros() + (startMicros - traceContext.getStartMicros());
        } else {
            requestTimeMicros = System.currentTimeMillis() * 1000;
        }
    }

    public void incRetryTimes(String connId) {
        retryTimes++;
        if (excludeAddrs == null) excludeAddrs = new HashSet<>();
        excludeAddrs.add(getAddr(connId));
    }

    String getAddr(String connId) {
        int p = connId.lastIndexOf(":");
        return connId.substring(0, p);
    }

    public CompletableFuture getFuture() {
        return future;
    }

    public void setFuture(CompletableFuture future) {
        this.future = future;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public Span getSpan() {
        return span;
    }

    public Set<String> getExcludeAddrs() {
        return excludeAddrs;
    }

    public void setExcludeAddrs(Set<String> excludeAddrs) {
        this.excludeAddrs = excludeAddrs;
    }

}
