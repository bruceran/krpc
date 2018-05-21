package krpc.core;

import java.util.concurrent.atomic.AtomicInteger;

import krpc.core.proto.RpcMeta;

public class RpcServerContextData  extends RpcContextData {
	
	Continue<RpcClosure> cont; // used in server side async call
	AtomicInteger subCalls = new AtomicInteger();
	
	public RpcServerContextData(String connId,RpcMeta meta) {
		super(connId,meta);
	}
	
	public int nextCall() {
		return subCalls.incrementAndGet();
	}

	public Continue<RpcClosure> getContinue() {
		return cont;
	}

	public void setContinue(Continue<RpcClosure> cont) {
		this.cont = cont;
	}

}
