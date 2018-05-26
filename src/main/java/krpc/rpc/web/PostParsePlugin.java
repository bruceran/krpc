package krpc.rpc.web;

public interface PostParsePlugin {
	int postParse(int serviceId,int msgId,WebReq req);
}
