package krpc.rpc.cluster.utils;

import krpc.rpc.core.ConnectionPlugin;
import krpc.rpc.util.TypeSafe;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/*

    此类提供的功能：

        getTargetIp 获取一个可用的IP
            对某个serviceId + key 获取一个可用的IP地址 （此IP地址在多进程之间保证一致, 通过redis）,
            这样保证所有数据可以发到相同的一台后端服务器上做累计操作

        getConnIdByIp 根据IP获取可用connId，用于RPC调用

        此类仅提供部分负载均衡功能，要保证数据只发往一台服务器且不丢失，还需配合重试功能

    使用方法：

        krpc.client.connectionPlugin = stickyConnections

        @Bean
        StickyConnections stickyConnections() {
            StickyConnections o = new StickyConnections();
            o.setCurrentServiceId(123); // current service is 123
            o.setStickyServices("345,456"); // 123 service connects to 345 and 456 service
            o.setExpireMinutes(15);
            o.setStringRedisTemplate(stringRedisTemplate);
            return o;
        }

*/

public class StickyConnections implements ConnectionPlugin {

    StringRedisTemplate stringRedisTemplate;
    int currentServiceId;
    String stickyServices;
    int expireMinutes = 15;

    Map<String,String> conns = new TreeMap<>(); // serviceId-ip -> connId

    static class IpCacheInfo {
        String ip;
        long minute = genMinute(System.currentTimeMillis());

        IpCacheInfo(String ip) {
            this.ip = ip;
        }
    }

    ConcurrentHashMap<String,IpCacheInfo> ipCache = new ConcurrentHashMap<>();
    Random rand = new Random();
    Timer t;
    Set serviceIds = new HashSet<>();

    @PostConstruct
    void init() {
        String[] ss = stickyServices.split(",");
        for(String s: ss) {
            serviceIds.add(TypeSafe.anyToInt(s));
        }

        t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                clean();
            }
        }, 60000, 60000);
    }

    @PreDestroy
    void close() {
        if (t != null) {
            t.cancel();
            t = null;
        }
    }

    static private long genMinute(long ts) {
        return ts / 60000;
    }

    private void clean() {
        long now = System.currentTimeMillis();
        long nowMinute = genMinute(now);

        List<String> saveKeys = new ArrayList<>();
        ipCache.forEach( (k,v) -> {
            if( nowMinute - v.minute >= expireMinutes ) {
                saveKeys.add(k);
            }
        });
        if( saveKeys.size() == 0 ) return;
        saveKeys.forEach( ipCache::remove );
    }

    public String getTargetIp(int serviceId, String key0) {
        String key = serviceId + "-" + key0;

        IpCacheInfo ip1 = ipCache.get(key);
        if( ip1 != null ) return ip1.ip;

        String ip2 = randomIp(serviceId); // 随机选一个当前可用的IP
        if( ip2 == null ) return null;

        String ip3 = setOrGetIp(key, ip2);
        if (ip3 != null) return ip3;

        return null; // 不再查了
    }

    private String setOrGetIp(String key, String ip) {

        String redisKey = currentServiceId + "_" + key; // 前缀是服务号

        try {
            Boolean succ = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, ip, expireMinutes, TimeUnit.MINUTES);
            if (succ != null && succ ) {
                ipCache.put(key, new IpCacheInfo(ip));
                return ip;
            }

            String existIp = stringRedisTemplate.opsForValue().get(redisKey);
            if (existIp != null) {
                ipCache.put(key, new IpCacheInfo(existIp));
                return existIp;
            }

            return null;

        } catch(Exception e) {
            return null;
        }
    }

    private String randomIp(int serviceId) {
        List<String> allKeys = new ArrayList<>(conns.keySet());
        if( allKeys.size() == 0 ) return null;
        if( serviceIds.size() == 1  ) {
            if( allKeys.size() == 1 )
                return removeServiceId(allKeys.get(0));
            int index = rand.nextInt(allKeys.size());
            return removeServiceId(allKeys.get(index));
        }

        String prefix = serviceId + "-";
        List<String> serviceKeys = allKeys.stream().filter(s->s.startsWith(prefix)).collect(Collectors.toList());
        if( serviceKeys.size() == 1 ) return serviceKeys.get(0);
        int index = rand.nextInt(serviceKeys.size());
        return removeServiceId(serviceKeys.get(index));
    }

    private String key(int serviceId, String ip) {
        return serviceId + "-" + ip;
    }

    private String removeServiceId(String key) {
        int p = key.indexOf("-");
        return key.substring(p+1);
    }

    public String getConnIdByIp(int serviceId, String ip) {
        return conns.get(key(serviceId,ip));
    }

    @Override
    public void connected(String connId, String localAddr) {
        int serviceId = getServiceId(connId);
        if( serviceIds.contains(serviceId) ) {
            conns.put(key(serviceId,clientIp(connId)),connId);
        }
    }

    @Override
    public void disconnected(String connId) {
        int serviceId = getServiceId(connId);
        if( serviceIds.contains(serviceId) ) {
            conns.remove(key(serviceId,clientIp(connId)));
        }
    }

    private int getServiceId(String connId) {
        String[] ss = connId.split(":");
        String port = ss[1];
        return TypeSafe.anyToInt(port.substring(1));
    }

    private String clientIp(String connId) {
        int p = connId.indexOf(":");
        return connId.substring(0,p);
    }

    public StringRedisTemplate getStringRedisTemplate() {
        return stringRedisTemplate;
    }

    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public int getCurrentServiceId() {
        return currentServiceId;
    }

    public void setCurrentServiceId(int currentServiceId) {
        this.currentServiceId = currentServiceId;
    }

    public String getStickyServices() {
        return stickyServices;
    }

    public void setStickyServices(String stickyServices) {
        this.stickyServices = stickyServices;
    }

    public int getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(int expireMinutes) {
        this.expireMinutes = expireMinutes;
    }
}
