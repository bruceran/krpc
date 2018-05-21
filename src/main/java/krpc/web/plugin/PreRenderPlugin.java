package krpc.web.plugin;

import krpc.web.WebContextData;
import krpc.web.WebReq;
import krpc.web.WebRes;

public interface PreRenderPlugin {
	void preRender(WebContextData ctx,WebReq req,WebRes res); // adjust results before render
}
