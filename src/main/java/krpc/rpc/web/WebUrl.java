package krpc.rpc.web;

import java.util.Map;

public class WebUrl {

    String hosts;
    String path;
    String methods;
    int serviceId;
    int msgId;
    int sessionMode = WebRoute.SESSION_MODE_NO;
    WebPlugins plugins;
    Map<String, String> attrs;
    String origins;

    public WebUrl(String hosts, String path) {
        this.hosts = hosts;
        this.path = path;
    }

    public WebUrl(String hosts, String path, String methods, int serviceId, int msgId) {
        this.hosts = hosts;
        this.path = path;
        this.methods = methods.toLowerCase();
        this.serviceId = serviceId;
        this.msgId = msgId;
    }

    public String getPath() {
        return path;
    }

    public WebUrl setPath(String path) {
        this.path = path;
        return this;
    }

    public int getServiceId() {
        return serviceId;
    }

    public WebUrl setServiceId(int serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public int getMsgId() {
        return msgId;
    }

    public WebUrl setMsgId(int msgId) {
        this.msgId = msgId;
        return this;
    }

    public int getSessionMode() {
        return sessionMode;
    }

    public WebUrl setSessionMode(int sessionMode) {
        this.sessionMode = sessionMode;
        return this;
    }

    public String getHosts() {
        return hosts;
    }

    public WebUrl setHosts(String hosts) {
        this.hosts = hosts;
        return this;
    }

    public String getMethods() {
        return methods;
    }

    public WebUrl setMethods(String methods) {
        this.methods = methods;
        return this;
    }

    public WebPlugins getPlugins() {
        return plugins;
    }

    public WebUrl setPlugins(WebPlugins plugins) {
        this.plugins = plugins;
        return this;
    }

    public Map<String, String> getAttrs() {
        return attrs;
    }

    public WebUrl setAttrs(Map<String, String> attrs) {
        this.attrs = attrs;
        return this;
    }

    public String getOrigins() {
        return origins;
    }

    public WebUrl setOrigins(String origins) {
        this.origins = origins;
        return this;
    }
}
