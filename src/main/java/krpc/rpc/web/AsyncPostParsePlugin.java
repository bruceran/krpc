package krpc.rpc.web;

import krpc.rpc.core.Continue;

public interface AsyncPostParsePlugin {
	 void asyncPostParse(int serviceId,int msgId,WebReq req,Continue<Integer> cont);
}
