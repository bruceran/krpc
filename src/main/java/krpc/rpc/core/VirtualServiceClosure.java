package krpc.rpc.core;

public class VirtualServiceClosure {

    int logType;
    int serviceId;
    int msgId;
    String msgName;
    int retCode;
    long responseTimeMicros;
    long timeUsedMicros;

    public static VirtualServiceClosure newServerClosure(int serviceId,int msgId,String msgName) {
        VirtualServiceClosure c = new VirtualServiceClosure(1);
        c.serviceId = serviceId;
        c.msgId = msgId;
        c.msgName = msgName;
        return c;
    }
    public static VirtualServiceClosure newClientClosure(int serviceId,int msgId,String msgName) {
        VirtualServiceClosure c = new VirtualServiceClosure(2);
        c.serviceId = serviceId;
        c.msgId = msgId;
        c.msgName = msgName;
        return c;
    }

    private VirtualServiceClosure(int logType) {
        this.logType = logType;
    }

    public int getLogType() { // 1=server 2=client
        return logType;
    }
    public int getServiceId() {
        return serviceId;
    }
    public int getMsgId() {
        return msgId;
    }
    public String getMsgName() {
        return msgName;
    }
    public int getRetCode() {
        return retCode;
    }
    public long getResponseTimeMicros() {
        return responseTimeMicros;
    }
    public long getTimeUsedMillis() {
        return timeUsedMicros/1000;
    }

    public VirtualServiceClosure setServiceId(int serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public VirtualServiceClosure setMsgId(int msgId) {
        this.msgId = msgId;
        return this;
    }

    public VirtualServiceClosure setRetCode(int retCode) {
        this.retCode = retCode;
        return this;
    }

    public VirtualServiceClosure setResponseTimeMicros(long responseTimeMicros) {
        this.responseTimeMicros = responseTimeMicros;
        return this;
    }

    public VirtualServiceClosure setTimeUsedMicros(long timeUsedMicros) {
        this.timeUsedMicros = timeUsedMicros;
        return this;
    }
}
