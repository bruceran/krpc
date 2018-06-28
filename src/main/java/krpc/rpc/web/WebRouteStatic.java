package krpc.rpc.web;

public class WebRouteStatic {

	String dir;
	String path;

	public WebRouteStatic(String dir,String path) {
		this.dir = dir;
		this.path = path;
	}
 
	public String getFilename() {
		if( path.startsWith("/") )
			return dir +  path ;
		else 
			return dir + "/" + path ;		
	}

	public String getDir() {
		return dir;
	}

	public String getPath() {
		return path;
	}
	
	
}
