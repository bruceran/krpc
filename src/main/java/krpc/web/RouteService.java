package krpc.web;

public interface RouteService {

	void addUrl(WebUrl url);
	Route findRoute(String host,String path,String method);

	void addDir(WebDir dir);
	String findStaticFile(String host,String path);
	String findTemplate(String host,String path,String templateName);
	String findUploadDir(String host,String path);
	
}
