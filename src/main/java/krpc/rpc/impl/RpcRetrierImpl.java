package krpc.rpc.impl;

import com.google.protobuf.Message;
import krpc.common.*;
import krpc.persistqueue.PersistQueue;
import krpc.persistqueue.impl.PersistQueueManagerImpl;
import krpc.rpc.core.*;
import krpc.rpc.util.MapToMessage;
import krpc.rpc.util.MessageToMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RpcRetrierImpl implements RpcRetrier, InitClose, StartStop {

    private static final Logger log = LoggerFactory.getLogger(RpcRetrierImpl.class);

    private String dataDir;

    NamedThreadFactory threadFactory = new NamedThreadFactory("krpc_retrier");
    ConcurrentHashMap<String, RetryContext> queueNameMap = new ConcurrentHashMap<>();

    static class RetryContext {
        String queueName;
        PersistQueue queue;
        long idx;
        RpcRetryTask task;
        int maxTimes = 0;
        int retryIndex = 0;
    }

    private String retryQueueDir;
    private PersistQueueManagerImpl persistQueueManager;

    ScheduledExecutorService executor;

    AtomicBoolean shutdownFlag = new AtomicBoolean();

    RpcCallable rpcCallable;
    ServiceMetas serviceMetas;

    public void init() {
        if (isEmpty(dataDir)) throw new RuntimeException("data dir is not valid");
        retryQueueDir = dataDir + "/rpc_retrier";

        executor = Executors.newSingleThreadScheduledExecutor(threadFactory);

        persistQueueManager = new PersistQueueManagerImpl();
        persistQueueManager.setDataDir(retryQueueDir);
        persistQueueManager.setCacheSize(20);

        try {
            persistQueueManager.init();
        } catch (IOException e) {
            throw new RuntimeException("persistQueueManager cannot be started, reason = " + e.getMessage());
        }

        List<String> queueNames = persistQueueManager.getQueueNames();
        for (String queueName : queueNames) {
            queueNameMap.put(queueName, new RetryContext() );
        }

        log.info("rpc_retrier inited");
    }

    public void close() {
        shutdownFlag.set(true);

        executor.shutdown();

        if (persistQueueManager != null) {
            persistQueueManager.close();
            persistQueueManager = null;
        }

        log.info("rpc_retrier closed");
    }

    public void start() {
        List<String> queueNames = persistQueueManager.getQueueNames();
        for (String queueName : queueNames) {
            startRetryQueue(queueName);
        }

        log.info("rpc_retrier started");
    }

    public void stop() {

    }

    void startRetryQueue(String queueName) {

        if( shutdownFlag.get() ) return;

        executor.execute(() -> {
            retryQueue(queueName);
        });

    }

    void retryQueue(String queueName)  {
        RetryContext ctx = queueNameMap.get(queueName);
        if( ctx == null ) return;

        ctx.queueName = queueName;
        try {
            ctx.queue = persistQueueManager.getQueue(queueName);
        } catch (Exception e) {
            log.error("persistQueueManager.getQueue io exception, queueName=" + queueName);
            executor.schedule( () -> {
                startRetryQueue(queueName);
            },30,TimeUnit.SECONDS);
        }
        readNext(ctx);
    }

    private void readNext(RetryContext ctx) {

        for(;;) {

            if( shutdownFlag.get() ) return;

            try {

                ctx.idx = ctx.queue.get(0); // no wait
                if (ctx.idx == -1) { // no data
                    executor.schedule( ()-> {
                        readNext(ctx);
                    },1,TimeUnit.SECONDS);
                    return;
                }

                String json = ctx.queue.getString(ctx.idx);
                if (isEmpty(json)) {
                    ctx.queue.commit(ctx.idx);
                    continue;
                }

                ctx.task = Json.toObject(json, RpcRetryTask.class);
                if (ctx.task == null) {
                    log.error("map json not valid");
                    ctx.queue.commit(ctx.idx);
                    continue;
                }

                try {
                    mapToPbMessage(ctx.task);
                } catch (Exception e) {
                    log.error("map json not valid, not pb message");
                    ctx.queue.commit(ctx.idx);
                    continue;
                }

                ctx.maxTimes = ctx.task.getMaxTimes();
                if (ctx.maxTimes <= 0) ctx.maxTimes = Integer.MAX_VALUE;
                ctx.retryIndex = 0;

                long now = System.currentTimeMillis();
                long receivedTs = ctx.task.getTimestamp();
                int diff = (int) (ctx.task.getWaitSeconds()[0] * 1000 - (now - receivedTs));
                if (diff > 0) {
                    executor.schedule( ()-> {
                        sendOnce(ctx);
                    },diff,TimeUnit.MILLISECONDS);
                } else {
                    sendOnce(ctx);
                }

                return;

            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                log.error("queue read io exception, queueName=" + ctx.queueName + ",e=" + e.getMessage());
                executor.schedule( () -> {
                            startRetryQueue(ctx.queueName);
                        },30,TimeUnit.SECONDS);
                return;
            }
        }
    }

    void sendOnce(RetryContext ctx) {

        if( shutdownFlag.get() ) return;

        RpcRetryTask task = ctx.task;

        if( task.getTimeout() > 0 ) ClientContext.setTimeout(task.getTimeout());
        if( task.getAttachement() != null ) ClientContext.setAttachment(task.getAttachement());
        CompletableFuture<Message> future = rpcCallable.callAsync(task.getServiceId(),task.getMsgId(),(Message)task.getMessage());
        future.thenAccept( (res) -> {

            ctx.retryIndex += 1;

            int retCode = ReflectionUtils.getRetCode(res);
            if (isDone(retCode) || ctx.retryIndex >= ctx.maxTimes  ) {
                ctx.queue.commit(ctx.idx);
                readNext(ctx);
                return;
            }

            int seconds = 0;
            if( ctx.retryIndex >= task.getWaitSeconds().length ) {
                seconds = task.getWaitSeconds()[task.getWaitSeconds().length - 1];
            } else {
                seconds = task.getWaitSeconds()[ctx.retryIndex];
            }

            executor.schedule( ()-> {
                sendOnce(ctx);
            },seconds,TimeUnit.SECONDS);

        });

    }

    public boolean isDone(int retCode) {
        if(  retCode == 0 ) return true;
        if( !RetCodes.canRecover(retCode) ) return true; // ignore
        return false;
    }

    public boolean submit(int retCode, RpcRetryTask task) {

        if( isDone(retCode) ) return true;

        if (task.getWaitSeconds() == null) {
            task.setWaitSeconds(  new int[] {1} );
        }

        boolean ok = persistTask(task);
        if (!ok) return false; // disk io error  TODO

        String queueName = task.getServiceId() + "_" + task.getMsgId();
        if (!queueNameMap.containsKey(queueName)) {
            RetryContext old = queueNameMap.putIfAbsent(queueName, new RetryContext() );
            if( old == null ) {
                startRetryQueue(queueName);
            }
        }

        return true;
    }

    boolean persistTask(RpcRetryTask task) {

        pbMessageToMap(task);

        String json = Json.toJson(task);

        String queueName = task.getServiceId() + "_" + task.getMsgId();
        try {
            PersistQueue queue = persistQueueManager.getQueue(queueName);
            queue.put(json);
            return true;
        } catch (IOException e) {
            log.error("cannot persist retrytask, queueName={}", queueName);
            return false;
        }

    }

    void pbMessageToMap(RpcRetryTask task) {
        Map<String, Object> map = new HashMap<>();
        MessageToMap.parseMessage((Message) task.getMessage(), map);
        map.put("pbMessageCls", task.getMessage().getClass().getName());
        task.setMessage(map);
    }

    void mapToPbMessage(RpcRetryTask task) {
        Map<String, Object> map = (Map<String, Object>) task.getMessage();
        String pbMessageCls = (String) map.get("pbMessageCls");
        if (pbMessageCls != null) {
            Class cls = null;
            try {
                cls = Class.forName(pbMessageCls);
            } catch (Exception e) {
                throw new RuntimeException("invalid message, pbMessageCls="+pbMessageCls);
            }
            Message.Builder b = ReflectionUtils.generateBuilder(cls);
            Object message = MapToMessage.toMessage(b, map);
            task.setMessage( message );
            return;
        }
        throw new RuntimeException("invalid message, pbMessageCls not found");
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public RpcCallable getRpcCallable() {
        return rpcCallable;
    }

    public void setRpcCallable(RpcCallable rpcCallable) {
        this.rpcCallable = rpcCallable;
    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }
}
