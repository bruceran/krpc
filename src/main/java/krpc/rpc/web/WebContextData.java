package krpc.rpc.web;

import java.util.Map;

import krpc.rpc.core.ServerContextData;
import krpc.rpc.core.proto.RpcMeta;
import krpc.trace.TraceContext;

public class WebContextData extends ServerContextData {

	Route route;
	String sessionId;
	Map<String,String> session;

	public WebContextData(String connId,RpcMeta meta,Route route,TraceContext traceContext) {
		super(connId,meta,traceContext);
		this.route = route;
	}

	public Route getRoute() {
		return route;
	}

	public void setRoute(Route route) {
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
