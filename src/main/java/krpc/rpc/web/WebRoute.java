package krpc.rpc.web;

import java.util.List;
import java.util.Map;

public class WebRoute {
	
	public static final int SESSION_MODE_NO = 0;
	public static final int SESSION_MODE_ID = 1;
	public static final int SESSION_MODE_OPTIONAL = 2;
	public static final int SESSION_MODE_YES = 3;
	
	int serviceId;
	int msgId;
	int sessionMode = SESSION_MODE_NO;
	Map<String,String>  attrs;

	Map<String,String> variables; // parsed from path

	WebPlugins plugins;
	
	public WebRoute(int serviceId,int msgId) {
		this.serviceId = serviceId;
		this.msgId = msgId;
	}

	public WebRoute setPlugins(WebPlugins plugins) {
		this.plugins = plugins;
		return this;
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

	public WebRoute setServiceId(int serviceId) {
		this.serviceId = serviceId;
		return this;
	}

	public int getMsgId() {
		return msgId;
	}

	public WebRoute setMsgId(int msgId) {
		this.msgId = msgId;
		return this;
	}

	public Map<String, String> getVariables() {
		return variables;
	}

	public WebRoute setVariables(Map<String, String> variables) {
		this.variables = variables;
		return this;
	}
	public int getSessionMode() {
		return sessionMode;
	}

	public WebRoute setSessionMode(int sessionMode) {
		this.sessionMode = sessionMode;
		return this;
	}

	public List<PreParsePlugin> getPreParsePlugins() {
		return plugins == null ? null : plugins.preParsePlugins;
	}
 
	public List<PostParsePlugin> getPostParsePlugins() {
		return plugins == null ? null : plugins.postParsePlugins;
	}
 
	public List<AsyncPostParsePlugin> getAsyncPostParsePlugins() {
		return plugins == null ? null : plugins.asyncPostParsePlugins;
	}
 
	public SessionService getSessionServicePlugin() {
		return plugins == null ? null : plugins.sessionServicePlugin;
	}
 
	public List<PostSessionPlugin> getPostSessionPlugins() {
		return plugins == null ? null : plugins.postSessionPlugins;
	}
 
	public List<AsyncPostSessionPlugin> getAsyncPostSessionPlugins() {
		return plugins == null ? null : plugins.asyncPostSessionPlugins;
	}
 
	public List<PreRenderPlugin> getPreRenderPlugins() {
		return plugins == null ? null : plugins.preRenderPlugins;
	}
 
	public List<PostRenderPlugin> getPostRenderPlugins() {
		return plugins == null ? null : plugins.postRenderPlugins;
	}

	public ParserPlugin getParserPlugin() {
		return plugins == null ? null : plugins.parserPlugin;
	}

	public RenderPlugin getRenderPlugin() {
		return plugins == null ? null : plugins.renderPlugin;
	}

	public List<AsyncPreParsePlugin> getAsyncPreParsePlugins() {
		return plugins == null ? null : plugins.asyncPreParsePlugins;
	}

	public Map<String, String> getAttrs() {
		return attrs;
	}

	public void setAttrs(Map<String, String> attrs) {
		this.attrs = attrs;
	}
 
}
