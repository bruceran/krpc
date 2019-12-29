package krpc.rpc.core;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import krpc.trace.Trace;

public class RpcClosure {

    RpcContextData ctx;
    Object req;
    Object res;
    boolean isRaw = false;

    public RpcClosure(RpcContextData ctx, Object req, boolean isRaw) {
        this.ctx = ctx;
        this.req = req;
        this.isRaw = req == null ? isRaw : req instanceof ByteBuf;
    }

    public RpcClosure(RpcContextData ctx, Message req, Message res) {
        this.ctx = ctx;
        this.req = req;
        this.res = res;
        isRaw = false;
        ctx.setRetCode(getRetCodeInner());
        ctx.end();
    }

    // used only in DefaultMonitorService
    public RpcClosure(RpcClosure rawRpcClosure, Message req, Message res) {
        this.ctx = rawRpcClosure.getCtx();
        this.req = req;
        this.res = res;
        isRaw = false;
    }

    public boolean isRaw() { return isRaw; }

    private int getRetCodeInner() {
        if (res == null) return 0;
        if (!(res instanceof Message)) return 0;
        return ReflectionUtils.getRetCode(res);
    }

    public void restoreContext() {
        if (ctx instanceof ServerContextData) {
            ServerContextData sctx = (ServerContextData) ctx;
            ServerContext.set(sctx);
            Trace.restoreContext(sctx.getTraceContext());
        }
    }

    public void done(Message res) {
        if (this.res != null) return;
        this.res = res;
        ctx.setRetCode(getRetCodeInner());
        ctx.end();
        if (ctx instanceof ServerContextData) {
            ServerContextData sctx = (ServerContextData) ctx;
            if (sctx.cont != null)
                sctx.cont.readyToContinue(this);
        }
    }

    public void done(RpcRawMessage rawRes) {
        if (this.res != null) return;
        this.res = rawRes.getRes();
        ctx.setRetCode(rawRes.getRetCode());
        ctx.end();
        if (ctx instanceof ServerContextData) {
            ServerContextData sctx = (ServerContextData) ctx;
            if (sctx.cont != null)
                sctx.cont.readyToContinue(this);
        }
    }

    public int getRetCode() {
        return ctx.getRetCode();
    }

    public String getRetMsg() {
        if (res == null) return "";
        if( isRaw ) return "";
        return ReflectionUtils.getRetMsg(res);
    }

    public RpcContextData getCtx() {
        return ctx;
    }

    public ClientContextData asClientCtx() {
        return (ClientContextData) ctx;
    }

    public ServerContextData asServerCtx() {
        return (ServerContextData) ctx;
    }

    public Object reqData() {
        return req;
    }

    public Object resData() {
        return res;
    }

    public Message getReq() {
        if( isRaw ) return null;
        return (Message)req;
    }

    public Message getRes() {
        if( isRaw ) return null;
        return (Message)res;
    }

    public ByteBuf asReqByteBuf() {
        if( !isRaw ) return null;
        return (ByteBuf)req;
    }

    public ByteBuf asResByteBuf() {
        if( !isRaw ) return null;
        return (ByteBuf)res;
    }

    public Message asReqMessage() {
        if( isRaw ) return null;
        return (Message)req;
    }

    public Message asResMessage() {
        if( isRaw ) return null;
        return (Message)res;
    }

    public <T extends Message> T asReq() {
        if( isRaw ) return null;
        return (T)req;
    }

    public <T extends Message> T asRes() {
        if( isRaw ) return null;
        return (T)res;
    }

}
