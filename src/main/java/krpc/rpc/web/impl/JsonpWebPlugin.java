package krpc.rpc.web.impl;

import java.util.Map;

import krpc.common.Json;
import krpc.rpc.core.Plugin;
import krpc.rpc.web.RenderPlugin;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebReq;
import krpc.rpc.web.WebRes;

public class JsonpWebPlugin implements WebPlugin, RenderPlugin {
	
	String jsonpField = "jsonp";
	String callbackFunction = "callback";
	
	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("jsonpField");
		if ( s != null && !s.isEmpty() )
			jsonpField = s;			
		s = params.get("callback");
		if ( s != null && !s.isEmpty() )
			callbackFunction = s;				
	}
	
	public void render(WebContextData ctx,WebReq req,WebRes res) {
		String callback = req.getParameter(jsonpField);
		if( callback == null || callback.isEmpty() ) callback = this.callbackFunction;
		String json = Json.toJson(res.getResults());
		String javascript = callback+"("+json+")";
		res.setContent(javascript);
		res.setContentType("application/javascript");
	}
	
}
