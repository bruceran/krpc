package krpc.rpc.core;

import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.TraceContext;

import java.util.concurrent.atomic.AtomicBoolean;

public class ServerContextData extends RpcContextData {

    Continue<RpcClosure> cont; // used in server side async call
    TraceContext traceContext;
    long decodeMicros = 0;
    long waitInQueueMicros = 0;
    AtomicBoolean replied = new AtomicBoolean(false);

    public ServerContextData(String connId, RpcMeta meta, TraceContext traceContext) {
        super(connId, meta);
        this.traceContext = traceContext;
        startMicros = traceContext.rootSpan().getStartMicros();
        requestTimeMicros = traceContext.getRequestTimeMicros() + (startMicros - traceContext.getStartMicros());
    }

    public Continue<RpcClosure> getContinue() {
        return cont;
    }

    public void setContinue(Continue<RpcClosure> cont) {
        this.cont = cont;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public void afterQueue() {
        waitInQueueMicros = System.nanoTime() / 1000 - startMicros;
    }

    public long getWaitInQueueMicros() { return waitInQueueMicros;}

    public boolean isReplied() { return replied.get(); }

    public boolean setReplied() { return replied.compareAndSet(false,true); }

    public void setDecodeMicros(long decodeMicros) {this.decodeMicros = decodeMicros; }

    public long getDecodeMicros() {
        return decodeMicros;
    }

}
