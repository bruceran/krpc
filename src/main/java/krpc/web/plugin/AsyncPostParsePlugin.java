package krpc.web.plugin;

import krpc.core.Continue;
import krpc.web.WebReq;

public interface AsyncPostParsePlugin {
	 void asyncPostParse(int serviceId,int msgId,WebReq req,Continue<Integer> cont);
}
