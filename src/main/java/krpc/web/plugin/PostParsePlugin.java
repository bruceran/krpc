package krpc.web.plugin;

import krpc.web.WebReq;

public interface PostParsePlugin {
	int postParse(int serviceId,int msgId,WebReq req);
}
