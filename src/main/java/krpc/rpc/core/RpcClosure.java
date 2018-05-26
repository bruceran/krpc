package krpc.rpc.core;

import com.google.protobuf.Message;

import krpc.trace.Trace;

public class RpcClosure {
	
	RpcContextData ctx;
	Message req;
	Message res;
	
	public RpcClosure(RpcContextData ctx,Message req) {
		this.ctx = ctx;
		this.req = req;
	}
	public RpcClosure(RpcContextData ctx,Message req,Message res) {
		this.ctx = ctx;
		this.req = req;
		this.res = res;
		ctx.end();
	}
	
	public void recoverContext() {
		if( ctx instanceof ServerContextData ) {
			ServerContextData sctx = (ServerContextData)ctx;
			ServerContext.set(sctx);
			Trace.setCurrentContext(sctx.getTraceContext());
		}
	}
	
	public void done(Message res) {
		if( this.res != null ) return;
		this.res = res;
		ctx.end();
		if( ctx instanceof ServerContextData ) {
			ServerContextData sctx = (ServerContextData)ctx;
			if( sctx.cont != null )
				sctx.cont.readyToContinue(this);
			
			String status = getRetCode() == 0 ? "SUCCESS" : "ERROR";
			sctx.getTraceContext().serverSpanStopped(status);
		}
	}
	
	public int getRetCode() {
		if( res == null ) return 0;
		return ReflectionUtils.getRetCode(res);
	}

	public String getRetMsg() {
		if( res == null ) return "";		
		return ReflectionUtils.getRetMsg(res);
	}

	public RpcContextData getCtx() {
		return ctx;
	}

	public ClientContextData asClientCtx() {
		return (ClientContextData)ctx;
	}
	
	public ServerContextData asServerCtx() {
		return (ServerContextData)ctx;
	}
	
	public Message getReq() {
		return req;
	}

	public Message getRes() {
		return res;
	}
	
}
