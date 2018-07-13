package krpc.trace;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultSpan implements Span {

    private Object parent; // may be a DefaultSpan or DefaultTraceContext
    private SpanIds spanIds;
    private String type;
    private String action;
    long startMicros;
    long timeUsedMicros;
    String status = "unset";
    String remoteAddr;
    Map<String, String> tags;
    List<Event> events;
    List<Span> children;
    List<Metric> metrics;
    AtomicInteger subCalls;

    AtomicInteger completed = new AtomicInteger(0); // 0=pending 1=stopped

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
        if (subCalls == null) subCalls = new AtomicInteger();
        SpanIds childIds = Trace.getAdapter().newChildSpanIds(spanIds.getSpanId(), subCalls);
        Span child = new DefaultSpan(this, childIds, type, action, -1, subCalls);
        if (children == null) children = new ArrayList<>();
        children.add(child);
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
        if (!completed.compareAndSet(0, 1)) {
            return 0;
        }
        this.status = status;
        timeUsedMicros = System.nanoTime() / 1000 - startMicros;
        DefaultTraceContext ctx = getContext();
        ctx.stopped(this);
        return getTimeUsedMicros();
    }

    public void stopAsyncIfNeeded() {
        if (!completed.compareAndSet(0, 2)) {
            return;
        }
        this.status = "ASYNC";
        timeUsedMicros = System.nanoTime() / 1000 - startMicros;
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
        if (tags == null) tags = new HashMap<>();
        tags.put(key, value);
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

}
