package krpc.rpc.web;

import krpc.rpc.core.Continue;

public interface AsyncPostParsePlugin {
	 void asyncPostParse(WebContextData ctx,WebReq req,Continue<Integer> cont);
}
