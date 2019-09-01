package krpc.test.misc;

import org.junit.Test;

import java.net.Inet4Address;

public class ClientIpTest {


    public String getClientIp(String[] ss) {

        int lastPublicIpIdx = -1;
        for(int i=ss.length-1;i>=0;--i) {
            String ip = ss[i];
            if( !isLocalIp(ip) ) {
                lastPublicIpIdx = i;
                break;
            }
        }

        if( lastPublicIpIdx >= 1 ) {
            return ss[lastPublicIpIdx-1].trim();
        }

        return ss[0].trim();
    }

    boolean isLocalIp(String ip) {
        if( ip.equals("127.0.0.1") ) return true;
        try {
            return Inet4Address.getByName(ip).isSiteLocalAddress();
        } catch(Exception e) {
            return true;
        }
    }


    @Test
    public void test1() {

        String s = "127.0.0.1,192.168.13.1";
        String clientIp = getClientIp(s.split(","));
        System.out.println("clientIp="+clientIp);

        s = "127.0.0.1,13.1.1.1";
        clientIp = getClientIp(s.split(","));
        System.out.println("clientIp="+clientIp);

        s = "127.0.0.1,13.1.1.1,14.4.4.4,15.5.5.5,10.191.1.1,127.0.0.1";
        clientIp = getClientIp(s.split(","));
        System.out.println("clientIp="+clientIp);


    }

    @Test
    public void test2() {

        try {
            System.out.println( Inet4Address.getByName(" 212.129.225.213") );
        } catch(Exception e) {
            System.out.println(e);
        }


    }

}

