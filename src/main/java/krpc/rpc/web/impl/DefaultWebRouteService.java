package krpc.rpc.web.impl;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.common.StartStop;
import krpc.rpc.web.WebRoute;
import krpc.rpc.web.WebRouteService;
import krpc.rpc.web.WebDir;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebPlugins;
import krpc.rpc.web.WebUrl;
import krpc.rpc.web.WebUtils;

public class DefaultWebRouteService implements WebRouteService, InitClose,StartStop {
	
	static Logger log = LoggerFactory.getLogger(DefaultWebRouteService.class);
	
	static class TemplateDirMapping implements Comparable<TemplateDirMapping>  {
		String hosts;
		Set<String> hostSet;
		String path;
		String dir;
		
		TemplateDirMapping(String hosts,String path,String dir) {
			this.hosts = hosts;
			if( !hosts.equals("*") ) {
				hostSet = new HashSet<String>();
				String[] ss = hosts.split(",");
				for(String s:ss ) hostSet.add(s);
			}

			this.path = path;
			this.dir = dir;
		}
		
		public int compareTo(TemplateDirMapping other) {
			return other.path.compareTo(this.path); // reverse order
		}		
	}

	static class StaticDirMapping implements Comparable<StaticDirMapping>  {
		String hosts;
		Set<String> hostSet;
		String path;
		List<String> dirs;
		
		StaticDirMapping(String hosts,String path,List<String> dirs) {
			this.hosts = hosts;
			if( !hosts.equals("*") ) {
				hostSet = new HashSet<String>();
				String[] ss = hosts.split(",");
				for(String s:ss ) hostSet.add(s);
			}

			this.path = path;
			this.dirs = dirs;
		}
		
		public int compareTo(StaticDirMapping other) {
			return other.path.compareTo(this.path); // reverse order
		}		
	}
	
	static class ServiceMapping implements Comparable<ServiceMapping> {
		
		String hosts;
		String originalPath;
		String path;
		boolean[] placeHolderFlags;
		String[] placeHolders;
		String methods;  // * for all
		Set<String> methodSet;
		
		int serviceId;
		int msgId;
		int sessionMode;
		WebPlugins plugins;
		Map<String,String>  attrs;
		
		ServiceMapping(String hosts,String path,String methods,int serviceId,int msgId,
				int sessionMode,WebPlugins plugins,Map<String,String>  attrs) {
			
			this.hosts = hosts;
			this.originalPath = path;

			checkPlaceHolders(path);
			
			int p = path.indexOf("/{");
			if( p >= 0 ) {
				this.path = path.substring(0,p);
				placeHolders = path.substring(p+1).split("/");
				placeHolderFlags = new boolean[placeHolders.length];
				for(int i=0;i< placeHolders.length;++i ) {
					String t = placeHolders[i];
					if( t.startsWith("{") ) {
						placeHolderFlags[i] = true;
						placeHolders[i] = t.substring(1, t.length()-1);
					} 
				}
			} else {
				this.path = path;
			}

			this.methods = methods;
			if( !methods.equals("*") ) {
				methodSet = new HashSet<String>();
				String[] ss = methods.split(",");
				for(String s:ss ) methodSet.add(s.toLowerCase());
			}
			
			this.serviceId = serviceId;
			this.msgId = msgId;
			this.sessionMode = sessionMode;
			this.plugins = plugins;
			this.attrs = attrs;
		}

		private void checkPlaceHolders(String path) {
			String[] tss = path.split("/");
			for(String s:tss) {
				int p1 = s.indexOf("{");
				if( p1 == -1 ) continue;
				if( p1 != 0 )
					throw new RuntimeException("place holder not valid, path="+path);
				int p2 = s.indexOf("}");
				if( p2 != s.length() - 1 ) {
					throw new RuntimeException("place holder not valid, path="+path);
				}			
			}
		}
		
		boolean match(String path,String method,HashMap<String,String> variables) {
			if( !methodSet.contains(method) ) return false;
			if( placeHolders == null ) return true;
			if( path.equals(this.path) ) return false;
			
			String t = path.substring(this.path.length()+1);
			String[] tt = t.split("/");
			if( tt.length < placeHolderFlags.length ) return false;
			
			for( int i=0;i<placeHolderFlags.length;++i) {
				if( !placeHolderFlags[i] ) {
					if( !tt[i].equals(placeHolders[i]) ) return false;
				}
			}
			// match
			for( int i=0;i<placeHolderFlags.length;++i) {
				if( placeHolderFlags[i] ) {
					variables.put(placeHolders[i], tt[i]);
				}
			}
			return true;
		}

		public int compareTo(ServiceMapping other) {
			return this.originalPath.compareTo(other.originalPath);
		}
	}

	static class HostMapping {
		Map<String,List<ServiceMapping>> pathMappings = new HashMap<>();
	}
	
	private String dataDir = ".";
	private String jarCacheDir;
	
	private List<WebUrl> urlList = new ArrayList<WebUrl>();
	private List<WebDir> dirList = new ArrayList<WebDir>();
	
	private List<StaticDirMapping> staticDir = new ArrayList<>();
	private List<TemplateDirMapping> templateDir = new ArrayList<>();

	private Map<String,HostMapping> hostMappings = new HashMap<String,HostMapping>();

	private Map<String,WebPlugin> plugins = new HashMap<>();
	
	public void addUrl(WebUrl url) {
		urlList.add(url);
	}

	public void addDir(WebDir dir) {
		dirList.add(dir);
	}
	
	public void init() {
		

		jarCacheDir = dataDir + "/jarcache";
		
		
		for(WebDir wd: dirList) {

			if( !isEmpty(wd.getStaticDir())) {
				String dir = wd.getStaticDir();
				List<String> dirs = staticDirConvert(dir);
				staticDir.add( new StaticDirMapping(wd.getHosts(),wd.getPath(),dirs) );
			}

			if( !isEmpty(wd.getTemplateDir())) {
				String dir = wd.getTemplateDir();
				templateDir.add( new TemplateDirMapping(wd.getHosts(),wd.getPath(),dir) );
			}

		}
		Collections.sort(staticDir);
		Collections.sort(templateDir);
	
		for(WebUrl url: urlList) {
			
			if( isEmpty(url.getHosts()) ) {
				throw new RuntimeException("host must be specified");
			}
			if( isEmpty(url.getPath()) ) {
				throw new RuntimeException("path must be specified");
			}

			ServiceMapping sm = new ServiceMapping(url.getHosts(),url.getPath(),url.getMethods(),
					url.getServiceId(),url.getMsgId(),
					url.getSessionMode(),url.getPlugins(),url.getAttrs());
			
			String host = url.getHosts();
			String[] ss = host.split(",");
			for(String s: ss ) {
				addHostMapping(s,sm);
			}
			
			if( url.getPlugins() != null ) {
				for(WebPlugin p:url.getPlugins().getPlugins()) {
					plugins.put(p.getClass().getName(), p);
				}
			}

		}
		
		for( HostMapping hm: hostMappings.values() ) {
			for( List<ServiceMapping> lsm: hm.pathMappings.values() ) {
				Collections.sort(lsm);
			}
		}

		for( WebPlugin p:plugins.values() ) {
			InitCloseUtils.init(p);
		}

	}

	void addHostMapping(String host,ServiceMapping sm) {
		HostMapping hm = hostMappings.get(host);
		if( hm == null ) {
			hm = new HostMapping(); 
			hostMappings.put(host,hm);
		}
		
		List<ServiceMapping> lsm =  hm.pathMappings.get(sm.path);
		if( lsm == null ) {
			lsm = new ArrayList<ServiceMapping>(); 
			hm.pathMappings.put(sm.path,lsm);
		}
		
		lsm.add(sm);
	}
	public void start() {

		for( WebPlugin p:plugins.values() ) {
			InitCloseUtils.start(p);
		}		
	}
	
	public void stop() {

		for( WebPlugin p:plugins.values() ) {
			InitCloseUtils.stop(p);
		}		
	}
	
	public void close() {

		for( WebPlugin p:plugins.values() ) {
			InitCloseUtils.close(p);
		}		
	}

	public WebRoute findRoute(String host, String path, String method) {
		return findByHost(host, path, method.toLowerCase());
	}

	private WebRoute findByHost(String host, String path, String method) {
		
		if( method.equals("head") ) method = "get";
		
		HostMapping hm = hostMappings.get(host);
		if (hm == null)
			hm = hostMappings.get("*");
		if (hm == null)
			return null;

		HashMap<String, String> variables = new HashMap<String, String>();
		String t = path;
		while (!t.isEmpty()) {
			List<ServiceMapping> lm = hm.pathMappings.get(t);
			if (lm != null) {
				for (int i=lm.size()-1;i>=0;--i) {
					ServiceMapping sm = lm.get(i);
					if (sm.match(path, method, variables)) {
						WebRoute r = new WebRoute(sm.serviceId, sm.msgId);
						r.setSessionMode(sm.sessionMode);
						r.setPlugins(sm.plugins);
						r.setVariables(variables);
						
						// transfer attributes to plugins 
						
						Map<String,String>  attrs = null;
						String templateDir = findTemplateDir(host,path);
						if( !isEmpty(templateDir) ) {
							attrs = new HashMap<>();
							attrs.put("templateDir", templateDir);
						}
						
						if( sm.attrs != null && sm.attrs.size() > 0 ) {
							if( attrs == null )
								attrs = sm.attrs;
							else
								attrs.putAll(sm.attrs);
						}
						
						if( attrs != null )
							r.setAttrs(attrs);
						
						return r;
					}
				}
			}
			t = up(t);
		}
		
		return null;
	}

	String up(String path) {
		int p = path.lastIndexOf("/");
		if (p >= 0) {
			return path.substring(0, p);
		}
		return "";
	}

	public File findStaticFile(String host,String path) {
		path = sanitizePath(path);
		if( path == null ) return null;
		for(StaticDirMapping dm: staticDir ) {
			if( match(dm,host,path) ) {
				String t = path.substring(dm.path.length());
				for(String dir: dm.dirs) {
					String filename = dir + "/" + t ; // todo			
					File f = new File(filename);
					if( f.exists() ) {
						if( f.isDirectory()) return null;
						else return f;
					}
				}
				return null;
			}
		}
		return null;
	}

	List<String> staticDirConvert(String dir) {
		List<String> list = new ArrayList<>();

		if( !dir.startsWith("classpath:") ) {
			list.add(dir);
			return list;
		}

		dir = dir.substring(10);
		Enumeration<URL> urls = null;
		try {
			urls = getClass().getClassLoader().getResources(dir);
		} catch(Exception e) {
			throw new RuntimeException("unknown classpath dir=classpath:"+dir);
		}
		
		while( urls.hasMoreElements() ) {
			URL url = urls.nextElement();
			String d = urlToDir(url);
			if( d != null ) {
				list.add(d);
			}
		}
		
		return list;
	}
	
	String urlToDir(URL url) {

		String path = url.getPath(); 
		
		if (url.getProtocol().equals("file")) { 
			path = path.substring(path.indexOf("/"));
			File file = new File(path); 
			if( !file.isDirectory() ) {
				return null;
			}
			return file.getAbsolutePath();
		} 
		
		if (url.getProtocol().equals("jar")) {
			
			String jarPath = path.substring(path.indexOf("/"), path.indexOf("!")); 
			String dir = path.substring(path.indexOf("!")+2); // remove the first / 
			
			try { 
				File jarFile = new File(jarPath);
				String targetDir = jarCacheDir+"/"+jarFile.getName();
				WebUtils.extractJarDir(jarPath,targetDir,dir);
				
				String searchdir = targetDir + "/" + dir;
				return new File(searchdir).getAbsolutePath();
            } catch (Exception e) {  
                log.error("load jar exception, exception="+e.getMessage()+", url="+url);
				return null;
            } 
		}

		return null;
	}
		
	public String findTemplateDir(String host,String path) {
		path = sanitizePath(path);
		if( path == null ) return null;
		for(TemplateDirMapping dm: templateDir ) {
			if( match(dm,host,path) ) {
				return dm.dir;
			}
		}
		return null;
	}
	
	String sanitizePath(String path) {
		int p = path.indexOf("?");
		if( p >= 0 ) {
			path = path.substring(0,p);
		}
		p = path.indexOf("#");
		if( p >= 0 ) {
			path = path.substring(0,p);
		}		
		p = path.lastIndexOf("/");
		if( p < 0 ) return null;
		String dir = path.substring(0,p);
		if( dir.indexOf(".") >= 0 ) return null; // . is not allowed in path
		return path;
	}

	boolean match(TemplateDirMapping dir,String host,String path) {
		if(dir.hosts.equals("*") || dir.hostSet.contains(host) ) {
			if( path.startsWith(dir.path)) return true;
		}
		return false;
	}
	
	boolean match(StaticDirMapping dir,String host,String path) {
		if(dir.hosts.equals("*") || dir.hostSet.contains(host) ) {
			if( path.startsWith(dir.path)) return true;
		}
		return false;
	}
	
	boolean isEmpty(String s) {
		return s == null || s.isEmpty() ;
	}

	public String getDataDir() {
		return dataDir;
	}

	public void setDataDir(String dataDir) {
		this.dataDir = dataDir;
	}

}
