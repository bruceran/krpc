package krpc.rpc.core;

public interface ProxyGenerator {
	Object generateReferer(Class<?> intf,RpcCallable callable) ;
	Object generateAsyncReferer(Class<?> intf,RpcCallable callable);
}

