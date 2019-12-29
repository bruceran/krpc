package krpc.rpc.impl.transport;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import krpc.common.RetCodes;
import krpc.rpc.core.*;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.Span;
import krpc.trace.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class TransportBase extends ChannelDuplexHandler{

    static Logger log = LoggerFactory.getLogger(TransportBase.class);

    TransportCallback callback;
    RpcCodec codec;
    ServiceMetas serviceMetas;
    MonitorService monitorService;
    boolean enableEncrypt = false;

    ConcurrentHashMap<String,String> keyMap = new ConcurrentHashMap<>();

    AtomicBoolean stopFlag = new AtomicBoolean();

    abstract boolean isServerSide();

    abstract String getConnId(ChannelHandlerContext ctx);

    abstract Channel getChannel(String connId);

    public void stop() {
        stopFlag.set(true);
    }

    public boolean isRequest(RpcMeta meta) {
        return meta.getDirection() == RpcMeta.Direction.REQUEST;
    }

    public boolean isHeartBeat(RpcMeta meta) {
        return meta.getServiceId() == 1 && meta.getMsgId() == 1;
    }

    public String getKey(String connId) {
        if( enableEncrypt ) {
            return keyMap.get(connId);
        } else {
            return null;
        }
    }

    // use this method to put key
    public void putKey(String connId,String key) {
        if( enableEncrypt ) {
            keyMap.put(connId,key);
        }
    }
    public boolean send(String connId, RpcData data) {

        if (data == null)
            return false;

        if (stopFlag.get() && isServerSide() ) { // allow response, disallow request
            if (isRequest(data.getMeta()))
                return false;
        }

        Channel ch = getChannel(connId);
        if (ch == null) {
            log.error("connection not found, connId={}", connId);
            return false;
        }

        if (ch.isActive()) {
            ch.writeAndFlush(data);
            return true;
        }

        return false;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof RpcData) {
            RpcData data = (RpcData) msg;

            String connId = getConnId(ctx);
            String key = getKey(connId);
            boolean enc = false;
            if( key != null && isRequest(data.getMeta()) ) {
                ReflectionUtils.updateEncrypt(data.getMeta(),1);
                enc = true;
            }

            int size = enc ? codec.getSize(data) + 16 : codec.getSize(data);
            ByteBuf out = ctx.alloc().buffer(size);

            codec.encode(data, out, key);
            ctx.writeAndFlush(out, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private RpcData decode(ChannelHandlerContext ctx, ByteBuf bb) {

        //debugLog("decode called");
        String connId = getConnId(ctx);
        if (connId == null) {
            log.error("decode rejected, since connId is null");
            return null;
        }

        RpcMeta meta = codec.decodeMeta(bb); // don't catch the exception, close the connection

        if (isHeartBeat(meta)) {
            if (isServerSide() && isRequest(meta)) {
                ByteBuf encoded = ctx.alloc().buffer();
                codec.getResHeartBeat(encoded);
                ctx.writeAndFlush(encoded);
            }
            return null;
        }

        if (stopFlag.get() && isServerSide() ) {
            if (isRequest(meta)) {
                responseError(ctx, connId, meta, RetCodes.SERVER_SHUTDOWN);
                log.info("shutingdown, reject request");
                return null;
            }
        }

        if (isRequest(meta)) {
            ReflectionUtils.adjustPeers(meta, connId);
        }

        boolean isExchange = callback.isExchange(connId,meta);
        try {
            String key = getKey(connId);
            RpcData data;
            if( isExchange )
                data = codec.decodeBodyAsByteBuf(meta, bb, key);
            else
                data = codec.decodeBodyAsMessage(meta, bb, key);
            return data;
        } catch (RpcException ex) {
            if (isRequest(meta)) {
                log.error("decode request error, retCode=" + ex.getRetCode() + ", connId=" + connId);
                responseError(ctx, connId, meta, ex.getRetCode());
                return null;
            } else {
                log.error("decode response error, retCode=" + ex.getRetCode() + ", connId=" + connId);
                return genErrorResponse(ctx, meta, isExchange, ex.getRetCode());
            }
        }

    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        //debugLog("channelRead");
        long receiveMicros = System.nanoTime() / 1000;

        RpcData data = null;
        try {
            data = decode(ctx, (ByteBuf) msg);
            if (data == null) return;
        } finally {
            ReferenceCountUtil.release(msg);
        }

        String connId = getConnId(ctx);
        if (connId == null) {
            log.error("channelRead, connId is null"); // donot send to monitor service
            return;
        }

        if (callback != null) {
            try {
                callback.receive(connId, data, receiveMicros);
            } catch (Exception ex) {
                ctx.close();
                log.error("this line should not be logged, connId=" + connId, ex); // donot send to monitor service
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String connId = getConnId(ctx);
        //log.error("connection exception, connId="+connId+",msg="+cause.toString(),cause);
        log.error("connection exception, connId=" + connId + ",msg=" + cause.toString());
        ctx.close();
    }

    RpcData genErrorResponse(ChannelHandlerContext ctx, RpcMeta meta, boolean isExchange, int retCode) {
        RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE)
                .setServiceId(meta.getServiceId()).setMsgId(meta.getMsgId()).setSequence(meta.getSequence())
                .setRetCode(retCode).build();
        if( isExchange ) {
            return new RpcData(resMeta, Unpooled.EMPTY_BUFFER);
        } else {
            Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), retCode);
            return new RpcData(resMeta, res);
        }
    }

    void responseError(ChannelHandlerContext ctx, String connId, RpcMeta meta, int retCode) {
        RpcMeta resMeta = RpcMeta.newBuilder().setDirection(RpcMeta.Direction.RESPONSE)
                .setServiceId(meta.getServiceId()).setMsgId(meta.getMsgId()).setSequence(meta.getSequence())
                .setRetCode(retCode).build();
        ByteBuf encoded = ctx.alloc().buffer();
        codec.encode(new RpcData(resMeta), encoded,null); // just header, no body
        ctx.writeAndFlush(encoded);
        endReq(connId, meta, retCode);
    }

    void endReq(String connId, RpcMeta meta, int retCode) {
        if (monitorService == null) return;

        String action = serviceMetas.getName(meta.getServiceId(), meta.getMsgId());
        Trace.startForServer(meta.getTrace(), "RPCSERVER", action);
        RpcContextData rpcCtx = new ServerContextData(connId, meta, Trace.currentContext());

        Message res = serviceMetas.generateRes(meta.getServiceId(), meta.getMsgId(), retCode);
        boolean isRaw = serviceMetas.isExchangeServiceId(meta.getServiceId());
        RpcClosure closure = new RpcClosure(rpcCtx, null, isRaw);
        closure.done(res);

        Span span = closure.asServerCtx().getTraceContext().stopForServer(retCode);
        closure.asServerCtx().endWithTime(span.getTimeUsedMicros());

        monitorService.reqDone(closure);
    }

    public String getAddr(String connId) {
        int p = connId.lastIndexOf(":");
        return connId.substring(0, p);
    }

    void debugLog(String msg) {
        if (log.isDebugEnabled())
            log.debug(msg);
    }

    String parseIpPort(String s) {
        int p = s.indexOf("/");

        if (p >= 0)
            return s.substring(p + 1);
        else
            return s;
    }

    public TransportCallback getCallback() {
        return callback;
    }

    public void setCallback(TransportCallback callback) {
        this.callback = callback;
    }

    public RpcCodec getCodec() {
        return codec;
    }

    public void setCodec(RpcCodec codec) {
        this.codec = codec;
    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }

    public MonitorService getMonitorService() {
        return monitorService;
    }

    public void setMonitorService(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    public boolean isEnableEncrypt() {
        return enableEncrypt;
    }

    public void setEnableEncrypt(boolean enableEncrypt) {
        this.enableEncrypt = enableEncrypt;
    }
}
