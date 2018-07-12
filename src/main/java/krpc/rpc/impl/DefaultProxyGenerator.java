package krpc.rpc.impl;

import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import krpc.rpc.core.ProxyGenerator;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.RpcCallable;

public class DefaultProxyGenerator implements ProxyGenerator {
	
	ClassPool pool;
	ClassLoader loader;
	
	public DefaultProxyGenerator()  {
		loader = Thread.currentThread().getContextClassLoader();
		pool = new ClassPool(true);
		pool.appendClassPath(new LoaderClassPath(loader));
	}
	
	public Object generateReferer(Class<?> intf,RpcCallable callable) {
		try {
			String infName = intf.getName();
			String packageName = getPackageName(infName);
			String implPackageName = packageName + ".proxy";
			String implSimpleName = intf.getSimpleName() +"Proxy";
			String implClsName = implPackageName + "." + implSimpleName; 
			CtClass cls = pool.makeClass(implClsName);
			cls.addInterface(pool.get(infName));
			cls.addField(CtField.make("private krpc.rpc.core.RpcCallable client;", cls));
			cls.addConstructor(CtNewConstructor.make("public "+implSimpleName+"(krpc.rpc.core.RpcCallable client) {this.client = client;}", cls));
			
			// public LoginRes login(LoginReq req) { return (LoginRes)client.call(100,1,req); }
			
			int serviceId = ReflectionUtils.getServiceId(intf);
			if( serviceId <= 1) throw new RuntimeException("serviceId must > 1");		
			HashMap<Integer,String> msgIdMap = ReflectionUtils.getMsgIds(intf);
			HashMap<String,Object> msgNameMap = ReflectionUtils.getMethodInfo(intf);
			for(  Map.Entry<Integer, String> entry: msgIdMap.entrySet() ) {
				int msgId = entry.getKey();
				String msgName = entry.getValue();
				if( msgId < 1) throw new RuntimeException("msgId must > 0");
				Class<?> reqCls = (Class<?>)msgNameMap.get(msgName+"-req");
				Class<?> resCls = (Class<?>)msgNameMap.get(msgName+"-res");		
				
				StringBuilder code = new StringBuilder();
				code.append("public ").append(resCls.getName()).append(" ").append(msgName);
				code.append("(").append(reqCls.getName()).append(" req)");
				code.append("{ return (").append(resCls.getName()).append(")client.call(").append(serviceId).append(",").append(msgId).append(",req);").append("}");
				cls.addMethod(CtNewMethod.make(code.toString(), cls));
			}
			ProtectionDomain pd = getClass().getProtectionDomain();
			Class<?> proxyClass = cls.toClass(loader, pd); 
			return ReflectionUtils.newRefererObject(proxyClass, callable);
		} catch(Exception e) {
			throw new RuntimeException("generateReferer exception",e);
		}
	}

	public Object generateAsyncReferer(Class<?> intf,RpcCallable callable) {
		try {
			String infName = intf.getName();
			String packageName = getPackageName(infName);
			String implPackageName = packageName + ".proxy";
			String implSimpleName = intf.getSimpleName() +"Proxy";
			String implClsName = implPackageName + "." + implSimpleName; 
			CtClass cls = pool.makeClass(implClsName);
			cls.addInterface(pool.get(infName));
			cls.addField(CtField.make("private krpc.rpc.core.RpcCallable client;", cls));
			cls.addConstructor(CtNewConstructor.make("public "+implSimpleName+"(krpc.rpc.core.RpcCallable client) {this.client = client;}", cls));

			int serviceId = ReflectionUtils.getServiceId(intf);
			if( serviceId <= 1) throw new RuntimeException("serviceId must > 1");		
			HashMap<Integer,String> msgIdMap = ReflectionUtils.getMsgIds(intf);
			HashMap<String,Object> msgNameMap = ReflectionUtils.getAsyncMethodInfo(intf);
			for(  Map.Entry<Integer, String> entry: msgIdMap.entrySet() ) {
				int msgId = entry.getKey();
				String msgName = entry.getValue();
				if( msgId < 1) throw new RuntimeException("msgId must > 0");
				Class<?> reqCls = (Class<?>)msgNameMap.get(msgName+"-req");
				//Class<?> resCls = (Class<?>)msgNameMap.get(msgName+"-res");		
				
				StringBuilder code = new StringBuilder();
				
				// template is not supported by javassist
				/*
				code.append("public java.util.concurrent.CompletableFuture<").append(resCls.getName()).append("> ").append(msgName);
				code.append("(").append(reqCls.getName()).append(" req)");
				code.append("{ return (java.util.concurrent.CompletableFuture<").append(resCls.getName()).append(">)client.callAsync(").append(serviceId).append(",").append(msgId).append(",req);").append("}");
				*/

				code.append("public java.util.concurrent.CompletableFuture ").append(msgName);
				code.append("(").append(reqCls.getName()).append(" req)");
				code.append("{ return (java.util.concurrent.CompletableFuture)client.callAsync(").append(serviceId).append(",").append(msgId).append(",req);").append("}");
				
				cls.addMethod(CtNewMethod.make(code.toString(), cls));
			}
			ProtectionDomain pd = getClass().getProtectionDomain();
			Class<?> proxyClass = cls.toClass(loader, pd); 
			return ReflectionUtils.newRefererObject(proxyClass, callable);
		} catch(Exception e) {
			throw new RuntimeException("generateReferer exception",e);
		}
	}
	
	String getPackageName(String clsName) {
		int p = clsName.lastIndexOf(".");
		return clsName.substring(0,p);
	}
		
}
