package krpc.rpc.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.rpc.core.Continue;
import krpc.rpc.core.FlowControl;
import krpc.rpc.core.Plugin;
import krpc.rpc.util.NamedThreadFactory;
import krpc.trace.Trace;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

public class JedisFlowControl implements FlowControl,InitClose {
	
	static Logger log = LoggerFactory.getLogger(JedisFlowControl.class);
	
	private JedisPool jedisPool;
	private JedisCluster jedisCluster;
	
	private boolean clusterMode = false;
	private boolean syncUpdate = false;
		
	private String addrs;
	
	private String keyPrefix = "FC_";
		
	HashMap<Integer,List<StatItem>> serviceStats = new HashMap<>();
	HashMap<String,List<StatItem>> msgStats = new HashMap<>();
	
	int threads = 1;
	int maxThreads = 0;
	int queueSize = 10000;
	
	NamedThreadFactory threadFactory = new NamedThreadFactory("jedisflowcontrol_threads");
	ThreadPoolExecutor pool = null;
	
	static class StatItem {
		int seconds;
		int limit;

		StatItem(int seconds,int limit) {
			this.seconds = seconds;
			this.limit = limit;
		}
	}

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);			
		String s = params.get("clusterMode");
		if ( s != null && !s.isEmpty() )
			clusterMode = Boolean.parseBoolean(s);				
		s = params.get("addrs");
		if ( s != null && !s.isEmpty() )
			addrs = s;				
		s = params.get("keyPrefix");
		if ( s != null && !s.isEmpty() )
			keyPrefix = s;				
		s = params.get("threads");
		if ( s != null && !s.isEmpty() )
			threads = Integer.parseInt(s);			
		s = params.get("maxThreads");
		if ( s != null && !s.isEmpty() )
			maxThreads = Integer.parseInt(s);			
		s = params.get("queueSize");
		if ( s != null && !s.isEmpty() )
			queueSize = Integer.parseInt(s);		
		s = params.get("syncUpdate");
		if ( s != null && !s.isEmpty() )
			syncUpdate = Boolean.parseBoolean(s);		
	}
	
	public void init() {
		if(!clusterMode) {
			String[] ss = addrs.split(":");
			jedisPool = new JedisPool(ss[0],Integer.parseInt(ss[1]));
		} else {
			Set<HostAndPort> hosts = new HashSet<>();
			String[] ss =  addrs.split(",");
			for(String s : ss ) {
				String[] tt = s.split(":");
				hosts.add(new HostAndPort(tt[0],Integer.parseInt(tt[1])));
			}
			try {
				jedisCluster = new JedisCluster(hosts);
			} catch(Exception e) {
				throw new RuntimeException("cannot init jedis cluster",e);
			}
		}
    	if( maxThreads > threads )
    		pool = new ThreadPoolExecutor(threads, maxThreads, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
    	else
    		pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize),threadFactory);
		
	}

	public void close() {
		
		pool.shutdown();
		
		if(!clusterMode) {
			jedisPool.close();
		} else {
			try {
				jedisCluster.close();
			} catch(Exception e) {
				log.error("close cluster exception, e="+e.getMessage());
			}
		}
	}

	public void addLimit(int serviceId,int seconds,int limit) {
    	List<StatItem> list = serviceStats.get(serviceId);
    	if( list == null ) {
    		list = new ArrayList<StatItem>();
    		list.add( new StatItem(seconds,limit) );
    	}
    	serviceStats.put(serviceId, list);
	}
	
	public void addLimit(int serviceId,int msgId,int seconds,int limit) {
		String key = serviceId + "." + msgId;
    	List<StatItem> list = msgStats.get(key);
    	if( list == null ) {
    		list = new ArrayList<StatItem>();
    		list.add( new StatItem(seconds,limit) );
    	}
    	msgStats.put(key, list);		
	}
	
	
	public boolean isAsync() { return false; }

    public boolean exceedLimit(int serviceId,int msgId,Continue<Boolean> dummy) {
    	long now = System.currentTimeMillis()/1000;
    	boolean failed1 = updateServiceStats(serviceId,now);
    	boolean failed2 = updateMsgStats(serviceId,msgId,now);
    	return failed1 || failed2;
    }

    boolean updateServiceStats(int serviceId,long now) {
    	List<StatItem> list = serviceStats.get(serviceId);
    	if( list == null ) return false;
    	return updateStats(String.valueOf(serviceId),list,now);
    }

    boolean updateMsgStats(int serviceId,int msgId,long now) {
    	String key = serviceId + "." + msgId;
    	List<StatItem> list = msgStats.get(key);
    	if( list == null ) return false;
    	return updateStats(key,list,now);
    }
    
    boolean updateStats(String key, List<StatItem> stats,long now) {

    	Map<String,String> values = hgetall(key);
    	if( values == null ) return false; // something wrong in jedis

    	List<String> incFieldsList = new ArrayList<>();
    	List<String> removeFieldsList = new ArrayList<>();
    	
    	boolean failed = false;
    	if( values.size() > 0 ) {
        	for(StatItem stat: stats) {
        		long time = (now / stat.seconds ) * stat.seconds;
        		String prefix = stat.seconds + "_";
        		
        		String stat_field =  prefix + time;
        		if( values.containsKey(stat_field) ) {
        			int curValue = Integer.parseInt( values.get(stat_field) );
        			if( curValue >= stat.limit ) failed = true;
        		} 
        		incFieldsList.add(stat_field);
        		
            	for(Map.Entry<String,String> entry:values.entrySet()) {
            		String field = entry.getKey();
            		if( field.startsWith(prefix) ) {
                		long t = Long.parseLong( field.substring(prefix.length()) );
            			if( t < time ) {
            				removeFieldsList.add(field);
            			}
            		}
            	}
        	}
    	} else {
    		for(StatItem stat: stats) {
        		long time = (now / stat.seconds ) * stat.seconds;
        		String prefix = stat.seconds + "_";
        		String stat_field =  prefix + time;
        		incFieldsList.add(stat_field);
    		}
    	}

    	if( syncUpdate ) {
    		doUpdate(key,incFieldsList,removeFieldsList);
    	} else {
    		try {
	    		pool.execute( new Runnable() {
	    			public void run() {
	    				doUpdate(key,incFieldsList,removeFieldsList);
	    			}
	    		});
    		} catch(Exception e) {
    			log.error("redis update pool is full");
    		}
    	}

    	return failed;
    }
    
    void doUpdate(String key,List<String> incFieldsList,List<String> removeFieldsList) {

    	Trace.start("REDIS", "update_and_delete");
    	
    	if( incFieldsList.size() > 0 ) {
    		hincrby(key,incFieldsList);
    	}
    	if( removeFieldsList.size() > 0 ) {
    		del(key,removeFieldsList);
    	}
    	
        Trace.stop(true);    	
    }
    
    void hincrby(String key,List<String> fields) {
		key = keyPrefix + key;

		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = jedisPool.getResource();  
	            for(String field:fields)
	            	jedis.hincrBy(key,field,1);
	        } catch (Exception e) {  
	            log.error("cannot hincrby key, key="+key);
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
	            for(String field:fields)
	            	jedisCluster.hincrBy(key,field,1);				
			} catch (Exception e) {  
	            log.error("cannot hincrby key, key="+key);
	        }
		}
	}    

    
    void del(String key,List<String> fields) {
		key = keyPrefix + key;

		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = jedisPool.getResource();  
	            for(String field:fields)
	            	jedis.hdel(key,field);
	        } catch (Exception e) {  
	            log.error("cannot hdel key, key="+key);
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
	            for(String field:fields)
	            	jedisCluster.hdel(key,field);				
			} catch (Exception e) {  
	            log.error("cannot hdel key, key="+key);
	        }
		}
	}    
    
    Map<String,String> hgetall(String key) {
		key = keyPrefix + key;
		
		Trace.start("REDIS", "hgetall");
		
		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = jedisPool.getResource();  
	            Map<String,String> v = jedis.hgetAll(key);
	            Trace.stop(true);
	            return v;
	        } catch (Exception e) {  
	            log.error("cannot load key, key="+key);
	            Trace.stop(false);
	            return null;
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
	            Map<String,String> v = jedisCluster.hgetAll(key);
	            Trace.stop(true);
	            return v;
			} catch (Exception e) {  
	            log.error("cannot load key, key="+key);
	            Trace.stop(false);
	            return null;
	        }
		}

	}    
    
}

