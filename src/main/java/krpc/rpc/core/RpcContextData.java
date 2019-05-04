package krpc.rpc.core;

import krpc.rpc.core.proto.RpcMeta;

import java.util.HashMap;
import java.util.Map;

abstract public class RpcContextData {

    protected String connId;
    RpcMeta meta;
    long requestTimeMicros; // in micros
    long startMicros;
    long timeUsedMicros;
    Map<String, Object> attributes;
    int retCode;

    public RpcContextData(String connId, RpcMeta meta) {
        this.connId = connId;
        this.meta = meta;
    }

    public long elapsedMillisByNow() {
        return (System.nanoTime() / 1000 - startMicros) / 1000;
    }

    public void end() {
        timeUsedMicros = System.nanoTime() / 1000 - startMicros;
    }

    public long getResponseTimeMicros() {
        return requestTimeMicros + timeUsedMicros;
    }

    public long getTimeUsedMicros() {
        return timeUsedMicros;
    }

    public long getTimeUsedMillis() {
        return timeUsedMicros / 1000;
    }

    public String getClientIp() {
        String peers = meta.getTrace().getPeers();
        if (peers.isEmpty()) return getRemoteIp();
        String s = peers.split(",")[0];
        int p = s.indexOf(":");
        if( p >= 0 ) return s.substring(0,p);
        return s;
    }

    public String getRemoteIp() {
        String remoteAddr = getRemoteAddr(); // to support ipv6
        int p = remoteAddr.lastIndexOf(":");
        return remoteAddr.substring(0, p);
    }

    public String getRemoteAddr() {
        int p = connId.lastIndexOf(":");
        return connId.substring(0, p);
    }

    public RpcMeta getMeta() {
        return meta;
    }

    public String getConnId() {
        return connId;
    }

    public long getRequestTimeMicros() {
        return requestTimeMicros;
    }

    public long getStartMicros() {
        return startMicros;
    }

    public void setConnId(String connId) {
        this.connId = connId;
    }

    public void setMeta(RpcMeta meta) {
        this.meta = meta;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        if (this.attributes == null) this.attributes = new HashMap<String, Object>();
        this.attributes = attributes;
    }

    public void setAttribute(String key, Object obj) {
        if (this.attributes == null) this.attributes = new HashMap<String, Object>();
        this.attributes.put(key, obj);
    }

    public Object getAttribute(String key) {
        if (this.attributes == null) return null;
        return this.attributes.get(key);
    }

    public void removeAttribute(String key) {
        if (this.attributes == null) return;
        this.attributes.remove(key);
    }

    public int getRetCode() {
        return retCode;
    }

    public void setRetCode(int retCode) {
        this.retCode = retCode;
    }
}
