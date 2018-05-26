package krpc.rpc.core;

import java.lang.reflect.Method;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;

public interface ServiceMetas {
	
	Object findService(int serviceId);
	Object findReferer(int serviceId);
    Object findAsyncReferer(int serviceId);
    Method findMethod(int serviceId,int msgId);
    Method findAsyncMethod(int serviceId,int msgId);   // used only for client
    Class<?> findReqClass(int serviceId,int msgId);
    Class<?> findResClass(int serviceId,int msgId);
    Method findReqParser(int serviceId,int msgId);
    Method findResParser(int serviceId,int msgId);

	String getName(int serviceId,int msgId);
	RpcCallable findCallable(String implClsName);
    
    Message generateRes(int serviceId,int msgId, int retCode);
    
	void addService(Class<?> intf, Object impl,RpcCallable callable);
	void addReferer(Class<?> intf, Object impl,RpcCallable callable);
	void addAsyncReferer(Class<?> intf, Object impl,RpcCallable callable);
	void addDirect(int serviceId,int msgId, Class<?> reqCls, Class<?> resCls); // for monitor use
	
	Descriptor findDynamicReqDescriptor(int serviceId,int msgId);
	Descriptor findDynamicResDescriptor(int serviceId,int msgId);
	RpcCallable findDynamicCallable(int serviceId);
	void addDynamic(int serviceId,int msgId, Descriptor reqDesc, Descriptor resDesc, String serviceName,String msgName);
	void addDynamic(int serviceId,RpcCallable callable);
}
