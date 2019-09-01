package krpc.trace.adapter;

import krpc.rpc.impl.transport.NettyClient;

public class NettyClientTest {

    public static void main(String[] args) throws Exception {

        NettyClient t = new NettyClient();
        t.init();

        t.connect("10.1.20.73:7100:1","10.1.20.73:7100");
        sleepSeconds(10);
        t.disconnect("10.1.20.73:7100:1");

        sleepSeconds(180);
    }

    static void sleepSeconds(int n) {
        try {
            Thread.sleep(n * 1000);
        } catch(Exception e) {

        }
    }
}

