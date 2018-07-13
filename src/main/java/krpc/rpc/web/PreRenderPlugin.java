package krpc.rpc.web;

public interface PreRenderPlugin {
    void preRender(WebContextData ctx, WebReq req, WebRes res); // adjust results before render
}
