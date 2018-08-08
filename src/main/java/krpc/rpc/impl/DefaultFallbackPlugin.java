package krpc.rpc.impl;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import krpc.common.InitClose;
import krpc.common.Plugin;
import krpc.rpc.core.*;
import krpc.rpc.util.MapToMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultFallbackPlugin implements FallbackPlugin, InitClose, ServiceMetasAware {

    static Logger log = LoggerFactory.getLogger(DefaultFallbackPlugin.class);

    String file = "fallback.yaml";

    static class Item {
        String forName;
        String match;
        Map<String, Object> results;
    }

    List<Item> items = new ArrayList<>();

    static class Rule {
        FallbackExpr expr;
        Map<String, Object> results;

        boolean match(Message req) {
            if (expr == null) return true;
            FallbackMessageDataProvider dp = new FallbackMessageDataProvider(req);
            return expr.eval(dp);
        }
    }

    Map<String, List<Rule>> rules = new HashMap<>();

    FallbackExprParser parser = new FallbackExprParser();

    ServiceMetas serviceMetas; // MUST implements ServiceMetasAware interface to autowire

    public void config(String paramsStr) {
        Map<String, String> params = Plugin.defaultSplitParams(paramsStr);
        if (params.containsKey("file")) {
            file = params.get(file);
        }
    }

    public void init() {

        loadYaml();

        for (Item item : items) {
            String[] ss = item.forName.split("\\.");
            if (ss.length != 2) {
                log.error("invalid fallback file, for is not valid, for=" + item.forName);
                continue;
            }
            String serviceName = ss[0];
            String msgName = ss[1];
            String key = "";
            if (isInt(serviceName) && isInt(msgName)) {
                key = toInt(serviceName) + "." + toInt(msgName);
                String name = serviceMetas.getName(toInt(serviceName), toInt(msgName));
                if (name == null) {
                    log.error("invalid fallback file, for is not valid, for=" + item.forName);
                    continue;
                }
            } else {
                key = serviceMetas.getServiceIdMsgId(serviceName, msgName);
                if (key == null) {
                    log.error("invalid fallback file, for is not valid, for=" + item.forName);
                    continue;
                }
            }

            List<Rule> list = rules.get(key);
            if (list == null) {
                list = new ArrayList<>();
                rules.put(key, list);
            }
            Rule r = new Rule();
            if (!isEmpty(item.match)) {
                r.expr = parser.parse(item.match);
                if (r.expr == null) {
                    log.error("invalid fallback file, match is not valid, for=" + item.match);
                    continue;
                }
            }
            r.results = item.results;
            list.add(r);
        }
    }

    public void close() {
    }

    public Message fallback(RpcContextData ctx, Message req) {

        String key = ctx.getMeta().getServiceId() + "." + ctx.getMeta().getMsgId();
        List<Rule> list = rules.get(key);
        if (list == null || list.size() == 0) return null;

        for (Rule r : list) {
            if (r.match(req)) {
                int serviceId = ctx.getMeta().getServiceId();
                int msgId = ctx.getMeta().getMsgId();
                return toMessage(serviceId, msgId, r.results);
            }
        }

        return null;
    }

    public Message toMessage(int serviceId, int msgId, Map<String, Object> results) {
        Builder b = null;
        Class<?> cls = serviceMetas.findResClass(serviceId, msgId);
        if (cls != null) {
            b = ReflectionUtils.generateBuilder(cls);
        } else {
            Descriptor desc = serviceMetas.findDynamicResDescriptor(serviceId, msgId);
            if (desc == null) return null;
            b = ReflectionUtils.generateDynamicBuilder(desc);
        }

        return MapToMessage.toMessage(b, results);
    }

    boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    InputStream getResource(String file) {
        return getClass().getClassLoader().getResourceAsStream(file);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void loadYaml() {

        InputStream in = null;

        try {
            Yaml yaml = new Yaml();
            in = getResource(file);
            if (in == null) {
                return;
            }
            Object result = yaml.load(in);
            if (!(result instanceof List)) {
                // throw new RuntimeException("invalid fallback file, not a list");
                log.error("invalid fallback file, not a list");
                return;
            }

            List l = (List) result;
            for (Object o : l) {
                if (!(o instanceof Map)) {
                    log.error("invalid fallback file, not a map, line="+o);
                    continue;
                }
                Map m = (Map) o;
                Object s = m.get("for");
                if (s == null) {
                    log.error("invalid fallback file, for not found, line="+o);
                    continue;
                }
                String forName = s.toString();
                s = m.get("match");
                String match = s != null ? s.toString() : "";

                Map results = null;

                Object r = m.get("results");
                if (r != null) {
                    if (!(r instanceof Map)) {
                        log.error("invalid fallback file, not a map, line="+o);
                        continue;
                    }
                    results = (Map) r;
                }
                Item i = new Item();
                i.forName = forName;
                i.match = match;
                i.results = results;
                items.add(i);
            }
        } catch (Exception e) {
            log.error("fallback file load  exception", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
        }

    }

    public ServiceMetas getServiceMetas() {
        return serviceMetas;
    }

    public void setServiceMetas(ServiceMetas serviceMetas) {
        this.serviceMetas = serviceMetas;
    }

}
