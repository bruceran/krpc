package krpc.rpc.web.impl;

import java.io.File;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.rpc.core.Plugin;
import krpc.rpc.web.RenderPlugin;
import krpc.rpc.web.WebConstants;
import krpc.rpc.web.WebContextData;
import krpc.rpc.web.WebPlugin;
import krpc.rpc.web.WebReq;
import krpc.rpc.web.WebRes;

public class VelocityWebPlugin implements WebPlugin, RenderPlugin {

	static Logger log = LoggerFactory.getLogger(VelocityWebPlugin.class);
	
	static String htmlStart = "<!DOCTYPE html><html><body>";
	static String htmlEnd = "</body></html>";
	
	String templateField = "template";
	boolean cache = false;
	int checkInterval = 10;
	String version = "?0";
	String toolClass = "";
	Object tool;
	
	ConcurrentHashMap<String,VelocityEngine> engines = new ConcurrentHashMap<>();
	ReentrantLock lock = new ReentrantLock();
	
	public void config(String paramsStr) {
		Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("templateField");
		if (!isEmpty(s))
			templateField = s;
		s = params.get("cache");
		if (!isEmpty(s))
			cache = s.equals("true");
		s = params.get("checkInterval");
		if (!isEmpty(s))
			checkInterval = Integer.parseInt(s);
		s = params.get("version");
		if (!isEmpty(s))
			version = s;
		s = params.get("toolClass");
		if (!isEmpty(s))
			toolClass = s;		
		
		if (!isEmpty(toolClass)) {
			try {
				tool = Class.forName(toolClass).newInstance();
			} catch(Throwable e) {
				throw new RuntimeException("toolClass cannot be instantiated, toolClass="+toolClass);
			}
		}
	}

	VelocityEngine getVelocityEngine(String dir) {
		VelocityEngine ve = engines.get(dir);
		if( ve != null ) return ve;
		
		lock.lock();
		try {
			ve = engines.get(dir);
			if( ve != null ) return ve;
			
			ve = new VelocityEngine();
	        ve.setProperty("runtime.log", "velocity.log");
	        ve.setProperty("input.encoding", "UTF-8");
	        ve.setProperty("output.encoding", "UTF-8");
	        ve.setProperty("resource.loader", "file");
	        
	        if( dir.startsWith("classpath:")) {
	        	ve.setProperty("file.resource.loader.class","org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
	        } else {
	        	ve.setProperty("file.resource.loader.path", dir);
	        }

	        ve.setProperty("file.resource.loader.cache", cache?"true":"false");
	        ve.setProperty("file.resource.loader.modificationCheckInterval", String.valueOf(checkInterval));
	        ve.init();		
	        
	        engines.put(dir,ve);
		} finally {
			lock.unlock();
		}

        return ve;
	}

	public void render(WebContextData ctx, WebReq req, WebRes res) {
		String templateName = res.getStringResult(templateField);
		if (isEmpty(templateName)) {
			templateName = ctx.getRoute().getAttribute("template");
			if (isEmpty(templateName)) {
				renderErrorHtml(res,"template field not found");
				return;
			}
		}

		String templateDir  = ctx.getRoute().getAttribute("templateDir");
		if (isEmpty(templateDir)) {
			renderErrorHtml(res,"template dir not found");
			return;
		}		
		
		VelocityEngine ve = getVelocityEngine(templateDir);

		templateName = getTemplateName(templateDir,templateName);
		if (isEmpty(templateName)) {
			renderErrorHtml(res,"template not found");
			return;
		}
		
		try {
			Template t = ve.getTemplate(templateName);
			VelocityContext context = generateVelocityContext(ctx,req,res);
			StringWriter writer = new StringWriter();
	        t.merge(context, writer);
	        String html = writer.toString();
	        
			res.setContent(html);
	        String contentType = getContentType(templateName) + "; charset=utf-8";
			res.setContentType(contentType);        
		} catch(Exception e) {
			log.error("template render exception, e="+e.getMessage());
			renderErrorHtml(res,"template render exception");
		}
	}

	VelocityContext generateVelocityContext(WebContextData ctx, WebReq req, WebRes res) {
		VelocityContext context = new VelocityContext();
		context.put("req", req.getParameters());
		context.put("res", res.getResults());
		if( ctx.getSession() != null ) context.put("session", ctx.getSession());
		context.put("version", version);
		context.put("tool", tool);
		return context;
	}
	
	String getContentType(String templateName) {
		int p = templateName.lastIndexOf(".");
		String s = templateName.substring(0,p);
		return WebConstants.getContentType(s);
	}
	
	String getTemplateName(String templateDir, String templateName) {
		
		if( templateDir.startsWith("classpath:")) {
			return getTemplateNameClassPath(templateDir.substring(10),templateName);
		}
		
		String file = templateName + ".vm";
		File f = new File(templateDir + "/" + file);
		if (f.exists()) {
			return file;
		}		
		
		file = templateName + ".html.vm";
		f = new File(templateDir + "/" + file);
		if (f.exists()) {
			return file;
		}		
		
		return null;
	}
	
	String getTemplateNameClassPath(String templateDir, String templateName) {
		
		if( !templateDir.isEmpty() ) {
			if( templateDir.startsWith("/") ) templateDir = templateDir.substring(1);
			if( !templateDir.isEmpty() && !templateDir.endsWith("/") ) templateDir = templateDir + "/";
		}
		
		String file = templateDir + templateName + ".vm";
		if (  checkResourceExist(file) ) {
			return file;
		}		
		
		file = templateDir + templateName + ".html.vm";
		if (  checkResourceExist(file) ) {
			return file;
		}		
		
		return null;
	}
	
	boolean checkResourceExist(String file) {
		return getClass().getClassLoader().getResource(file) != null;
	}
	
	void renderErrorHtml(WebRes res, String content) {
		String html = htmlStart + content + htmlEnd;
		res.setContent(html);
		res.setContentType("text/html; charset=utf-8");		
	}

	boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

}
