package krpc.rpc.web.impl;

import krpc.common.InitClose;
import krpc.common.InitCloseUtils;
import krpc.common.StartStop;
import krpc.rpc.web.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;

public class DefaultWebRouteService implements WebRouteService, InitClose, StartStop {

    static Logger log = LoggerFactory.getLogger(DefaultWebRouteService.class);

    static class DirInfo implements Comparable<DirInfo> {
        String hosts;
        Set<String> hostSet;
        String path;

        String templateDir;

        DirInfo(String hosts, String path, String templateDir) {
            this.hosts = hosts;
            if (!hosts.equals("*")) {
                hostSet = new HashSet<String>();
                String[] ss = hosts.split(",");
                for (String s : ss) hostSet.add(s);
            }

            this.path = path;
            this.templateDir = templateDir;
        }

        public boolean equals(final java.lang.Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof DirInfo)) {
                return false;
            }
            DirInfo other = (DirInfo) obj;

            boolean result = true;
            result = result && Objects.equals(hosts, other.hosts);
            result = result && Objects.equals(path, other.path);

            return result;
        }

        public int hashCode() {
            return Objects.hash(hosts, path);
        }

        public int compareTo(DirInfo other) {
            return other.path.compareTo(this.path); // reverse order
        }
    }

    static class StaticDirMapping implements Comparable<StaticDirMapping> {
        String hosts;
        Set<String> hostSet;
        String path;
        List<String> dirs;

        StaticDirMapping(String hosts, String path, List<String> dirs) {
            this.hosts = hosts;
            if (!hosts.equals("*")) {
                hostSet = new HashSet<String>();
                String[] ss = hosts.split(",");
                for (String s : ss) hostSet.add(s);
            }

            this.path = path;
            this.dirs = dirs;
        }

        public boolean equals(final java.lang.Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof StaticDirMapping)) {
                return false;
            }
            StaticDirMapping other = (StaticDirMapping) obj;

            boolean result = true;
            result = result && Objects.equals(hosts, other.hosts);
            result = result && Objects.equals(path, other.path);

            return result;
        }

        public int hashCode() {
            return Objects.hash(hosts, path);
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
        Map<String, String> attrs;

        String origins; // * for all

        ServiceMapping(String hosts, String path, String methods, String origins, int serviceId, int msgId,
                       int sessionMode, WebPlugins plugins, Map<String, String> attrs,boolean caseSensitive) {

            this.hosts = hosts;
            this.originalPath = path;

            checkPlaceHolders(path);

            int p = path.indexOf("/{");
            if (p >= 0) {
                this.path = path.substring(0, p);
                placeHolders = path.substring(p + 1).split("/");
                placeHolderFlags = new boolean[placeHolders.length];
                for (int i = 0; i < placeHolders.length; ++i) {
                    String t = placeHolders[i];
                    if (t.startsWith("{")) {
                        placeHolderFlags[i] = true;
                        placeHolders[i] = t.substring(1, t.length() - 1);
                    } else {
                        if(!caseSensitive) placeHolders[i] = placeHolders[i].toLowerCase();
                    }
                }
                if(!caseSensitive) this.path = this.path.toLowerCase();
            } else {
                this.path = path;
                if(!caseSensitive) this.path = this.path.toLowerCase();
            }

            this.methods = methods;
            if (!methods.equals("*")) {
                methodSet = new HashSet<String>();
                String[] ss = methods.split(",");
                for (String s : ss) methodSet.add(s.toLowerCase());
            }

            this.origins = origins;

            this.serviceId = serviceId;
            this.msgId = msgId;
            this.sessionMode = sessionMode;
            this.plugins = plugins;
            this.attrs = attrs;
        }

        private void checkPlaceHolders(String path) {
            String[] tss = path.split("/");
            for (String s : tss) {
                int p1 = s.indexOf("{");
                if (p1 == -1) continue;
                if (p1 != 0)
                    throw new RuntimeException("place holder not valid, path=" + path);
                int p2 = s.indexOf("}");
                if (p2 != s.length() - 1) {
                    throw new RuntimeException("place holder not valid, path=" + path);
                }
            }
        }

        boolean match(String path, String method, HashMap<String, String> variables) {
            if (!methodSet.contains(method)) return false;
            if (placeHolders == null) return true;
            if (path.equals(this.path)) return false;

            String t = path.substring(this.path.length() + 1);
            String[] tt = t.split("/");
            if (tt.length < placeHolderFlags.length) return false;

            for (int i = 0; i < placeHolderFlags.length; ++i) {
                if (!placeHolderFlags[i]) {
                    if (!tt[i].equals(placeHolders[i])) return false;
                }
            }
            // match
            for (int i = 0; i < placeHolderFlags.length; ++i) {
                if (placeHolderFlags[i]) {
                    variables.put(placeHolders[i], tt[i]);
                }
            }
            return true;
        }

        public boolean equals(final java.lang.Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ServiceMapping)) {
                return false;
            }
            ServiceMapping other = (ServiceMapping) obj;

            boolean result = true;
            result = result && Objects.equals(hosts, other.hosts);
            result = result && Objects.equals(originalPath, other.originalPath);
            result = result && Objects.equals(methods, other.methods);

            return result;
        }

        public int hashCode() {
            return Objects.hash(hosts, originalPath, methods);
        }

        public int compareTo(ServiceMapping other) {
            return this.originalPath.compareTo(other.originalPath);
        }
    }

    static class HostMapping {
        Map<String, List<ServiceMapping>> pathMappings = new HashMap<>();
    }

    private String dataDir = ".";
    private String jarCacheDir;
    private String defaultCorsAllowOrigin = "*";

    private boolean caseSensitive = false;

    private List<WebUrl> urlList = new ArrayList<WebUrl>();
    private List<WebDir> dirList = new ArrayList<WebDir>();

    private List<StaticDirMapping> staticDirs = new ArrayList<>();
    private List<DirInfo> dirInfos = new ArrayList<>();

    private Map<String, HostMapping> hostMappings = new HashMap<String, HostMapping>();

    private Map<String, WebPlugin> plugins = new HashMap<>();

    public void addUrl(WebUrl url) {
        urlList.add(url);
    }

    public void addDir(WebDir dir) {
        dirList.add(dir);
    }

    public void init() {


        jarCacheDir = dataDir + "/jarcache";


        for (WebDir wd : dirList) {

            if (!isEmpty(wd.getStaticDir())) {
                String dir = wd.getStaticDir();
                List<String> dirs = staticDirConvert(dir);
                staticDirs.add(new StaticDirMapping(wd.getHosts(), wd.getPath(), dirs));
            }

            if (!isEmpty(wd.getTemplateDir())) {
                String dir = wd.getTemplateDir();
                dirInfos.add(new DirInfo(wd.getHosts(), wd.getPath(), dir));
            }

        }
        Collections.sort(staticDirs);
        Collections.sort(dirInfos);

        for (WebUrl url : urlList) {

            if (isEmpty(url.getHosts())) {
                throw new RuntimeException("host must be specified");
            }
            if (isEmpty(url.getPath())) {
                throw new RuntimeException("path must be specified");
            }

            ServiceMapping sm = new ServiceMapping(url.getHosts(), url.getPath(),
                    url.getMethods(), url.getOrigins(),
                    url.getServiceId(), url.getMsgId(),
                    url.getSessionMode(), url.getPlugins(), url.getAttrs(),caseSensitive);

            String host = url.getHosts();
            String[] ss = host.split(",");
            for (String s : ss) {
                addHostMapping(s, sm);
            }

            if (url.getPlugins() != null) {
                for (WebPlugin p : url.getPlugins().getPlugins()) {
                    plugins.put(p.getClass().getName(), p);
                }
            }

        }

        for (HostMapping hm : hostMappings.values()) {
            for (List<ServiceMapping> lsm : hm.pathMappings.values()) {
                Collections.sort(lsm);
            }
        }

        for (WebPlugin p : plugins.values()) {
            InitCloseUtils.init(p);
        }

    }

    void addHostMapping(String host, ServiceMapping sm) {
        HostMapping hm = hostMappings.get(host);
        if (hm == null) {
            hm = new HostMapping();
            hostMappings.put(host, hm);
        }

        String t = sm.path;
        List<ServiceMapping> lsm = hm.pathMappings.get(t);
        if (lsm == null) {
            lsm = new ArrayList<ServiceMapping>();
            hm.pathMappings.put(t, lsm);
        }

        lsm.add(sm);
    }

    public void start() {

        for (WebPlugin p : plugins.values()) {
            InitCloseUtils.start(p);
        }
    }

    public void stop() {

        for (WebPlugin p : plugins.values()) {
            InitCloseUtils.stop(p);
        }
    }

    public void close() {

        for (WebPlugin p : plugins.values()) {
            InitCloseUtils.close(p);
        }
    }

    public WebRoute findRoute(String host, String path, String method) {
        WebRoute r = findByHost(host, path, method.toLowerCase());
        if (r != null) {
            String templateDir = findTemplateDir(host, path);
            if (!isEmpty(templateDir)) {
                r.setTemplateDir(templateDir);
            }
        }
        return r;
    }

    private WebRoute findByHost(String host, String path, String method) {

        if (method.equals("head")) method = "get";

        HostMapping hm = hostMappings.get(host);
        if (hm == null)
            hm = hostMappings.get("*");
        if (hm == null)
            return null;

        HashMap<String, String> variables = new HashMap<String, String>();
        String t = path;
        if(!caseSensitive) t = t.toLowerCase();
        while (!t.isEmpty()) {
            List<ServiceMapping> lm = hm.pathMappings.get(t);
            if (lm != null) {
                for (int i = lm.size() - 1; i >= 0; --i) {
                    ServiceMapping sm = lm.get(i);
                    if (sm.match(path, method, variables)) {
                        WebRoute r = new WebRoute(sm.serviceId, sm.msgId);
                        r.setSessionMode(sm.sessionMode);
                        r.setPlugins(sm.plugins);
                        r.setVariables(variables);
                        r.setAttrs(sm.attrs);
                        r.setMethods(sm.methods);
                        r.setOrigins(sm.origins);
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

    public File findStaticFile(String host, String path) {
        path = sanitizePath(path);
        if (path == null) return null;
        for (StaticDirMapping dm : staticDirs) {
            if (match(dm, host, path)) {
                String t = path.substring(dm.path.length());
                for (String dir : dm.dirs) {
                    String filename = dir + "/" + t;
                    File f = new File(filename);
                    if (f.exists()) {
                        if (f.isDirectory()) return null;
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

        if (!dir.startsWith("classpath:")) {
            list.add(dir);
            return list;
        }

        dir = dir.substring(10);
        Enumeration<URL> urls = null;
        try {
            urls = getClass().getClassLoader().getResources(dir);
        } catch (Exception e) {
            throw new RuntimeException("unknown classpath dir=classpath:" + dir);
        }

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String d = urlToDir(url);
            if (d != null) {
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
            if (!file.isDirectory()) {
                return null;
            }
            return file.getAbsolutePath();
        }

        if (url.getProtocol().equals("jar")) {

            String jarPath = path.substring(path.indexOf("/"), path.indexOf("!"));
            String dir = path.substring(path.indexOf("!") + 2); // remove the first /

            try {
                File jarFile = new File(jarPath);
                String targetDir = jarCacheDir + "/" + jarFile.getName();
                WebUtils.extractJarDir(jarPath, targetDir, dir);

                String searchdir = targetDir + "/" + dir;
                return new File(searchdir).getAbsolutePath();
            } catch (Exception e) {
                log.error("load jar exception, exception=" + e.getMessage() + ", url=" + url);
                return null;
            }
        }

        return null;
    }

    public String findTemplateDir(String host, String path) {
        path = sanitizePath(path);
        if (path == null) return null;
        for (DirInfo dm : dirInfos) {
            if (isEmpty(dm.templateDir)) continue;
            if (match(dm, host, path)) {
                return dm.templateDir;
            }
        }
        return null;
    }

    String sanitizePath(String path) {
        int p = path.indexOf("?");
        if (p >= 0) {
            path = path.substring(0, p);
        }
        p = path.indexOf("#");
        if (p >= 0) {
            path = path.substring(0, p);
        }
        p = path.lastIndexOf("/");
        if (p < 0) return null;
        String dir = path.substring(0, p);
        if (dir.indexOf(".") >= 0) return null; // . is not allowed in path
        return path;
    }

    boolean match(DirInfo dir, String host, String path) {
        if (dir.hosts.equals("*") || dir.hostSet.contains(host)) {
            if (path.startsWith(dir.path)) return true;
        }
        return false;
    }

    boolean match(StaticDirMapping dir, String host, String path) {
        if (dir.hosts.equals("*") || dir.hostSet.contains(host)) {
            if (path.startsWith(dir.path)) return true;
        }
        return false;
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getDefaultCorsAllowOrigin() {
        return defaultCorsAllowOrigin;
    }

    public void setDefaultCorsAllowOrigin(String defaultCorsAllowOrigin) {
        this.defaultCorsAllowOrigin = defaultCorsAllowOrigin;
    }


    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

}
