package krpc.rpc.web;

import java.util.List;
import java.util.Map;

// interface used only for WebPlugin
public interface WebReq extends WebMessage {

    String getMethodString();

    String getHost();

    String getCookie(String name);

    String getPath();

    String getQueryString();

    WebReq setQueryString(String queryString);

    String getCharSet();

    String getContentType();

    WebReq setContentType(String contentType);

    String getContent();

    WebReq setContent(String content);

    String getHeader(String name);

    WebReq setHeader(String name, String value);

    Map<String, Object> getParameters();

    String getParameter(String name);

    List<String> getParameterList(String name);
}
