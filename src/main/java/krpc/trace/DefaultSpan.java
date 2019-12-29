package krpc.trace;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultSpan implements Span {

    private Object parent; // may be a DefaultSpan or DefaultTraceContext
    private SpanIds spanIds;
    private String type;
    private String action;
    long startMicros;

    String remoteAddr;
    Map<String, String> tags;
    List<Event> events;
    List<Metric> metrics;

    Object childrenLock = new Object();
    List<DefaultSpan> children; // 可能被其它线程读写

    volatile long timeUsedMicros;
    volatile String status = "unset";

    AtomicInteger subCalls;

    DefaultSpan(Object parent, SpanIds spanIds, String type, String action, long startMicros, AtomicInteger parentSubCalls) {
        this.parent = parent;
        this.spanIds = spanIds;
        this.type = type;
        this.action = action;
        if (startMicros <= 0) this.startMicros = System.nanoTime() / 1000;
        else this.startMicros = startMicros;

        if (Trace.getAdapter().useCtxSubCalls()) {
            subCalls = parentSubCalls;
        }
    }

    public Span newChild(String type, String action) {
        return newChild(type,action,-1);
    }

    public Span newChild(String type, String action, long startMicros) {
        if (subCalls == null) subCalls = new AtomicInteger();
        SpanIds childIds = Trace.getAdapter().newChildSpanIds(spanIds.getSpanId(), subCalls);
        DefaultSpan child = new DefaultSpan(this, childIds, type, action, startMicros, subCalls);
        synchronized (childrenLock) {
            if (children == null) children = new ArrayList<>();
            children.add(child);
        }
        return child;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("parentSpanId=").append(spanIds.getParentSpanId()).append(",");
        b.append("spanId=").append(spanIds.getSpanId()).append(",");
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
        return stop(ok ? "SUCCESS" : "ERROR");
    }

    public long stop(String status) {
        return stopWithTime(status,System.nanoTime()/1000 - startMicros);
    }

    public long stopWithTime(String status,long timeUsedMicros) {
        this.status = status;
        this.timeUsedMicros = timeUsedMicros;

        DefaultTraceContext ctx = getContext();
        ctx.removeFromStack(this, parent == ctx);

        return timeUsedMicros;
    }

    public long stopForServer(String status) {
        long t = System.nanoTime()/1000 - startMicros;
        this.status = status;
        this.timeUsedMicros = t;
        return t;
    }

    public void logEvent(String type, String action, String status, String data) {
        if (events == null) events = new ArrayList<>();
        Event e = new Event(type, action, status, data);
        events.add(e);
    }

    public void logException(Throwable cause) {
        logException(null, cause);
    }

    public void logException(String message, Throwable cause) {
        if (events == null) events = new ArrayList<>();
        StringWriter sw = new StringWriter(1024);
        if (message != null) {
            sw.write(message);
            sw.write(' ');
        }
        cause.printStackTrace(new PrintWriter(sw));
        logEvent("Exception", cause.getClass().getName(), "ERROR", sw.toString());
    }

    public void tag(String key, String value) {
        if (tags == null) tags = new LinkedHashMap<>();
        tags.put(key, value);
    }

    public void removeTag(String key) {
        if (tags == null) return;
        tags.remove(key);
    }

    public void incCount(String key) {
        if (metrics == null) metrics = new ArrayList<>();
        metrics.add(new Metric(key, Metric.COUNT, "1"));
    }

    public void incQuantity(String key, long value) {
        if (metrics == null) metrics = new ArrayList<>();
        metrics.add(new Metric(key, Metric.QUANTITY, String.valueOf(value)));
    }

    public void incSum(String key, double value) {
        if (metrics == null) metrics = new ArrayList<>();
        String s = String.format("%.2f", value);
        metrics.add(new Metric(key, Metric.SUM, s));
    }

    public void incQuantitySum(String key, long v1, double v2) {
        if (metrics == null) metrics = new ArrayList<>();
        String s = String.format("%d,%.2f", v1, v2);
        metrics.add(new Metric(key, Metric.QUANTITY_AND_SUM, s));
    }

    public Span getRootSpan() {
        if (parent instanceof DefaultSpan) {
            return ((DefaultSpan) parent).getRootSpan();
        } else {
            return this;
        }
    }

    public String getRootSpanId() {
        if (parent instanceof DefaultSpan) {
            return ((DefaultSpan) parent).getRootSpanId();
        } else {
            return spanIds.getSpanId();
        }
    }

    public DefaultTraceContext getContext() {
        if (parent instanceof DefaultSpan) {
            return ((DefaultSpan) parent).getContext();
        } else {
            return (DefaultTraceContext) parent;
        }
    }

    public String statsTimeUsed() {

        synchronized (childrenLock) {
            if ( children != null) {
                StringBuilder b = new StringBuilder();
                long sum = 0;
                for (DefaultSpan child : children ) {
                    String type = child.getType();
                    long t = child.getTimeUsedMicros();
                    sum += t;
                    if( b.length() > 0 ) b.append("^");
                    b.append(type).append(":").append(t);
                }
                b.append("^IOSUM").append(":").append(sum);
                return b.toString();
            } else {
                return "";
            }
        }

    }

    public List<Span> getChildren() {
        synchronized (childrenLock) {
            if( children == null ) return null;
            return new ArrayList<>(children);
        }
    }

    public void setRemoteAddr(String addr) {
        this.remoteAddr = addr;
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
        return spanIds.getParentSpanId();
    }

    public String getSpanId() {
        return spanIds.getSpanId();
    }

    public SpanIds getSpanIds() {
        return spanIds;
    }

    public List<Metric> getMetrics() {
        return metrics;
    }

    public void removeTags() {
        tags = null;
    }

    public void changeAction(String action) {
        this.action = action;
    }
}
