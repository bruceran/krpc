package krpc.rpc.impl;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;

import krpc.common.RetCodes;
import krpc.rpc.core.ReflectionUtils;
import krpc.rpc.core.RpcCallable;
import krpc.rpc.core.RpcException;
import krpc.rpc.core.ServiceMetas;
import krpc.rpc.core.Validator;

import com.google.protobuf.DynamicMessage;

public class DefaultServiceMetas implements ServiceMetas {

	HashMap<Integer,Object> services = new HashMap<Integer,Object>();
	HashMap<Integer,Object> referers = new HashMap<Integer,Object>();
	HashMap<String,Method> methods = new HashMap<String,Method>();
	HashMap<Integer,Object> asyncReferers = new HashMap<Integer,Object>();
	HashMap<String,Method> asyncMethods = new HashMap<String,Method>();
	HashMap<String,Class<?>> reqClsMap = new HashMap<String,Class<?>>();
	HashMap<String,Class<?>> resClsMap = new HashMap<String,Class<?>>();
	HashMap<String,Method> reqParserMap = new HashMap<String,Method>();
	HashMap<String,Method> resParserMap = new HashMap<String,Method>();
	HashMap<Integer,String> serviceNames = new HashMap<Integer,String>();
	HashMap<String,String> msgNames = new HashMap<String,String>();
	HashMap<String,RpcCallable> callableMap = new HashMap<String,RpcCallable>();
	HashMap<String,Descriptor> reqDescMap = new HashMap<String,Descriptor>();
	HashMap<String,Descriptor> resDescMap = new HashMap<String,Descriptor>();
	HashMap<Integer,RpcCallable> dynamicCallableMap = new HashMap<Integer,RpcCallable>();
	
	Validator validator;
	
	public Object findService(int serviceId) {
		return services.get(serviceId);
	}
	public Object findReferer(int serviceId) {
		return referers.get(serviceId);
	}
	public Method findMethod(int serviceId,int msgId) {
		return methods.get(serviceId+"."+msgId);
    }
	public Object findAsyncReferer(int serviceId) {
		return asyncReferers.get(serviceId);
	}
	public Method findAsyncMethod(int serviceId,int msgId) {
		return asyncMethods.get(serviceId+"."+msgId);
    }	
	public Class<?> findReqClass(int serviceId,int msgId) {
    	return reqClsMap.get(serviceId+"."+msgId);
    }
	public Class<?> findResClass(int serviceId,int msgId) {
		return resClsMap.get(serviceId+"."+msgId);
    }
	public Method findReqParser(int serviceId,int msgId) {
		return reqParserMap.get(serviceId+"."+msgId);
	}
	public Method findResParser(int serviceId,int msgId) {
		return resParserMap.get(serviceId+"."+msgId);
	}

	public String getServiceName(int serviceId) {
		return serviceNames.get(serviceId);  
	}
	public String getName(int serviceId,int msgId) {
		return msgNames.get(serviceId+"."+msgId);
	}
	public RpcCallable findCallable(String implClsName) {
		return callableMap.get(implClsName);
	}	
	
	public Map<Integer, String> getMsgNames(int serviceId) {
		Map<Integer, String> map = new HashMap<>();
		String prefix = serviceId + "." ;
		for( Map.Entry<String, Method> entry: methods.entrySet()) {
			String key = entry.getKey();
			String name = entry.getValue().getName();
			if( key.startsWith(prefix) ) {
				int p = key.indexOf(".");
				map.put(Integer.parseInt( key.substring(p+1) ), name);
			}
		}
		return map;
	}
	
	public String getServiceIdMsgId(String serviceName,String msgName) {

		serviceName = serviceName.toLowerCase();
		msgName = msgName.toLowerCase();
		
		String s = serviceName + "." + msgName;
		for( Map.Entry<String, String> entry: msgNames.entrySet()) {
			if( entry.getValue().equals(s) ) {
				return entry.getKey();
			}
		}
		
		return null;

	}

	private void addImpl(Class<?> intf, Object obj,boolean isService) {
		
		ReflectionUtils.checkInterface(intf,obj);
		
		int serviceId = ReflectionUtils.getServiceId(intf);
		if( serviceId <= 1) throw new RuntimeException("serviceId must > 1");
		if( isService )
			services.put(serviceId, obj);
		else
			referers.put(serviceId, obj);

		String serviceName = intf.getSimpleName();
		
		HashMap<Integer,String> msgIdMap = ReflectionUtils.getMsgIds(intf);
		HashMap<String,Object> msgNameMap = ReflectionUtils.getMethodInfo(intf);
		for( int msgId : msgIdMap.keySet() ) {
			if( msgId < 1) throw new RuntimeException("msgId must > 0");
			String msgName = msgIdMap.get(msgId);
			Method m = (Method)msgNameMap.get(msgName);
			Class<?> reqCls = (Class<?>)msgNameMap.get(msgName+"-req");
			Class<?> resCls = (Class<?>)msgNameMap.get(msgName+"-res");
			
			if( isService && validator != null ) validator.prepare(reqCls);
			
			Method reqParser = (Method)msgNameMap.get(msgName+"-reqp");
			Method resParser = (Method)msgNameMap.get(msgName+"-resp");			
			if( m != null ) {
				methods.put(serviceId+"."+msgId, m);
				reqClsMap.put(serviceId+"."+msgId, reqCls);
				resClsMap.put(serviceId+"."+msgId, resCls);
				reqParserMap.put(serviceId+"."+msgId, reqParser);
				resParserMap.put(serviceId+"."+msgId, resParser);	
				serviceNames.put(serviceId, serviceName.toLowerCase());
				msgNames.put(serviceId+"."+msgId, (serviceName+"."+msgName).toLowerCase());
			}
		}
	}
	
	private void addAsyncImpl(Class<?> intf, Object obj) {
		ReflectionUtils.checkInterface(intf,obj);
		
		int serviceId = ReflectionUtils.getServiceId(intf);
		if( serviceId <= 1) throw new RuntimeException("serviceId must > 1");
		asyncReferers.put(serviceId, obj);

		String serviceName = intf.getSimpleName();
		serviceName = serviceName.substring(0,serviceName.length()-5);
		
		HashMap<Integer,String> msgIdMap = ReflectionUtils.getMsgIds(intf);
		HashMap<String,Object> msgNameMap = ReflectionUtils.getAsyncMethodInfo(intf);
		for( int msgId : msgIdMap.keySet() ) {
			if( msgId < 1) throw new RuntimeException("msgId must > 0");
			String msgName = msgIdMap.get(msgId);
			Method m = (Method)msgNameMap.get(msgName);
			Class<?> reqCls = (Class<?>)msgNameMap.get(msgName+"-req");
			Class<?> resCls = (Class<?>)msgNameMap.get(msgName+"-res");
			Method reqParser = (Method)msgNameMap.get(msgName+"-reqp");
			Method resParser = (Method)msgNameMap.get(msgName+"-resp");			
			if( m != null ) {
				asyncMethods.put(serviceId+"."+msgId, m);
				reqClsMap.putIfAbsent(serviceId+"."+msgId, reqCls);
				resClsMap.putIfAbsent(serviceId+"."+msgId, resCls);
				reqParserMap.putIfAbsent(serviceId+"."+msgId, reqParser);
				resParserMap.putIfAbsent(serviceId+"."+msgId, resParser);
				serviceNames.putIfAbsent(serviceId, serviceName.toLowerCase());
				msgNames.putIfAbsent(serviceId+"."+msgId, (serviceName+"."+msgName).toLowerCase());
			}
		}
	}

	public void addService(Class<?> intf, Object impl,RpcCallable callable) {
		addImpl(intf,impl,true); 
		if( callable != null )
			callableMap.put(impl.getClass().getName(), callable);
	}
	
	public void addReferer(Class<?> intf, Object impl,RpcCallable callable) {
		addImpl(intf,impl,false); 
		callableMap.put(impl.getClass().getName(), callable);
	}
	
	public void addAsyncReferer(Class<?> intf, Object impl,RpcCallable callable) {
		addAsyncImpl(intf,impl);
		callableMap.put(impl.getClass().getName(), callable);
	}

	public void addDirect(int serviceId,int msgId, Class<?> reqCls, Class<?> resCls) {
		reqClsMap.putIfAbsent(serviceId+"."+msgId, reqCls);
		resClsMap.putIfAbsent(serviceId+"."+msgId, resCls);
		HashMap<String,Method> parsers = ReflectionUtils.getParsers(reqCls,resCls);
		reqParserMap.put(serviceId+"."+msgId, parsers.get("reqp"));
		resParserMap.put(serviceId+"."+msgId, parsers.get("resp"));
	}

	public void addDynamic(int serviceId,int msgId, Descriptor reqDesc, Descriptor resDesc, String serviceName,String msgName) {
		reqDescMap.put(serviceId+"."+msgId, reqDesc);
		resDescMap.put(serviceId+"."+msgId, resDesc);
		serviceNames.put(serviceId, serviceName.toLowerCase());
		msgNames.put(serviceId+"."+msgId, serviceName+"."+msgName);
	}
	
	public  void addDynamic(int serviceId,RpcCallable callable) {
		dynamicCallableMap.put(serviceId, callable);
	}
	public Descriptor findDynamicReqDescriptor(int serviceId,int msgId) {
		return reqDescMap.get(serviceId+"."+msgId);
	}
	
	public Descriptor findDynamicResDescriptor(int serviceId,int msgId) {
		return resDescMap.get(serviceId+"."+msgId);
	}
	
	public RpcCallable findDynamicCallable(int serviceId) {
		return dynamicCallableMap.get(serviceId);
	}
	
	public Message generateRes(int serviceId,int msgId, int retCode) {
		return generateRes(serviceId,msgId,retCode,null);
	}
	
	public Message generateRes(int serviceId,int msgId, int retCode,String retMsg) {

		Class<?> cls = findResClass(serviceId,msgId);
		if( cls == null ) {
			return generateResDynamic(serviceId,msgId,retCode);
		} 

		Message res = null;
		try {
			if( retMsg == null )
	    		retMsg = RetCodes.retCodeText(retCode);
			res = (Message)ReflectionUtils.generateResponseObject(cls,retCode,retMsg);
		} catch(Exception e) {
			throw new RpcException(RetCodes.ENCODE_RES_ERROR,"generateRes generate object exception");
		}

		return res;
	}

	Message generateResDynamic(int serviceId,int msgId, int retCode) {
	
		Descriptor desc = findDynamicResDescriptor(serviceId,msgId);
		if( desc == null ) {
			throw new RpcException(RetCodes.NOT_FOUND,"generateRes cls not found");
		}  
		
		Message res = null;
		try {
	    	String retMsg = RetCodes.retCodeText(retCode);
	    	DynamicMessage.Builder b = ReflectionUtils.generateDynamicBuilder(desc);
			res = (Message)ReflectionUtils.generateResponseObject(b,serviceId+":"+msgId,retCode,retMsg);
		} catch(Exception e) {
			throw new RpcException(RetCodes.ENCODE_RES_ERROR,"generateRes generate object exception");
		}			
	
		return res;
	}
	
	public Validator getValidator() {
		return validator;
	}
	public void setValidator(Validator validator) {
		this.validator = validator;
	}	
	
}
