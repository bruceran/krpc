package krpc.rpc.web.impl;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.rpc.web.*;

import java.util.Map;

public class JsRedirectWebPlugin implements WebPlugin, RenderPlugin {

    String redirectUrlField = "redirectUrl";

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("redirectUrlField");
        if (s != null && !s.isEmpty())
            redirectUrlField = s;
    }

    String htmlStart = "<!DOCTYPE html><html><body><script language=\"javascript\">window.location.href=\"";
    String htmlEnd = "\"</script></body></html>";

    public void render(WebContextData ctx, WebReq req, WebRes res) {
        String redirectUrl = res.getStringResult(redirectUrlField);
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            String json = Json.toJson(res.getResults());
            res.setContent(json);
            res.setContentType("application/json");
            return;
        }
        String content = htmlStart + redirectUrl + htmlEnd;
        res.setContent(content);
        res.setContentType("text/html");
    }

}
