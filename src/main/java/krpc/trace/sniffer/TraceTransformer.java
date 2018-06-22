package krpc.trace.sniffer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

public class TraceTransformer implements ClassFileTransformer {

	class MethodPattern {
		String clsSimpleName;
		Pattern methodPattern;
		String traceType;
		MethodPattern(String clsSimpleName,Pattern methodPattern,String traceType) {
			this.clsSimpleName = clsSimpleName;
			this.methodPattern = methodPattern;
			this.traceType = traceType;
		}
	}
	enum LogLevel { INFO, ERROR };
	
	private Map<String,List<MethodPattern>> classMap = new HashMap<>();
	private ClassPool pool;
	private String cfgFile = "./tracesniffer.cfg";
	private String logFile = "./tracesniffer.log";
	private LogLevel logLevel = LogLevel.ERROR;

	void error(String s) {
		log(s,LogLevel.ERROR);
	}
	void info(String s) {
		log(s,LogLevel.INFO);
	}
	
	void log(String s,LogLevel logLevel) {
		
		if( this.logLevel == LogLevel.ERROR  && logLevel == LogLevel.INFO ) return;
		 
		s = LocalDateTime.now()  + " " + s;
		try {
			FileOutputStream out = new FileOutputStream(logFile,true);
			out.write((s+"\n").getBytes());
			out.close();
		} catch(Exception e) {
			System.out.println(s);
		}
	}
	
	public TraceTransformer(String args) {
		pool = ClassPool.getDefault();
		
		if( args != null && !args.isEmpty() )  cfgFile = args;
		loadConfig();
		
		info("-----");
	}

	private void loadConfig() {
		try {
			Properties cfg = new Properties();
			InputStream in = new FileInputStream(cfgFile);
			cfg.load( in );
			in.close();
			for(Object o: cfg.keySet() ) {
				String key = (String)o;
				String value = cfg.getProperty(key);
				if( key.equals("log.file")) {
					logFile = value;
					continue;
				}
				if( key.equals("log.level")) {
					switch(value.toLowerCase()) {
						case "error":
						case "warn":
						case "fatal":
							logLevel = LogLevel.ERROR;
							break;
						case "info":
						case "debug":
						case "trace":
							logLevel = LogLevel.INFO;
							break;
						default:
							break;
					}
					continue;
				}
				int p = key.indexOf("#");
				if( p < 0 ) continue;
				String clsName = key.substring(0, p);
				int p2 = clsName.lastIndexOf(".");
				if( p2 < 0 ) continue;
				
				String clsSimpleName = clsName.substring(p2+1);
				String pattern = key.substring(p+1);
				String traceType = value;

				Pattern methodPattern = Pattern.compile(pattern);
				MethodPattern mc = new MethodPattern(clsSimpleName,methodPattern,traceType);
				List<MethodPattern> list = classMap.get(clsName);
				if( list == null ) {
					list = new ArrayList<MethodPattern>();
					classMap.put(clsName,list);
				}
				list.add(mc);
			}
		} catch(Exception e) {
			error("load sniffer config file exception, file="+cfgFile+",e="+e.getMessage());
		}
	}
	
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
    		ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
    	className = className.replace('/','.');
    	List<MethodPattern> list = classMap.get(className);
    	if( list == null ) return classfileBuffer;

    	 try {
	    	CtClass ctClass= pool.get(className);
	    	
	    	CtMethod[] methods = ctClass.getMethods();
	    	for(CtMethod m:methods) {

		    	for(MethodPattern mp: list) {
		    		
		                String methodName = m.getName();
		                if( !mp.methodPattern.matcher(methodName).matches()) continue;
		                
		                String traceType = mp.traceType;
		                String traceAction = mp.clsSimpleName+"."+methodName;
		                
		                String startExpr = String.format("krpc.trace.Trace.start(\"%s\",\"%s\");",traceType,traceAction);
		                String stopExpr = "krpc.trace.Trace.stop(ok);";
		                String logExceptionExpr = "krpc.trace.Trace.logException(e);";
	
		                String oldMethodName = methodName + "$krpctrace2018";
	                    m.setName(oldMethodName);
	                    m = CtNewMethod.copy(m, methodName, ctClass, null);
	                    
	                    StringBuilder buff = new StringBuilder();
	                    buff.append("{");
	                    buff.append("boolean ok = true;");
	                    buff.append(startExpr);
	                    buff.append("try {");
	                    if( m.getReturnType() != null )
	                    	buff.append("return ");
	                    buff.append(oldMethodName + "($$);");
	                    buff.append("} catch(Throwable e) {");
	                    buff.append("ok = false;");
	                    buff.append(logExceptionExpr);
	                    buff.append("throw e;");
	                    buff.append("} finally {");
	                    buff.append(stopExpr);
	                    buff.append("}");
	                    buff.append("}");
	
	                    m.setBody(buff.toString());
	                    ctClass.addMethod(m);
	                    
		                info("transform "+className+"."+methodName+" success");
		                
		                break;
		    	}
    	 	}
	    	return ctClass.toBytecode();
         } catch (Throwable e) {
             error("transform "+className+" exception, e="+e.getMessage());
         }
         return classfileBuffer;
    }
    
}
