package krpc.rpc.web;

public interface PreParsePlugin {
    int preParse(WebContextData ctx, WebReq req);
}
