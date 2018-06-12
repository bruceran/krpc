package krpc.rpc.web.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.rpc.web.WebRoute;
import krpc.rpc.web.WebRouteService;
import krpc.rpc.web.WebDir;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebUrl;

public class DefaultWebRouteService implements WebRouteService, InitClose {
	
	static class DirMapping {
		String hosts;
		Set<String> hostSet;
		String path;
		String dir;
		
		DirMapping(String hosts,String path,String dir) {
			this.hosts = hosts;
			if( !hosts.equals("*") ) {
				hostSet = new HashSet<String>();
				String[] ss = hosts.split(",");
				for(String s:ss ) hostSet.add(s);
			}

			this.path = path;
			this.dir = dir;
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
		WebPlugin[] plugins;
		Map<String,String>  attrs;
		
		ServiceMapping(String hosts,String path,String methods,int serviceId,int msgId,
				int sessionMode,WebPlugin[] plugins,Map<String,String>  attrs) {
			
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
	
	private List<WebUrl> urlList = new ArrayList<WebUrl>();
	private List<WebDir> dirList = new ArrayList<WebDir>();
	
	private List<DirMapping> staticDir = new ArrayList<DirMapping>();
	private List<DirMapping> uploadDir = new ArrayList<DirMapping>();
	private List<DirMapping> templateDir = new ArrayList<DirMapping>();

	private Map<String,HostMapping> hostMappings = new HashMap<String,HostMapping>();

	private Map<String,WebPlugin> plugins = new HashMap<>();
	
	public void addUrl(WebUrl url) {
		urlList.add(url);
	}

	public void addDir(WebDir dir) {
		dirList.add(dir);
	}
	
	public void init() {

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
				for(WebPlugin p:url.getPlugins()) {
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
		
		for(WebDir wd: dirList) {

			if( !isEmpty(wd.getBaseDir()) || !isEmpty(wd.getStaticDir())) {
				String dir = null;
				if(  !isEmpty(wd.getStaticDir() ) ) {
					dir = wd.getStaticDir();
				} else {
					dir = wd.getBaseDir() + "/static";
				}
				staticDir.add( new DirMapping(wd.getHosts(),wd.getPath(),dir) );
			}
			
			if( !isEmpty(wd.getBaseDir()) || !isEmpty(wd.getUploadDir())) {
				String dir = null;
				if(  !isEmpty(wd.getUploadDir() ) ) {
					dir = wd.getUploadDir();
				} else {
					dir = wd.getBaseDir() + "/upload";
				}
				uploadDir.add( new DirMapping(wd.getHosts(),wd.getPath(),dir) );
			}
			
			if( !isEmpty(wd.getBaseDir()) || !isEmpty(wd.getTemplateDir())) {
				String dir = null;
				if(  !isEmpty(wd.getTemplateDir() ) ) {
					dir = wd.getTemplateDir();
				} else {
					dir = wd.getBaseDir() + "/template";
				}
				templateDir.add( new DirMapping(wd.getHosts(),wd.getPath(),dir) );
			}

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
	
	public void close() {

		for( WebPlugin p:plugins.values() ) {
			InitCloseUtils.close(p);
		}		
	}

	public WebRoute findRoute(String host, String path, String method) {
		return findByHost(host, path, method.toLowerCase());
	}

	private WebRoute findByHost(String host, String path, String method) {
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

	public String findStaticFile(String host,String path) {
		path = sanitizePath(path);
		for(DirMapping dm: staticDir ) {
			if( match(dm,host,path) ) {
				return dm.dir + "/" + path.substring(dm.path.length());
			}
		}
		return null;
	}
	
	public String findTemplate(String host,String path,String templateName) {
		path = sanitizePath(path);
		for(DirMapping dm: templateDir ) {
			if( match(dm,host,path) ) {
				return dm.dir + "/" + templateName;
			}
		}
		return null;
	}
	
	public String findUploadDir(String host,String path) {
		path = sanitizePath(path);
		for(DirMapping dm: uploadDir ) {
			if( match(dm,host,path) ) {
				return dm.dir;
			}
		}
		return null;
	}
	
	String sanitizePath(String path) {
		// todo
		return path;
	}

	boolean match(DirMapping dir,String hosts,String path) {
		if(dir.hosts.equals("*") || dir.hostSet.contains(hosts) ) {
			if( path.startsWith(dir.path)) return true;
		}
		return false;
	}
	
	boolean isEmpty(String s) {
		return s == null || s.isEmpty() ;
	}

}
