package krpc.rpc.web.impl;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.rpc.web.*;

import java.util.Map;

public class ServerRedirectWebPlugin implements WebPlugin, RenderPlugin {

    String redirectUrlField = "redirectUrl";

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("redirectUrlField");
        if (s != null && !s.isEmpty())
            redirectUrlField = s;
    }

    public void render(WebContextData ctx, WebReq req, WebRes res) {
        String redirectUrl = res.getStringResult(redirectUrlField);
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            String json = Json.toJson(res.getResults());
            res.setContent(json);
            res.setContentType("application/json");
            return;
        }
        res.setHeader("location", redirectUrl);
        res.setHttpCode(302);
    }

}
