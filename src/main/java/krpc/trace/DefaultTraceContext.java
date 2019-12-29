package krpc.trace;

import krpc.common.RetCodes;
import krpc.rpc.core.proto.RpcMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultTraceContext implements TraceContext {

    RpcMeta.Trace trace;

    long requestTimeMicros = System.currentTimeMillis() * 1000;
    long startMicros = System.nanoTime() / 1000;
    AtomicInteger subCalls = new AtomicInteger();
    Deque<Span> stack = new ArrayDeque<>();
    Map<String, String> tagsForRpc;

    long threadId;
    String threadName = "";
    String threadGroupName = "";

    String timeUsedStr = "";

    public DefaultTraceContext(RpcMeta.Trace trace, boolean restoreFlag) {
        initThreadNames();
        this.trace = trace;
        if (!restoreFlag) return; // web server

        SpanIds newSpanIds = Trace.getAdapter().restore(trace.getParentSpanId(), trace.getSpanId());

        if (newSpanIds != null) {
            RpcMeta.Trace.Builder traceBuilder = trace.toBuilder();
            traceBuilder.setParentSpanId(newSpanIds.getParentSpanId());
            traceBuilder.setSpanId(newSpanIds.getSpanId());
            this.trace = traceBuilder.build();
        }

    }

    public DefaultTraceContext() {
        initThreadNames();

        RpcMeta.Trace.Builder traceBuilder = RpcMeta.Trace.newBuilder();

        TraceIds ids = Trace.getAdapter().newStartTraceIds(false);
        traceBuilder.setTraceId(ids.getTraceId());
        traceBuilder.setParentSpanId(ids.getParentSpanId());
        traceBuilder.setSpanId(ids.getSpanId());

        traceBuilder.setSampleFlag(Trace.getSampleFlag());

        this.trace = traceBuilder.build();
    }

    public TraceContext detach() {
        DefaultTraceContext newCtx = new DefaultTraceContext();
        newCtx.trace = this.trace; // trace_id 不变
        newCtx.requestTimeMicros = this.requestTimeMicros;
        newCtx.startMicros = this.startMicros;
        newCtx.threadId = this.threadId;
        newCtx.threadName = this.threadName;
        newCtx.threadGroupName = this.threadGroupName;
        newCtx.subCalls = this.subCalls;
        if( this.tagsForRpc != null ) {
            newCtx.tagsForRpc = new LinkedHashMap<>(this.tagsForRpc);
        }
        // donot copy stack and timeUsedStr
        return newCtx;
    }

    public Span startForServer(String type, String action) {
        SpanIds spanIds = new SpanIds(trace.getParentSpanId(), trace.getSpanId());
        Span rootSpan = new DefaultSpan(this, spanIds, type, action, startMicros, subCalls);

        synchronized (stack) {
            stack.addLast(rootSpan);
        }

        if (!isEmpty(trace.getTags())) {
            parseTags(rootSpan, trace.getTags());
        }

        return rootSpan;
    }

    // create a span
    public Span startAsync(String type, String action) {
        Span tail;
        synchronized (stack) {
            tail = stack.peekLast();
        }
        if (tail == null) {
            SpanIds childIds = Trace.getAdapter().newChildSpanIds(trace.getSpanId(), subCalls);
            return new DefaultSpan(this, childIds, type, action, -1, subCalls);
        } else {
            return tail.newChild(type, action);
        }
    }

    // append a finished span
    public Span appendSpan(String type, String action,long startMicros, String status, long timeUsedMicros) {
        Span tail;
        synchronized (stack) {
            tail = stack.peekLast();
        }
        DefaultSpan span;
        if (tail == null) {
            SpanIds childIds = Trace.getAdapter().newChildSpanIds(trace.getSpanId(), subCalls);
            span = new DefaultSpan(this, childIds, type, action, startMicros, subCalls);
        } else {
            span = (DefaultSpan)tail.newChild(type, action, startMicros);
        }
        span.stopWithTime(status, timeUsedMicros);
        return span;
    }

    // push the new span to the stack top
    public Span start(String type, String action) {
        Span child = startAsync(type, action);
        synchronized (stack) {
            stack.addLast(child);
        }
        return child;
    }


    public Span rootSpan()  {
        Span span;
        synchronized (stack) {
            span = stack.peekFirst();
        }
        return span;
    }

    public Span stopForServer(int retCode) {
        return stopForServer(retCode,null);
    }

    public Span stopForServer(int retCode,String retMsg) {

        boolean hasError = Trace.getAdapter().hasError(retCode);
        String result = !hasError ? "SUCCESS" : "ERROR";

        Span span;
        synchronized (stack) {
            span = stack.peekFirst();
            stack.clear();
        }

        if (span != null) {

            span.tag("retCode",String.valueOf(retCode));
            if( retCode != 0 ) {
                if (retMsg == null) {
                    retMsg = RetCodes.retCodeText(retCode);
                }
                span.tag("retMsg", retMsg);
            }

            span.stopForServer(result);
            statsTimeUsed(span);
            sendToTrace(span);
        }

        return span;
    }

    public void removeFromStack(Span span, boolean isRootSpan) {

        boolean needSendToTrace = false;

        synchronized (stack) {
            if (span == stack.peekLast()) {
                stack.removeLast();
            } else if (span == stack.peekFirst()) {
                stack.clear();
            }
            if (stack.isEmpty()) {
                needSendToTrace = true;
            }
        }

        if( !isRootSpan ) { // 被嵌套的span不发给cat
            return;
        }

        if (needSendToTrace) {
            sendToTrace(span);
        }

    }

    private void sendToTrace(Span span) {
//        stopAsync(span);
        doSend(span);
    }

    private void statsTimeUsed(Span span) {
        if( span == null ) return;
        timeUsedStr = span.statsTimeUsed();
    }

    private void doSend(Span span) {
        if (trace.getSampleFlag() == 2) return; // ignore
        Trace.getAdapter().send(this, span);
    }

    void parseTags(Span rootSpan, String tags) {
        String[] ss = tags.split("&");
        for (String s : ss) {
            String[] tt = s.split("=");
            String key = tt[0];
            String value = decodeValue(tt[1]);
            rootSpan.tag(key, value);
            tagForRpc(key, value);
        }
    }

    public void tagForRpc(String key, String value) {
        if (tagsForRpc == null) tagsForRpc = new HashMap<>();
        tagsForRpc.put(key, value);
    }

    public void tagForRpcIfAbsent(String key, String value) {
        if (getTagForRpc(key) != null) return;
        tagForRpc(key, value);
    }

    public String getTagForRpc(String key) {
        if (tagsForRpc == null) return null;
        return tagsForRpc.get(key);
    }

    public String getTagsForRpc() {
        if (tagsForRpc == null) return null;
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> entry : tagsForRpc.entrySet()) {
            if (b.length() > 0) b.append("&");
            b.append(entry.getKey()).append("=").append(encodeValue(entry.getValue()));
        }
        return b.toString();
    }

    public Map<String,String> getTagsMapForRpc() {
        if (tagsForRpc == null) return null;
        return tagsForRpc;
    }

    void initThreadNames() {

        if (!Trace.getAdapter().needThreadInfo()) return;

        Thread t = Thread.currentThread();
        threadId = t.getId();
        threadName = t.getName();
        threadGroupName = t.getThreadGroup().getName();
    }

    String decodeValue(String value) {
        try {
            return URLDecoder.decode(value, "utf-8");
        } catch (Exception e) {
            return value;
        }
    }

    String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, "utf-8");
        } catch (Exception e) {
            return value;
        }
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
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

    public String getTimeUsedStr() {
        return timeUsedStr;
    }
}
