package krpc.rpc.impl;

import krpc.common.Alarm;
import krpc.common.NamedThreadFactory;
import krpc.rpc.core.DumpPlugin;
import krpc.rpc.core.RpcClosure;
import krpc.rpc.core.ServerContext;
import krpc.rpc.core.ServerContextData;
import krpc.rpc.util.IpUtils;
import krpc.trace.Trace;
import krpc.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TracablePool implements DumpPlugin  {

    private static final Logger log = LoggerFactory.getLogger(TracablePool.class);

    static final String queueFullPrompt = "tracable_pool queue is full";

    int threads = 0;
    int maxThreads = 0;
    int queueSize = 1000;
    String name = "";

    public static Alarm alarm;

    NamedThreadFactory threadFactory;
    ThreadPoolExecutor pool;

    static AtomicInteger cnt = new AtomicInteger();

    int poolId = 0;

    public void init() {

        poolId = cnt.incrementAndGet();

        if( name.isEmpty() ) {
            name = "tracable_pool_" + poolId;
        } else if( !name.startsWith("tracable_pool_")) {
            name = "tracable_pool_" + name;
        }

        int processors = Runtime.getRuntime().availableProcessors();
        if( threads <= 0 ) threads = processors;
        if( maxThreads <= 0 ) maxThreads = threads;
        threadFactory = new NamedThreadFactory(name);
        pool = new ThreadPoolExecutor(threads, maxThreads, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueSize), threadFactory);
        pool.prestartAllCoreThreads();

        log.info("tracable_pool inited");
    }

    public void close() {
        pool.shutdown();

        log.info("tracable_pool closed");
    }


    public void post(Runnable r) {
        postWithResult(r);
    }

    public boolean postWithResult(Runnable r) {

        TraceContext traceContext = Trace.currentContext();
        long t1 = System.nanoTime()/1000;

        try {
            pool.execute(new Runnable() {
                public void run() {

                    Trace.restoreDetachedContext(traceContext);

                    if( traceContext != null ) {
                        long ts = System.nanoTime()/1000 - t1;
                        if( ts >= 10000 ) {
                            Trace.appendSpan("TRACABLEPOOLQ","WAIT",t1,"SUCCESS",ts);
                        }
                    }

                    try {
                        r.run();
                    } catch(Exception e) {
                        log.error("business exception",e);
                    }
                }
            });

            return true;
        } catch(Exception e) {
            log.error(queueFullPrompt,e);
            if( alarm != null ) {
                alarm.alarm(Alarm.ALARM_TYPE_TRACABLE_POOL, queueFullPrompt, "tracable_pool_queue", IpUtils.localIp());
            }
            // 不再抛出异常
            return false;
        }

    }


    public CompletableFuture<Void> future(Runnable r) {

        CompletableFuture<Void> f = new CompletableFuture<>();

        try {

            ServerContextData d = ServerContext.get();
            RpcClosure closure = d != null ? RpcFutureUtils.createRpcClosure() : null;
            TraceContext traceContext = d != null ? null : Trace.currentContext();

            long t1 = System.nanoTime()/1000;

            pool.execute(new Runnable() {
                public void run() {

                    if( closure != null ) {
                        closure.restoreContext();
                    } else if( traceContext != null ){
                        ServerContext.remove();
                        Trace.restoreContext(traceContext);
                    } else {
                        ServerContext.remove();
                        Trace.clearContext();
                    }

                    if( closure != null || traceContext != null ) {
                        long ts = System.nanoTime()/1000 - t1;
                        if( ts >= 10000 ) {
                            Trace.appendSpan("TRACABLEPOOLQ","WAIT",t1,"SUCCESS",ts);
                        }
                    }

                    try {
                        r.run();
                        f.complete(null);
                    } catch(Exception e) {
                        log.error("business exception",e);
                        f.completeExceptionally(e);
                    }

                }
            });

        } catch(Exception e) {
            log.error(queueFullPrompt,e);
            if( alarm != null ) {
                alarm.alarm(Alarm.ALARM_TYPE_TRACABLE_POOL, queueFullPrompt, "tracable_pool_queue", IpUtils.localIp());
            }
            f.completeExceptionally(e);
        }
        return f;
    }

    public <T> CompletableFuture<T> future(Callable<T> r) {

        CompletableFuture<T> f = new CompletableFuture<>();

        try {
            ServerContextData d = ServerContext.get();
            RpcClosure closure = d != null ? RpcFutureUtils.createRpcClosure() : null;
            TraceContext traceContext = d != null ? null : Trace.currentContext();

            long t1 = System.nanoTime()/1000;

            pool.execute(new Runnable() {
                public void run() {

                    if( closure != null ) {
                        closure.restoreContext();
                    } else if( traceContext != null ){
                        ServerContext.remove();
                        Trace.restoreContext(traceContext);
                    } else {
                        ServerContext.remove();
                        Trace.clearContext();
                    }

                    if( closure != null || traceContext != null ) {
                        long ts = System.nanoTime()/1000 - t1;
                        if( ts >= 10000 ) {
                            Trace.appendSpan("TRACABLEPOOLQ","WAIT",t1,"SUCCESS",ts);
                        }
                    }

                    try {
                        T v = r.call();
                        f.complete(v);
                    } catch(Exception e) {
                        log.error("business exception",e);
                        f.completeExceptionally(e);
                    }

                }
            });


        } catch(Exception e) {
            log.error(queueFullPrompt,e);
            if( alarm != null ) {
                alarm.alarm(Alarm.ALARM_TYPE_TRACABLE_POOL, queueFullPrompt, "tracable_pool_queue", IpUtils.localIp());
            }
            f.completeExceptionally(e);
        }

        return f;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void dump(Map<String, Object> map) {
        map.put("tracable_pool["+name+"].activeCount", pool.getActiveCount());
        map.put("tracable_pool["+name+"].waitingInQueue", pool.getQueue().size());
    }

}
