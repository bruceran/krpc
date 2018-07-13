package krpc.rpc.web;

public interface PostParsePlugin {
    int postParse(WebContextData ctx, WebReq req);
}
