package krpc.rpc.web;

public interface PostRenderPlugin {
    void postRender(WebContextData ctx, WebReq req, WebRes res);  // adjust httpCode,headers,cookies,content before send to network
}
