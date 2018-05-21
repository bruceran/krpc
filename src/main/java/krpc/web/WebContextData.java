package krpc.web;

import java.util.Map;

import krpc.core.RpcServerContextData;
import krpc.core.proto.RpcMeta;

public class WebContextData extends RpcServerContextData {

	Route route;
	String sessionId;
	Map<String,String> session;

	public WebContextData(String connId,RpcMeta meta,Route route) {
		super(connId,meta);
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
