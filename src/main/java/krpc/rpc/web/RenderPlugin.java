package krpc.rpc.web;

public interface RenderPlugin {
    void render(WebContextData ctx, WebReq req, WebRes res); // generate httpCode,headers,cookies,content from results
}
