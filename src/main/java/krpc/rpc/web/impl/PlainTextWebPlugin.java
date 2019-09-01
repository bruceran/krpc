package krpc.rpc.web.impl;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.rpc.web.*;

import java.util.Map;

public class PlainTextWebPlugin implements WebPlugin, RenderPlugin {

    String plainTextField = "plainText";

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("plainTextField");
        if (s != null && !s.isEmpty())
            plainTextField = s;
    }

    public void render(WebContextData ctx, WebReq req, WebRes res) {
        String content = res.getStringResult(plainTextField);
        if (content == null || content.isEmpty()) {
            String json = Json.toJson(res.getResults());
            res.setContent(json);
            res.setContentType(WebConstants.MIMETYPE_JSON );
            return;
        }
        res.setContent(content);
        res.setContentType("text/plain");
    }

}
