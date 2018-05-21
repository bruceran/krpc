package krpc.web.plugin;

import krpc.web.WebContextData;
import krpc.web.WebReq;
import krpc.web.WebRes;

public interface PostRenderPlugin {
	void postRender(WebContextData ctx,WebReq req,WebRes res);  // adjust httpCode,headers,cookies,content before send to network
}
