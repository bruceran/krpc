package krpc.web;

import java.util.Map;

import krpc.web.plugin.WebPlugin;

public class Route {
	
	public static final int SESSION_MODE_NO = 0;
	public static final int SESSION_MODE_ID = 1;
	public static final int SESSION_MODE_OPTIONAL = 2;
	public static final int SESSION_MODE_YES = 3;
	
	int serviceId;
	int msgId;
	int sessionMode = SESSION_MODE_NO;
	Map<String,String>  attrs;
	WebPlugin[] plugins;
	Map<String,String> variables; // parsed from path

	public Route(int serviceId,int msgId) {
		this.serviceId = serviceId;
		this.msgId = msgId;
	}
	
	public boolean needLoadSession() {
		return sessionMode == SESSION_MODE_OPTIONAL || sessionMode == SESSION_MODE_YES ;
	}
	
	public String getAttribute(String name) {
		if( attrs == null ) return null;
		return attrs.get(name);
	}
	
	public int getServiceId() {
		return serviceId;
	}

	public Route setServiceId(int serviceId) {
		this.serviceId = serviceId;
		return this;
	}

	public int getMsgId() {
		return msgId;
	}

	public Route setMsgId(int msgId) {
		this.msgId = msgId;
		return this;
	}

	public WebPlugin[] getPlugins() {
		return plugins;
	}

	public Route setPlugins(WebPlugin[] plugins) {
		this.plugins = plugins;
		return this;
	}

	public Map<String, String> getVariables() {
		return variables;
	}

	public Route setVariables(Map<String, String> variables) {
		this.variables = variables;
		return this;
	}
	public int getSessionMode() {
		return sessionMode;
	}

	public Route setSessionMode(int sessionMode) {
		this.sessionMode = sessionMode;
		return this;
	}	
}
