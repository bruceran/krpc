package krpc.web.plugin;

import krpc.core.Continue;
import krpc.web.WebContextData;
import krpc.web.WebReq;

public interface AsyncPostSessionPlugin {
	void asyncPostSession(WebContextData ctx,WebReq req,Continue<Integer> cont);
}
