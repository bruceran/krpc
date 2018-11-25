package krpc.trace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public interface Span {

    public Span newChild(String type, String action);

    public long stop();

    public long stop(boolean ok);

    public long stop(String status);

    public void logEvent(String type, String action, String status, String data);

    public void logException(Throwable c);

    public void logException(String message, Throwable c);

    public void tag(String key, String value);

    public void incCount(String key);

    public void incQuantity(String key, long value);

    public void incSum(String key, double value);

    public void incQuantitySum(String key, long v1, double v2);

    public void setRemoteAddr(String addr);

    public Span getRootSpan();

    public String getRootSpanId();

    public SpanIds getSpanIds();

    public String getParentSpanId();

    public String getSpanId();

    public String getType();

    public String getAction();

    public long getStartMicros();

    public long getTimeUsedMicros();

    public String getStatus();

    public String getRemoteAddr();

    public List<Span> getChildren();

    public List<Event> getEvents();

    public Map<String, String> getTags();

    public List<Metric> getMetrics();

    public AtomicInteger getCompleted();

    public void removeTag(String key);

    public void removeTags();
}
