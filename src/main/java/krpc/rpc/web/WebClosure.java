package krpc.rpc.web;

public class WebClosure {

    WebContextData ctx;
    DefaultWebReq req;
    DefaultWebRes res;

    public WebClosure(WebContextData ctx, DefaultWebReq req, DefaultWebRes res) {
        this.ctx = ctx;
        this.req = req;
        this.res = res;
    }

    public WebContextData getCtx() {
        return ctx;
    }

    public DefaultWebReq getReq() {
        return req;
    }

    public DefaultWebRes getRes() {
        return res;
    }

}
