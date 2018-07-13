package krpc.trace;

public class TraceIds {

    private String traceId;
    private String parentSpanId;
    private String spanId;

    public TraceIds(String traceId, String parentSpanId, String spanId) {
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.spanId = spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

}
