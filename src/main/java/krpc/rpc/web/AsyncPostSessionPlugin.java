package krpc.rpc.web;

import krpc.rpc.core.Continue;

public interface AsyncPostSessionPlugin {
	void asyncPostSession(WebContextData ctx,WebReq req,Continue<Integer> cont);
}
