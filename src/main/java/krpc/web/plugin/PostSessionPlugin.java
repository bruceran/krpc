package krpc.web.plugin;

import krpc.web.WebContextData;
import krpc.web.WebReq;

public interface PostSessionPlugin {
	int postSession(WebContextData ctx,WebReq req);
}
