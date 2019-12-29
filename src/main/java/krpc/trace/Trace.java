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
    private static ThreadLocal<TraceContext> tlContext = new ThreadLocal<>();
    private static Random rand = new Random();

    public static int getSampleFlag() {
        int sampleFlag = rand.nextInt(100) < Trace.getSampleRate() ? 0 : 2; // 1 not used by now
        return sampleFlag;
    }

    public static Span startForServer(RpcMeta.Trace trace, String type, String action) {
        TraceContext ctx = adapter.newTraceContext(trace, type.equals("RPCSERVER"));
        setCurrentContext(ctx);
        return ctx.startForServer(type, action);
    }

    public static Span start(String type, String action) {
        TraceContext ctx = getOrNew();
        return ctx.start(type, action);
    }

    public static Span startAsync(String type, String action) {
        TraceContext ctx = getOrNew();
        return ctx.startAsync(type, action);
    }

    public static Span appendSpan(String type, String action,long startMicros, String status, long timeUsedMicros) {
        TraceContext ctx = getOrNew();
        return ctx.appendSpan(type, action,startMicros,status,timeUsedMicros);
    }

    private static TraceContext getOrNew() {
        TraceContext ctx = tlContext.get();
        if (ctx != null) return ctx;
        ctx = adapter.newTraceContext();
        setCurrentContext(ctx);
        return ctx;
    }

    public static void tagForRpc(String key, String value) {
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

    public static TraceIds newStartTraceIds(boolean isServerSide) {
        return adapter.newStartTraceIds(isServerSide);
    }

    public static void inject(TraceContext ctx, Span span, RpcMeta.Trace.Builder traceBuilder) {
        adapter.inject(ctx, span, traceBuilder);
    }

    public static TraceContext currentContext() {
        return tlContext.get();
    }

    @Deprecated
    public static void setCurrentContext(TraceContext traceContext) {
        tlContext.set(traceContext);
    }

    @Deprecated
    public static void clearCurrentContext() {
        tlContext.set(null);
    }

    public static void restoreContext(TraceContext traceContext) {
        tlContext.set(traceContext);
    }

    public static void restoreDetachedContext(TraceContext traceContext) {
        if( traceContext == null ) {
            tlContext.set(null);
            return;
        }
        tlContext.set(traceContext.detach());
    }

    public static void clearContext() {
        tlContext.set(null);
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


    public static int getSampleRate() {
        return sampleRate;
    }

    public static void setSampleRate(int sampleRate) {
        Trace.sampleRate = sampleRate;
    }

}
