package krpc.rpc.web;

public interface ParserPlugin {
	int parse(int serviceId,int msgId,WebReq req);
}
