package krpc.rpc.web;

public interface ParserPlugin {
    int parse(WebContextData ctx, WebReq req);
}
