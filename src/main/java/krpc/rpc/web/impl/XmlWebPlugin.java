package krpc.rpc.web.impl;

import krpc.common.Json;
import krpc.common.Plugin;
import krpc.rpc.web.*;

import java.util.Map;

public class XmlWebPlugin implements WebPlugin, RenderPlugin {

    String xmlField = "xml";

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        String s = params.get("xmlField");
        if (s != null && !s.isEmpty())
            xmlField = s;
    }

    public void render(WebContextData ctx, WebReq req, WebRes res) {
        String content = res.getStringResult(xmlField);
        if (content == null || content.isEmpty()) {
            String json = Json.toJson(res.getResults());
            res.setContent(json);
            res.setContentType(WebConstants.MIMETYPE_JSON );
            return;
        }
        res.setContent(content);
        res.setContentType("text/xml; charset=utf-8");
    }

}
