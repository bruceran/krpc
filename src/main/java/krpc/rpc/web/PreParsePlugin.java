package krpc.rpc.web;

public interface PreParsePlugin {
	int preParse(int serviceId,int msgId,WebReq req);
}
