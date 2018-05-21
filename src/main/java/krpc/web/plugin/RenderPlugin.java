package krpc.web.plugin;

import krpc.web.WebContextData;
import krpc.web.WebReq;
import krpc.web.WebRes;

public interface RenderPlugin {
	void render(WebContextData ctx,WebReq req,WebRes res); // generate httpCode,headers,cookies,content from results
}
