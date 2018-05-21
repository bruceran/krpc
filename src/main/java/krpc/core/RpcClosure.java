package krpc.core;

import com.google.protobuf.Message;

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
	
	public void done(Message res) {
		if( this.res != null ) return;
		this.res = res;
		ctx.end();
		if( ctx instanceof RpcServerContextData ) {
			RpcServerContextData sctx = (RpcServerContextData)ctx;
			if( sctx.cont != null )
				sctx.cont.readyToContinue(this);
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
	
	public void initThreadLocalContext() {
		if( ctx instanceof RpcServerContextData ) {
			RpcServerContextData sctx = (RpcServerContextData)ctx;
			RpcServerContext.set(sctx);
		}
	}

	public RpcContextData getCtx() {
		return ctx;
	}

	public RpcClientContextData asClientCtx() {
		return (RpcClientContextData)ctx;
	}
	
	public RpcServerContextData asServerCtx() {
		return (RpcServerContextData)ctx;
	}
	
	public Message getReq() {
		return req;
	}

	public Message getRes() {
		return res;
	}
	
}
