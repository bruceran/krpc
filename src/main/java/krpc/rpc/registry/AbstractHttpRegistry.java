package krpc.rpc.registry;

import krpc.common.AlarmAware;
import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.httpclient.DefaultHttpClient;
import krpc.rpc.core.Registry;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

abstract public class AbstractHttpRegistry implements Registry, InitClose, AlarmAware {

    String addrs;
    String[] addrArray;
    int addrIndex = 0;
    boolean enableRegist = true;
    boolean enableDiscover = true;

    DefaultHttpClient hc;

    public void config(String paramsStr) {

        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        config(params);
    }

    public void config(Map<String, String> params) {

        addrs = params.get("addrs");
        addrArray = addrs.split(",");

        String s = params.get("enableRegist");
        if (!isEmpty(s)) enableRegist = Boolean.parseBoolean(s);

        s = params.get("enableDiscover");
        if (!isEmpty(s)) enableDiscover = Boolean.parseBoolean(s);
    }

    public String addr() {
        return addrArray[addrIndex];
    }

    public void nextAddr() {
        addrIndex++;
        if (addrIndex >= addrArray.length) addrIndex = 0;
    }

    public void init() {
        hc = new DefaultHttpClient();
        hc.init();
    }

    public void close() {
        if (hc == null) return;
        hc.close();
        hc = null;
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public String encode(String v) {
        try {
            return URLEncoder.encode(v, "utf-8");
        } catch (Exception e) {
            return v;
        }
    }

    public String decode(String v) {
        try {
            return URLDecoder.decode(v, "utf-8");
        } catch (Exception e) {
            return v;
        }
    }
}

