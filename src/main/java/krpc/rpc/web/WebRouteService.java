package krpc.rpc.web;

import java.io.File;

public interface WebRouteService {

    void addUrl(WebUrl url);

    WebRoute findRoute(String host, String path, String method);

    void addDir(WebDir dir);

    File findStaticFile(String host, String path);

    String findTemplateDir(String host, String path);

}
