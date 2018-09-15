package krpc.trace;

import krpc.rpc.core.proto.RpcMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class Trace {

    private static Logger log = LoggerFactory.getLogger(Trace.class);
    private static String appName = "unknown";
    private static int sampleRate = 100;
    private static TraceAdapter adapter = new DummyTraceAdapter();
    private static ThreadLocal<TraceContext> tlContext = new ThreadLocal<TraceContext>();
    private static Random rand = new Random();

    public static int getSampleFlag() {
        int sampleFlag = rand.nextInt(100) < Trace.getSampleRate() ? 0 : 2; // 1 not used by now
        return sampleFlag;
    }

    public static void startForServer(RpcMeta.Trace trace, String type, String action) {
        TraceContext ctx = adapter.newTraceContext(trace, type.equals("RPCSERVER"));
        setCurrentContext(ctx);
        ctx.startForServer(type, action);
    }

    public static void start(String type, String action) {
        TraceContext ctx = getOrNew();
        ctx.start(type, action);
    }

    public static Span startAsync(String type, String action) {
        TraceContext ctx = getOrNew();
        return ctx.startAsync(type, action);
    }

    private static TraceContext getOrNew() {
        TraceContext ctx = tlContext.get();
        if (ctx != null) return ctx;
        ctx = adapter.newTraceContext();
        setCurrentContext(ctx);
        return ctx;
    }

    public static Span currentSpan() {
        TraceContext ctx = tlContext.get();
        if (ctx != null) return ctx.currentSpan();
        return null;
    }

    public static long stop() {
        return stop("SUCCESS");
    }

    public static long stop(boolean ok) {
        return stop(ok ? "SUCCESS" : "ERROR");
    }

    public static long stop(String status) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return 0;
        }
        span.stop(status);
        return span.getTimeUsedMicros();
    }


    public static void logEvent(String type, String action) {
        logEvent(type, action, "SUCCESS", null);
    }

    public static void logEvent(String type, String action, String status, String data) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.logEvent(type, action, status, data);
    }

    public static void logException(Throwable c) {
        logException(null, c);
    }

    public static void logException(String message, Throwable c) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.logException(message, c);
    }

    public static void tag(String key, String value) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.tag(key, value);
    }

    public static void tagForRpc(String key, String value) {

        tag(key, value); // normal tag

        TraceContext ctx = tlContext.get();
        if (ctx == null) return;
        ctx.tagForRpc(key, value); // save to rpc
    }

    public static void tagForRpcIfAbsent(String key, String value) {
        if (getTagForRpc(key) != null) return;
        tagForRpc(key, value);
    }

    public static String getTagForRpc(String key) {
        TraceContext ctx = tlContext.get();
        if (ctx == null) return null;
        return ctx.getTagForRpc(key);
    }

    public static void incCount(String key) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.incCount(key);
    }

    public static void incQuantity(String key, long value) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.incQuantity(key, value);
    }

    public static void incSum(String key, double value) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.incSum(key, value);
    }

    public static void incQuantitySum(String key, long v1, double v2) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.incQuantitySum(key, v1, v2);
    }

    public static void setRemoteAddr(String addr) {
        Span span = currentSpan();
        if (span == null) {
            log.error("span not started");
            return;
        }
        span.setRemoteAddr(addr);
    }

    public static TraceIds newStartTraceIds(boolean isServerSide) {
        return adapter.newStartTraceIds(isServerSide);
    }

    public static void inject(TraceContext ctx, Span span, RpcMeta.Trace.Builder traceBuilder) {
        adapter.inject(ctx, span, traceBuilder);
    }

    public static TraceAdapter getAdapter() {
        return adapter;
    }

    public static void setAdapter(TraceAdapter adapter) {
        Trace.adapter = adapter;
    }

    public static String getAppName() {
        return appName;
    }

    public static void setAppName(String appName) {
        Trace.appName = appName;
    }

    public static TraceContext currentContext() {
        return tlContext.get();
    }

    public static void setCurrentContext(TraceContext traceContext) {
        tlContext.set(traceContext);
    }

    public static void clearCurrentContext() {
        tlContext.remove();
    }

    public static int getSampleRate() {
        return sampleRate;
    }

    public static void setSampleRate(int sampleRate) {
        Trace.sampleRate = sampleRate;
    }

}
