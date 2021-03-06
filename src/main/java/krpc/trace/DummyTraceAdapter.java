package krpc.trace;

import krpc.rpc.core.proto.RpcMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DummyTraceAdapter implements TraceAdapter {

    RpcMeta.Trace dummyTrace = RpcMeta.Trace.newBuilder().build();

    TraceIds dummyTraceIds = new TraceIds("", "", "");
    SpanIds dummySpanIds = new SpanIds("", "");

    Span dummySpan = new Span() {

        public Span newChild(String type, String action) {
            return dummySpan;
        }
        public Span newChild(String type, String action,long timeUsedMicros) {
            return dummySpan;
        }

        public long stop() {
            return 0;
        }

        public long stop(boolean ok) {
            return 0;
        }

        public long stop(String result) {
            return 0;
        }

        public long stopForServer(String status) {
            return 0;
        }

        public long stopWithTime(String status,long timeUsedMicros) {return 0;}

        public void logEvent(String type, String action, String status, String data) {
        }

        public void logException(Throwable c) {
        }

        public void logException(String message, Throwable c) {
        }

        public void tag(String key, String value) {
        }

        public void setRemoteAddr(String addr) {
        }

        public Span getRootSpan() {
            return this;
        }

        public String getRootSpanId() {
            return "";
        }

        public SpanIds getSpanIds() {
            return dummySpanIds;
        }

        public String getParentSpanId() {
            return "";
        }

        public String getSpanId() {
            return "";
        }

        public String getType() {
            return "";
        }

        public String getAction() {
            return "";
        }

        public long getStartMicros() {
            return 0;
        }

        public long getTimeUsedMicros() {
            return 0;
        }

        public String getStatus() {
            return "";
        }

        public String getRemoteAddr() {
            return "";
        }

        public Map<String, String> getTags() {
            return null;
        }

        public List<Event> getEvents() {
            return null;
        }

        public List<Span> getChildren() {
            return null;
        }

        public List<Metric> getMetrics() {
            return null;
        }

        public void incCount(String key) {
        }

        public void incQuantity(String key, long value) {
        }

        public void incSum(String key, double value) {
        }

        public void incQuantitySum(String key, long v1, double v2) {
        }

        public void removeTag(String key) {}

        public void removeTags() {}

        public String statsTimeUsed() {return "";}

        public void changeAction(String action) {}
    };

    TraceContext dummyTraceContext = new TraceContext() {

        public Span startForServer(String type, String action) {
            return dummySpan;
        }

        public Span start(String type, String action) {
            return dummySpan;
        }

        public Span startAsync(String type, String action) {
            return dummySpan;
        }
        public Span appendSpan(String type, String action,long startMicros, String status, long timeUsedMicros) {
            return dummySpan;
        }

        public Span rootSpan()  {
            return dummySpan;
        }

        public RpcMeta.Trace getTrace() {
            return dummyTrace;
        }

        public long getThreadId() {
            return 0;
        }

        public String getThreadName() {
            return "";
        }

        public String getThreadGroupName() {
            return "";
        }

        public long getRequestTimeMicros() {
            return 0;
        }

        public long getStartMicros() {
            return 0;
        }

        @Override
        public Map<String, String> getTagsMapForRpc() {
            return null;
        }

        public Span stopForServer(int retCode) {
            return dummySpan;
        }
        public Span stopForServer(int retCode,String retMsg) {
            return dummySpan;
        }

        public void tagForRpc(String key, String value) {

        }

        public void tagForRpcIfAbsent(String key, String value) {

        }

        public String getTagsForRpc() {
            return "";
        }

        public String getTagForRpc(String key) {
            return "";
        }

        public TraceContext detach() {return null; }
    };

    public TraceContext newTraceContext() {
        return dummyTraceContext;
    }

    public TraceContext newTraceContext(RpcMeta.Trace trace, String type, String action) {
        return dummyTraceContext;
    }

    public TraceIds newStartTraceIds(boolean isServer) {
        return dummyTraceIds;
    }

    public SpanIds newChildSpanIds(String spanId, AtomicInteger subCalls) {
        return dummySpanIds;
    }

    public void inject(TraceContext ctx, Span span, RpcMeta.Trace.Builder traceBuilder) {
    }

    public SpanIds restore(String parentSpanId, String spanId) {
        return dummySpanIds;
    }

    public void send(TraceContext ctx, Span span) {
    }

}
