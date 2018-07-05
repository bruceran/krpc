package krpc.rpc.core;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;

import krpc.rpc.core.proto.RpcMeta;

public class ReflectionUtils {

	static Logger log = LoggerFactory.getLogger(ReflectionUtils.class);
	
	static Class<?>[] dummyTypes = new Class<?>[0];
	static Object[] dummyParameters = new Object[0];
	static Class<?>[] callableTypes = new Class<?>[] {RpcCallable.class};
	
	public static String retCodeFieldInMap = "retCode"; // can be configured
	public static String retMsgFieldInMap = "retMsg"; // can be configured
	public static String retCodeField = "retCode_";
	public static String retMsgField = "retMsg_";
	static Map<String,Field> retCodeFields = new HashMap<String,Field>();
	static Map<String,Field> retMsgFields = new HashMap<String,Field>();
	static Field metaSequenceField = null;
	static Field metaPeersField = null;
	static Field metaCompressField = null;
	static ConcurrentHashMap<String,Object> errors = new ConcurrentHashMap<String,Object>();
	
	static {
		init();
	}
	
	public static void init() {
		try {
			metaPeersField = RpcMeta.Trace.class.getDeclaredField("peers_");
			metaPeersField.setAccessible(true);
			metaCompressField = RpcMeta.class.getDeclaredField("compress_");
			metaCompressField.setAccessible(true);
			metaSequenceField = RpcMeta.class.getDeclaredField("sequence_");
			metaSequenceField.setAccessible(true);
			retCodeField = retCodeFieldInMap + "_";
			retMsgField = retMsgFieldInMap + "_";
		} catch(Exception e) {
			throw new RuntimeException("ReflectionUtils init failed");
		}		
	}
	
    public static int getRetCode(Object object) {  
    	if( object instanceof DynamicMessage ) {
    		DynamicMessage dm = (DynamicMessage)object;
    		FieldDescriptor f = dm.getDescriptorForType().findFieldByName(retCodeFieldInMap);
    		if( f == null )
    			return -99999999;  // should not be executed
    		return (Integer)dm.getField(f);
    	}
    	
        try {  
        	Field f = retCodeFields.get(object.getClass().getName());
        	if( f == null ) {
        		return -99999999;  // should not be executed
        	}

            return (Integer)f.get(object);
        } catch(Exception e) {  
        	log.error("getRetCode exception",e);
        	return -99999999; // should not be called
        }   
    }
    
    public static void setRetCode(Object object,int retCode) {  
        try {  
        	Field f = retCodeFields.get(object.getClass().getName());
        	if( f == null ) {
        		throw new RuntimeException("setRetCode exception, no field found");
        	}
            f.set(object,retCode);
        } catch(Exception e) {  
        	throw new RuntimeException("setRetCode exception");
        }   
    }
    
    public static String getRetMsg(Object object) {  
    	if( object instanceof DynamicMessage ) {
    		DynamicMessage dm = (DynamicMessage)object;
    		FieldDescriptor f = dm.getDescriptorForType().findFieldByName(retMsgFieldInMap);
    		if( f == null )
    			return "";
    		return (String)dm.getField(f);
    	}    	
    	
        try {  
        	Field f = retMsgFields.get(object.getClass().getName());
        	if( f == null ) {
        		return "";
        	}

        	Object o = (String)f.get(object);
            if (o instanceof String) {
                return (String) o;
              } else {
                ByteString bs = (ByteString) o;
                return bs.toStringUtf8(); 
              }
        } catch(Exception e) {  
        	log.error("getRetMsg exception",e);
        	return "";
        }   
    }
    
    public static void setRetMsg(Object object,String retMsg) {  
        try {  
        	Field f = retMsgFields.get(object.getClass().getName());
        	if( f == null ) {
        		return;
        	}
        	if( retMsg == null ) retMsg = "";
            f.set(object,retMsg);
        } catch(Exception e) {  
        	throw new RuntimeException("setRetMsg exception",e);
        }   
    }
	
    public static Class<?> getClass(String s) {
		try {
			return Class.forName(s);
		} catch(Throwable e) {
			return null;
		}
	}
	
	public static Object newRefererObject(Class<?> cls,Object callable) { 
		try {  
			Constructor<?> cons = cls.getDeclaredConstructor(callableTypes);
	    	return cons.newInstance(new Object[]{ callable });
	    } catch(Exception e) {  
	    	throw new RuntimeException("newObject exception",e);
	    }       	
	}
    
	public static Object newObject(String clsName) { 
		try {  
			Class<?> cls = Class.forName(clsName);
			Constructor<?> cons = cls.getDeclaredConstructor();
	    	return cons.newInstance(new Object[]{});
	    } catch(Throwable e) {  
	    	throw new RuntimeException("newObject exception",e);
	    }       	
	}

	@SuppressWarnings("all")
	public static Object invokeMethod(Object obj,String methodName) {  
	    try {  
        	Method method = obj.getClass().getDeclaredMethod(methodName,dummyTypes);
        	return method.invoke(obj, dummyParameters);
	    } catch(Exception e) {  
	    	log.error("invokeMethod exception, e="+e.getMessage());
	    	return null;
	    }   
	}
	
	public static Object generateResponseObject(Class<?> cls,int retCode,String retMsg) { 
		String key = cls.getName()+":"+retCode;
		Object o = errors.get(key);
		if( o != null ) return o;
		o = generateResponseObjectNoCache(cls,retCode,retMsg);
		errors.put(key,o);
		return o;
	}

	@SuppressWarnings("all")
	public static Object generateResponseObjectNoCache(Class<?> cls,int retCode,String retMsg) {  
		
	    try {  
	    	if( retCode == 0 ) {
	        	Method method = cls.getDeclaredMethod("getDefaultInstance",dummyTypes);
	        	return method.invoke(null, dummyParameters);
	    	}
	
	    	Method method = cls.getDeclaredMethod("newBuilder",dummyTypes);
	        Object builder = method.invoke(null,dummyParameters);
	    	Method buildMethod = builder.getClass().getDeclaredMethod("build",dummyTypes);
	    	Object obj = buildMethod.invoke(builder,dummyParameters);
	
	    	setRetCode(obj,retCode);
	    	if( retMsg != null && retMsg.length() > 0 ) setRetMsg(obj,retMsg);
	        return obj;
	    } catch(Exception e) {  
	    	throw new RuntimeException("generateResponseObjectNoCache exception",e);
	    }   
	}

	@SuppressWarnings("all")
	public static Builder generateBuilder(Class<?> cls) {  
	    try {  
		    Method method = cls.getDeclaredMethod("newBuilder",dummyTypes);
	    	Builder builder = (Builder)method.invoke(null,dummyParameters);
	        return builder;
	    } catch(Exception e) {  
	    	throw new RuntimeException("generateBuilder exception",e);
	    }   
	}

	public static void adjustPeers(RpcMeta meta,String connId) {
		try {  
			int p = connId.lastIndexOf(":");
			String addr = connId.substring(0,p);
			String peers = meta.getTrace().getPeers();
			String newPeers = peers.isEmpty() ? addr : peers+","+addr;
			metaPeersField.set(meta.getTrace(),newPeers);
        } catch(Exception e) {  
        	log.error("adjustPeers exception");
        }   			
	}

	public static void updateSequence(RpcMeta meta,int sequence) {
		try {  
			metaSequenceField.set(meta,sequence);
        } catch(Exception e) {  
        	log.error("adjustPeers exception");
        }   			
	}
		
	public static void updateCompress(RpcMeta meta,int zip) {
		try {  
			metaCompressField.set(meta,zip);
        } catch(Exception e) {  
        	log.error("updateCompress exception");
        }   			
	}
	
	public static void checkInterface(Class<?> intf, Object obj) {
		if( intf.isAssignableFrom(obj.getClass()) ) return;
		throw new RuntimeException("not a valid service object");
	}
	
	public static void checkInterface(String intfName, Object obj) {
		try {
			Class<?> intf = Class.forName(intfName);
			if( intf.isAssignableFrom(obj.getClass()) ) return;
			throw new RuntimeException("not a valid service object");
		} catch(Throwable e) {
			throw new RuntimeException("interface not found, cls="+intfName);
		}
	}
	
    public static int getServiceId(Class<?> intf) {  
    	try {  
			Field field = intf.getDeclaredField("serviceId");

		    if (Modifier.isStatic(field.getModifiers())) {
		    	int serviceId = (Integer)field.get(null);
		    	return serviceId;
			}
		    throw new RuntimeException("interface_parse_serviceId_exception");
    	} catch(Exception e) {  
        	throw new RuntimeException("interface_parse_serviceId_exception");
        }
    }
    
    public static HashMap<Integer,String> getMsgIds(Class<?> intf) {
    	try {  
			Field[] declaredFields = intf.getDeclaredFields();
			HashMap<Integer,String> msgIds = new HashMap<Integer,String>();
			for (Field field : declaredFields) {
			    if (Modifier.isStatic(field.getModifiers())) {
			    	if( field.getName().endsWith("MsgId")) {
			    		int msgId = field.getInt(null);
			    		msgIds.put(msgId,field.getName().substring(0,field.getName().length()-5));
			    	}
			    }
			}
			return msgIds;
    	} catch(Exception e) {  
        	throw new RuntimeException("interface_parse_msgId_exception");
        }
	}
    
    public static HashMap<String,Integer> getMsgNames(Class<?> intf) {
    	try {  
			Field[] declaredFields = intf.getDeclaredFields();
			HashMap<String,Integer> msgIds = new HashMap<String,Integer>();
			for (Field field : declaredFields) {
			    if (Modifier.isStatic(field.getModifiers())) {
			    	if( field.getName().endsWith("MsgId")) {
			    		int msgId = field.getInt(null);
			    		msgIds.put(field.getName().substring(0,field.getName().length()-5),msgId);
			    	}
			    }
			}
			return msgIds;
    	} catch(Exception e) {  
        	throw new RuntimeException("interface_parse_msgId_exception");
        }
	}
    
    public static HashMap<String,Object> getMethodInfo(Class<?> intf) {  
    	try {  
			Method[] methods = intf.getDeclaredMethods();
			HashMap<String,Object> msgNames = new HashMap<String,Object>();
			for (Method m : methods) {
			    if (Modifier.isStatic(m.getModifiers())) continue;
			    if( m.getParameterCount() != 1 ) continue;
			    Class<?> reqCls = m.getParameterTypes()[0];
			    Class<?> resCls = m.getReturnType();
			    if( !Message.class.isAssignableFrom(reqCls) ) continue;
			    if( !Message.class.isAssignableFrom(resCls) ) continue;	    
			    msgNames.put(m.getName(), m);
			    msgNames.put(m.getName()+"-req", reqCls);
			    msgNames.put(m.getName()+"-res", resCls);
			    msgNames.put(m.getName()+"-reqp", reqCls.getDeclaredMethod("parseFrom", InputStream.class ));
			    msgNames.put(m.getName()+"-resp", resCls.getDeclaredMethod("parseFrom", InputStream.class ));
			    
        		Field f1 = resCls.getDeclaredField(retCodeField);
    			f1.setAccessible(true);
    			retCodeFields.put(resCls.getName(),f1);
    			try {
	        		Field f2 = resCls.getDeclaredField(retMsgField);
	    			f2.setAccessible(true);
	    			retMsgFields.put(resCls.getName(),f2);    
    			} catch(Exception e) {
    			}
			}
			return msgNames;
    	} catch(Exception e) {  
        	throw new RuntimeException("getMethodInfo",e);
        }
    }
    
    public static HashMap<String,Method> getParsers(Class<?> reqCls,Class<?> resCls) {  
    	try {  
			HashMap<String,Method> map = new HashMap<String,Method>();
			map.put("reqp", reqCls.getDeclaredMethod("parseFrom", InputStream.class ));
			map.put("resp", resCls.getDeclaredMethod("parseFrom", InputStream.class ));
			    
    		Field f1 = resCls.getDeclaredField(retCodeField);
			f1.setAccessible(true);
			retCodeFields.put(resCls.getName(),f1);
			try {
        		Field f2 = resCls.getDeclaredField(retMsgField);
    			f2.setAccessible(true);
    			retMsgFields.put(resCls.getName(),f2);    
			} catch(Exception e) {
			}
			return map;
    	} catch(Exception e) {  
        	throw new RuntimeException("getMethodInfo",e);
        }
    }
    
    public static HashMap<String,Object> getAsyncMethodInfo(Class<?> intf) {  
    	try {  
			Method[] methods = intf.getDeclaredMethods();
			HashMap<String,Object> msgNames = new HashMap<String,Object>();
			for (Method m : methods) {
			    if (Modifier.isStatic(m.getModifiers())) continue;
			    if( m.getParameterCount() != 1 ) continue;
			    Class<?> reqCls = m.getParameterTypes()[0];
			    Class<?> resFutureCls = m.getReturnType();
			    if( !Message.class.isAssignableFrom(reqCls) ) continue;
			    if( !CompletableFuture.class.isAssignableFrom(resFutureCls) ) continue;	
			    Class<?> resCls = parseParameterCls(m.getGenericReturnType());
			    if( !Message.class.isAssignableFrom(resCls) ) continue;
			    
			    msgNames.put(m.getName(), m);
			    msgNames.put(m.getName()+"-req", reqCls);
			    msgNames.put(m.getName()+"-res", resCls);
			    msgNames.put(m.getName()+"-reqp", reqCls.getDeclaredMethod("parseFrom", InputStream.class ));
			    msgNames.put(m.getName()+"-resp", resCls.getDeclaredMethod("parseFrom", InputStream.class ));
			    
        		Field f1 = resCls.getDeclaredField(retCodeField);
    			f1.setAccessible(true);
    			retCodeFields.put(resCls.getName(),f1);
    			try {
	        		Field f2 = resCls.getDeclaredField(retMsgField);
	    			f2.setAccessible(true);
	    			retMsgFields.put(resCls.getName(),f2);    
    			} catch(Exception e) {
    			}	    			
			}
			return msgNames;
    	} catch(Exception e) {  
        	throw new RuntimeException("getAsyncMethodInfo",e);
        }
    }    
    
    private static Class<?> parseParameterCls(Type t) throws ClassNotFoundException {
    	String s = t.toString();
    	int p1 = s.indexOf("<");
    	int p2 = s.indexOf(">");
    	String clsName = s.substring(p1+1,p2);
    	return Class.forName(clsName);
    }

    public static DynamicMessage.Builder generateDynamicBuilder(Descriptor desc) {
    	return DynamicMessage.newBuilder(desc);
    }
    
	public static Object generateResponseObject(DynamicMessage.Builder b,String cacheName,int retCode,String retMsg) { 
		String key = cacheName+":"+retCode;
		Object o = errors.get(key);
		if( o != null ) return o;
		o = generateResponseObjectNoCache(b,retCode,retMsg);
		errors.put(key,o);
		return o;
	}

	@SuppressWarnings("all")
	public static Object generateResponseObjectNoCache(DynamicMessage.Builder b,int retCode,String retMsg) {  
	    try {  
	    	for (FieldDescriptor field : b.getDescriptorForType().getFields()) {
	    		if( field.getName().equals(retCodeFieldInMap) )
	    			b.setField(field, retCode );
	    		if( field.getName().equals(retMsgFieldInMap) )
	    			b.setField(field, retMsg );
	    	}
	        return b.build();
	    } catch(Exception e) {  
	    	throw new RuntimeException("generateResponseObjectNoCache exception",e);
	    }   
	}
	
}
