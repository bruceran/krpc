package krpc.rpc.web;

import java.util.ArrayList;
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

	List<PreParsePlugin> preParsePlugins;
	List<AsyncPreParsePlugin> asyncPreParsePlugins;
	ParserPlugin parserPlugin;   // only one
	List<PostParsePlugin> postParsePlugins;
	List<AsyncPostParsePlugin> asyncPostParsePlugins;
	SessionService sessionServicePlugin;  // only one
	List<PostSessionPlugin> postSessionPlugins;
	List<AsyncPostSessionPlugin> asyncPostSessionPlugins;
	List<PreRenderPlugin> preRenderPlugins;
	RenderPlugin renderPlugin;   // only one
	List<PostRenderPlugin> postRenderPlugins;
	
	public WebRoute(int serviceId,int msgId) {
		this.serviceId = serviceId;
		this.msgId = msgId;
	}

	public WebRoute setPlugins(WebPlugin[] plugins) {
		if( plugins != null ) {
			for(WebPlugin p: plugins) {
				 if( p instanceof PreParsePlugin ) {
					 if( preParsePlugins == null ) preParsePlugins = new ArrayList<>();
					 preParsePlugins.add((PreParsePlugin)p);
				 }
				 if( p instanceof AsyncPreParsePlugin ) {
					 if( asyncPreParsePlugins == null ) asyncPreParsePlugins = new ArrayList<>();
					 asyncPreParsePlugins.add((AsyncPreParsePlugin)p);
				 }
				 if( p instanceof ParserPlugin ) {
					 if( parserPlugin == null ) parserPlugin = (ParserPlugin)p;
				 }
				 if( p instanceof PostParsePlugin ) {
					 if( postParsePlugins == null ) postParsePlugins = new ArrayList<>();
					 postParsePlugins.add((PostParsePlugin)p);
				 }
				 if( p instanceof AsyncPostParsePlugin ) {
					 if( asyncPostParsePlugins == null ) asyncPostParsePlugins = new ArrayList<>();
					 asyncPostParsePlugins.add((AsyncPostParsePlugin)p);
				 }
				 if( p instanceof SessionService ) {
					 if( sessionServicePlugin == null ) sessionServicePlugin = (SessionService)p; // only one
				 }
				 if( p instanceof PostSessionPlugin ) {
					 if( postSessionPlugins == null ) postSessionPlugins = new ArrayList<>();
					 postSessionPlugins.add((PostSessionPlugin)p);
				 }
				 if( p instanceof AsyncPostSessionPlugin ) {
					 if( asyncPostSessionPlugins == null ) asyncPostSessionPlugins = new ArrayList<>();
					 asyncPostSessionPlugins.add((AsyncPostSessionPlugin)p);
				 }		
				 if( p instanceof PreRenderPlugin ) {
					 if( preRenderPlugins == null ) preRenderPlugins = new ArrayList<>();
					 preRenderPlugins.add((PreRenderPlugin)p);
				 }		
				 if( p instanceof RenderPlugin ) {
					 if( renderPlugin == null ) renderPlugin = (RenderPlugin)p;
				 }		
				 if( p instanceof PostRenderPlugin ) {
					 if( postRenderPlugins == null ) postRenderPlugins = new ArrayList<>();
					 postRenderPlugins.add((PostRenderPlugin)p);
				 }						 
			}
		}
		
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
		return preParsePlugins;
	}
 
	public List<PostParsePlugin> getPostParsePlugins() {
		return postParsePlugins;
	}
 

	public List<AsyncPostParsePlugin> getAsyncPostParsePlugins() {
		return asyncPostParsePlugins;
	}
 
	public SessionService getSessionServicePlugin() {
		return sessionServicePlugin;
	}
 

	public List<PostSessionPlugin> getPostSessionPlugins() {
		return postSessionPlugins;
	}
 
	public List<AsyncPostSessionPlugin> getAsyncPostSessionPlugins() {
		return asyncPostSessionPlugins;
	}
 
	public List<PreRenderPlugin> getPreRenderPlugins() {
		return preRenderPlugins;
	}
 
	public List<PostRenderPlugin> getPostRenderPlugins() {
		return postRenderPlugins;
	}

	public ParserPlugin getParserPlugin() {
		return parserPlugin;
	}

	public RenderPlugin getRenderPlugin() {
		return renderPlugin;
	}

	public List<AsyncPreParsePlugin> getAsyncPreParsePlugins() {
		return asyncPreParsePlugins;
	}

	public Map<String, String> getAttrs() {
		return attrs;
	}

	public void setAttrs(Map<String, String> attrs) {
		this.attrs = attrs;
	}
 
}
