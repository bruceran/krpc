package krpc.web.plugin;

import krpc.web.WebReq;

public interface PreParsePlugin {
	int preParse(int serviceId,int msgId,WebReq req);
}
