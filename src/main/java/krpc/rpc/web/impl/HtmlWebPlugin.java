package krpc.rpc.web.impl;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.rpc.web.*;

import java.util.Map;

public class HtmlWebPlugin implements WebPlugin, RenderPlugin {

    String htmlField = "html";

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("htmlField");
        if (s != null && !s.isEmpty())
            htmlField = s;
    }

    public void render(WebContextData ctx, WebReq req, WebRes res) {
        String content = res.getStringResult(htmlField);
        if (content == null || content.isEmpty()) {
            String json = Json.toJson(res.getResults());
            res.setContent(json);
            res.setContentType("application/json");
            return;
        }
        res.setContent(content);
        res.setContentType("text/html; charset=utf-8");
    }

}
