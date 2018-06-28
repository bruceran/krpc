package krpc.rpc.web;

import java.util.ArrayList;
import java.util.List;

public class WebPlugins {

	WebPlugin[] plugins;
	
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

	public WebPlugins(WebPlugin[] plugins) {
		this.plugins = plugins;

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

	public WebPlugin[] getPlugins() {
		return plugins;
	}

}
