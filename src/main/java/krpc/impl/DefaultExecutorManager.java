package krpc.impl;

import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import krpc.core.ExecutorManager;
import krpc.core.InitClose;
import krpc.util.NamedThreadFactory;

public class DefaultExecutorManager implements ExecutorManager, InitClose {
	
	NamedThreadFactory threadFactory = new NamedThreadFactory("rpc_server_worker");
	HashMap<String,ThreadPoolExecutor> pools = new HashMap<String,ThreadPoolExecutor>();

    public ThreadPoolExecutor getExecutor(int serviceId,int msgId) {
    	ThreadPoolExecutor pool = pools.get(serviceId+"."+msgId);
    	if( pool == null ) pool = pools.get(serviceId+".-1");
    	if( pool == null ) pool = pools.get("-1.-1");
    	return pool;
    }
    
	public void addDefaultPool(int threads,int maxThreads,int queueSize) {
        addPool(-1,threads,maxThreads,queueSize);
	}   

    public void addPool(int serviceId,int threads,int maxThreads,int queueSize) {
    	ThreadPoolExecutor pool = null;
    	if( maxThreads > threads )
    		pool = new ThreadPoolExecutor(threads, maxThreads, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
    	else
    		pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
    	pools.put(serviceId+".-1", pool);
	}   
	
	public void addPool(int serviceId,int[] msgIds,int threads,int maxThreads,int queueSize) {
    	ThreadPoolExecutor pool = null;
    	if( maxThreads > threads )
    		pool = new ThreadPoolExecutor(threads, maxThreads, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
    	else
    		pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
        for(int msgId:msgIds) {
        	pools.put(serviceId+"."+msgId, pool);
        }
	}    
	
	public void init() {
		for(String key:pools.keySet()) {
			ThreadPoolExecutor pool = pools.get(key);
			pool.prestartAllCoreThreads();
		}
	}
	
	public void close() {
		for(String key:pools.keySet()) {
			ThreadPoolExecutor pool = pools.get(key);
			pool.shutdown();
		}
	}
}

