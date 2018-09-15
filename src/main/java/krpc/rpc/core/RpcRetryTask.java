package krpc.rpc.core;

public class RpcRetryTask {

    int serviceId;
    int msgId;

    Object message;

    int maxTimes = 0;
    int[] waitSeconds;

    int timeout = -1;
    String attachement = null;

    long timestamp =  System.currentTimeMillis();

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public int getMsgId() {
        return msgId;
    }

    public void setMsgId(int msgId) {
        this.msgId = msgId;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public int getMaxTimes() {
        return maxTimes;
    }

    public void setMaxTimes(int maxTimes) {
        this.maxTimes = maxTimes;
    }

    public int[] getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int[] waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }


    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getAttachement() {
        return attachement;
    }

    public void setAttachement(String attachement) {
        this.attachement = attachement;
    }
}
