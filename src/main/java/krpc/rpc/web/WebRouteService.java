package krpc.rpc.web;

public interface WebRouteService {

	void addUrl(WebUrl url);
	WebRoute findRoute(String host,String path,String method);

	void addDir(WebDir dir);
	String findStaticFile(String host,String path);
	String findTemplate(String host,String path,String templateName);
	String findUploadDir(String host,String path);
	
}
