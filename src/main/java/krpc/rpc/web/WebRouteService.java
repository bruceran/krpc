package krpc.rpc.web;

public interface WebRouteService {

	void addUrl(WebUrl url);
	WebRoute findRoute(String host,String path,String method);

	void addDir(WebDir dir);
	
	String findStaticFile(String host,String path);
	String findTemplateDir(String host,String path);
	String findUploadDir(String host,String path);
	
}
