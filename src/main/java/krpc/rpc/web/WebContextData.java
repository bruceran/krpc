package krpc.rpc.web;

import krpc.rpc.core.ServerContextData;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.TraceContext;

import java.util.Map;

public class WebContextData extends ServerContextData {

    WebRoute route;
    String sessionId;
    Map<String, String> session;

    public WebContextData(String connId, RpcMeta meta, WebRoute route, TraceContext traceContext) {
        super(connId, meta, traceContext);
        this.route = route;
    }

    public WebRoute getRoute() {
        return route;
    }

    public void setRoute(WebRoute route) {
        this.route = route;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, String> getSession() {
        return session;
    }

    public void setSession(Map<String, String> session) {
        this.session = session;
    }

}
