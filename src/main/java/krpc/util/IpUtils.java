package krpc.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;

// import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpUtils {

	static Logger log = LoggerFactory.getLogger(IpUtils.class);
	
    static ArrayList<String> localips  = new ArrayList<String>();
    static ArrayList<String> netips = new ArrayList<String>();

    static {
    	loadIps();
    }

    static void loadIps() {

        try {
        	Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
            	Enumeration<InetAddress> address = netInterfaces.nextElement().getInetAddresses();
                while (address.hasMoreElements()) {
                	InetAddress ip = address.nextElement();

                    if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {
                        netips.add( ip.getHostAddress() );
                    } else if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {
                        localips.add( ip.getHostAddress() );
                    }
                }
            }
        } catch(Exception e) {
        }
    }

    static String localIp0() {

        try {
        	InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch(Exception e) {
            return "127.0.0.1";
        }

    }

    static public String localIp() {

        if( true ) {
            String envhost = System.getenv("KRPC_HOST"); // used in docker
            if( envhost != null && !envhost.equals("") ) {
                try {
                	InetAddress addr = InetAddress.getByName(envhost);
                    String s = addr.getHostAddress();
                    return s;
                } catch(Exception e) {
                    log.error("cannot get host address, use local ip");
                }
            }
        }
    	
        String docker0 = "172.17.0.1";
        localips.remove(docker0);

        String ip0 = localIp0();
        
        if ( localips.size() > 0  ) {
            if( localips.contains(ip0)) return ip0;
            return localips.get(0) ;
        }

        if ( netips.size() > 0  ) {
            if( netips.contains(ip0)) return ip0;
            return netips.get(0) ;
        }

        return ip0;
    }

}


