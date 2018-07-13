package krpc.test.misc;

import krpc.common.Json;
import krpc.rpc.core.DynamicRouteConfig;
import krpc.rpc.core.DynamicRouteConfig.AddrWeight;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DynamicRouteConfigTest {

    @Test
    public void test1() throws Exception {

        DynamicRouteConfig c = new DynamicRouteConfig();
        c.setServiceId(100);
        c.setDisabled(false);

        List<AddrWeight> weights = new ArrayList<>();
        AddrWeight w = new AddrWeight("192.168.31.27", 50);
        weights.add(w);
        AddrWeight w2 = new AddrWeight("192.168.31.28", 50);
        weights.add(w2);
        c.setWeights(weights);

        List<RouteRule> rules = new ArrayList<>();
        RouteRule r1 = new RouteRule("host = 192.168.31.27", "host = 192.168.31.27", 2);
        rules.add(r1);
        RouteRule r2 = new RouteRule("host = 192.168.31.28", "host = $host", 1);
        rules.add(r2);
        c.setRules(rules);

        String json = Json.toJson(c);
        System.out.println(json);

        // {"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27","weight":50},{"addr":"192.168.31.28","weight":50}],"rules":[{"from":"host = 192.168.31.27","to":"host = 192.168.31.27","priority":2},{"from":"host = 192.168.31.28","to":"host = $host","priority":1}]}

    }


    @Test
    public void test2() throws Exception {

        DynamicRouteConfig c = new DynamicRouteConfig();
        c.setServiceId(100);
        c.setDisabled(false);

        List<AddrWeight> weights = new ArrayList<>();
        AddrWeight w = new AddrWeight("192.168.31.27:5600", 50);
        weights.add(w);
        AddrWeight w2 = new AddrWeight("192.168.31.27:5601", 25);
        weights.add(w2);
        AddrWeight w3 = new AddrWeight("192.168.31.27:5602", 25);
        weights.add(w3);
        c.setWeights(weights);


        String json = Json.toJson(c);
        System.out.println(json);

        // {"serviceId":100,"disabled":false,"weights":[{"addr":"192.168.31.27:5600","weight":50},{"addr":"192.168.31.27:5601","weight":25},{"addr":"192.168.31.27:5602","weight":25}]}

    }


}

