package krpc.rpc.web.impl;

import java.util.Map;

import krpc.rpc.core.Plugin;
import krpc.rpc.web.RenderPlugin;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebReq;
import krpc.rpc.web.WebRes;

public class PlainTextWebPlugin implements WebPlugin, RenderPlugin {
	
	String key = "plainText";
	
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("key");
		if ( s != null && !s.isEmpty() )
			key = s;				
	}
	
	public void render(WebContextData ctx,WebReq req,WebRes res) {
		String content = res.getStringResult(key);
		if( content == null ) content = "";
		res.setContent(content);
		res.setContentType("text/plain");
	}
	
}
