package krpc.rpc.web.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import krpc.common.InitClose;
import krpc.rpc.core.Continue;
import krpc.rpc.core.Plugin;
import krpc.rpc.web.SessionService;
import krpc.trace.Trace;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

public class JedisSessionService implements SessionService, InitClose {
	
	static Logger log = LoggerFactory.getLogger(JedisSessionService.class);
	
	private JedisPool jedisPool;
	private JedisCluster jedisCluster;
	
	private int expireSeconds = 600;
	private boolean clusterMode = false;
	private String addrs;
	
	private String keyPrefix = "KRW_";
	
	// todo more control parameters

	public void config(String paramsStr) {
		Map<String,String> params = Plugin.defaultSplitParams(paramsStr);
		String s = params.get("expireSeconds");
		if ( s != null && !s.isEmpty() )
			expireSeconds = Integer.parseInt(s);				
		s = params.get("clusterMode");
		if ( s != null && !s.isEmpty() )
			clusterMode = Boolean.parseBoolean(s);				
		s = params.get("addrs");
		if ( s != null && !s.isEmpty() )
			addrs = s;				
		s = params.get("keyPrefix");
		if ( s != null && !s.isEmpty() )
			keyPrefix = s;				
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
	}

	public void close() {
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
	
	public void load(String sessionId,Map<String,String> values,Continue<Integer> cont) {
		String key = key(sessionId);
		
		Trace.start("REDIS", "hgetall");
		
		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = getJedis();  
	            Map<String,String> v = jedis.hgetAll(key);
	            values.putAll(v);
	            Trace.stop(true);
	        } catch (Exception e) {  
	            log.error("cannot load key, key="+key);
	            Trace.stop(false);
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
	            values.putAll(v);
	            Trace.stop(true);
			} catch (Exception e) {  
	            log.error("cannot load key, key="+key);
	            Trace.stop(false);
	        }
		}

		if( cont != null ) {
			cont.readyToContinue(0);
		}	
	}
	
	public void update(String sessionId, Map<String,String> values,Continue<Integer> cont) {

		String key = key(sessionId);
		
		Trace.start("REDIS", "hmset");
		
		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = getJedis();  
	            jedis.hmset(key, values);
	            jedis.expire(key, expireSeconds);
	            Trace.stop(true);
	        } catch (Exception e) {  
	            log.error("cannot update key, key="+key);
	            Trace.stop(false);
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
	            jedisCluster.hmset(key, values);
	            jedisCluster.expire(key, expireSeconds);
	            Trace.stop(true);
			} catch (Exception e) {  
	            log.error("cannot update key, key="+key);
	            Trace.stop(false);
	        }	            
		}
		
		if( cont != null ) {
			cont.readyToContinue(0);
		}		
	}
	
	public void remove(String sessionId,Continue<Integer> cont) {
		
		String key = key(sessionId);
		
		Trace.start("REDIS", "del");
		
		if(!clusterMode) {
	        Jedis jedis = null;  
	        try {  
	            jedis = getJedis();  
	            jedis.del(key);  
	            Trace.stop(true);
	        } catch (Exception e) {  
	            log.error("cannot delete key, key="+key);
	            Trace.stop(false);
	        } finally {  
	        	try {
	        		if( jedis != null )
	        			jedis.close();
	        	} catch(Exception e) {
	        	}
	        }  
		} else {
			try {  
				jedisCluster.del(key);
	            Trace.stop(true);
			} catch (Exception e) {  
	            log.error("cannot del key, key="+key);
	            Trace.stop(false);
	        }					
		}
		
		if( cont != null ) {
			cont.readyToContinue(0);
		}
		
	}
	
	Jedis getJedis() {
		if(!clusterMode) {
			return jedisPool.getResource();  
		} else {
			return null;
		}
	}
	
	String key(String sessionId) {
		return keyPrefix+sessionId;
	}
}
