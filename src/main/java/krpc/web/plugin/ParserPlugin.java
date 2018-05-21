package krpc.web.plugin;

import krpc.web.WebReq;

public interface ParserPlugin {
	int parse(int serviceId,int msgId,WebReq req);
}
