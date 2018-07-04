package krpc.rpc.web.impl;

import java.util.Map;

import krpc.common.Json;
import krpc.rpc.core.Plugin;
import krpc.rpc.web.RenderPlugin;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebReq;
import krpc.rpc.web.WebRes;

public class PlainTextWebPlugin implements WebPlugin, RenderPlugin {
	
	String plainTextField = "plainText";
	
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("plainTextField");
		if ( s != null && !s.isEmpty() )
			plainTextField = s;				
	}
	
	public void render(WebContextData ctx,WebReq req,WebRes res) {
		String content = res.getStringResult(plainTextField);
		if( content == null || content.isEmpty()   ) {
			String json = Json.toJson(res.getResults());
			res.setContent(json);
			res.setContentType("application/json");
			return;			
		}		
		res.setContent(content);
		res.setContentType("text/plain");
	}
	
}
