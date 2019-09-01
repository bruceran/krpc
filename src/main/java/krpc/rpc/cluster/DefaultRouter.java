package krpc.rpc.cluster;

import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;
import krpc.rpc.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultRouter implements Router {

    static Logger log = LoggerFactory.getLogger(DefaultRouter.class);

    RouterExprParser conditionParser;
    RouterExprParser targetParser;

    String host;
    Map<String, String> matchData = new HashMap<>();

    AtomicReference<List<Rule>> rulesList = new AtomicReference<>();

    public DefaultRouter(int serviceId, String application) {

//        conditionParser = new RouterExprParser("host,application,msgId,dyeing"); // 允许所有
        conditionParser = new RouterExprParser(); // 允许所有key
        targetParser = new RouterExprParser("host,addr"); // 只允许 host addr

        host = IpUtils.localIp();
        matchData.put("application", application);
        matchData.put("host", host);
    }

    public void config(List<RouteRule> params) {
        if (params == null) {
            rulesList.set(null);
            return;
        }

        boolean allZero = true;
        for (RouteRule rr : params) {
            if( rr.getPriority() != 0 ) {
                allZero = false;
            }
        }

        if( !allZero ) {
            Collections.sort(params);
        }

        List<Rule> rules = new ArrayList<>(params.size());
        for (RouteRule rr : params) {
            Rule r = toRule(rr);
            if (r != null)
                rules.add(r);
        }

        rulesList.set(rules);
    }

    public boolean needReqInfo(int serviceId,int msgId) {
        return false; // todo
    }

    public List<Addr> select(List<Addr> addrs, ClientContextData ctx, Map<String,Object> req) {
        int msgId = ctx.getMeta().getMsgId();

        Map<String,String> dynamicMap = new HashMap<>();

//        if( req != null ) { // todo
//                    dynamicMap.putAll(req);
//        }

        Map<String,String> tags = ctx.getTraceContext().getTagsMapForRpc();
        if( tags != null ) {
            dynamicMap.putAll(tags);
        }

        String dyeing = ctx.getTraceContext().getTrace().getDyeing();
        if( dyeing != null && !dyeing.isEmpty() ) {
            dynamicMap.put("dyeing", dyeing);
        }

        dynamicMap.put("msgId",String.valueOf(msgId));

        return select(addrs, dynamicMap);
    }

    List<Addr> select(List<Addr> addrs, Map<String,String> req) {

        List<Rule> rules = rulesList.get();

        if (rules == null)
            return addrs;

        boolean[] matchFlag = new boolean[addrs.size()];
        for (Rule r : rules) {
            if (r.match(addrs, matchFlag, req)) {
                break;  // 返回true, 表明规则匹配，已做好matchFlag标志； 无需再做下一个规则匹配
            }
        }

        List<Addr> newList = new ArrayList<>();
        for (int i = 0; i < addrs.size(); ++i) {
            if (matchFlag[i]) {
                newList.add(addrs.get(i));
            }
        }

        return newList;
    }

    Rule toRule(RouteRule rr) {
        String from = rr.getFrom();
        Condition condition = new Condition(conditionParser, matchData, from);
        if (!condition.hasDynamicConditions()) {
            return null;
        }

        return new Rule(targetParser, host, condition, rr.getTo());
    }

    static class Rule {

        Condition condition;
        RouterExpr target;

        Rule(RouterExprParser targetParser, String host, Condition condition, String to) {
            this.condition = condition;

            if (to == null) return;
            to = to.trim();
            if (to.isEmpty()) return;
            to = to.replaceAll("\\$host", host);

            target = targetParser.parseSimple(to);
            if (target == null) {
                log.error("cannot parse target, rule=" + to);
            }
        }

        boolean match(List<Addr> addrs, boolean[] matchFlag, Map<String,String> values) {
            if (!condition.match(values)) return false;
            if (target == null) return false; // invalid target expr or empty expr

            for (int i = 0; i < addrs.size(); ++i) {
                String addr = addrs.get(i).getAddr();
                if (matchTarget(addr)) {
                    matchFlag[i] = true;
                }
            }
            return true;
        }

        boolean matchTarget(String addr) {
            Map<String, String> data = new HashMap<>();
            data.put("addr", addr);
            data.put("host", getIp(addr));
            return target.eval(data);
        }

        String getIp(String addr) {
            int p = addr.lastIndexOf(":");
            return addr.substring(0, p);
        }
    }

    static class Condition {

        RouterExpr expr = null;
        Map<String, String> matchData;

        Condition(RouterExprParser conditionParser, Map<String, String> matchData, String from) {
            this.matchData = matchData;
            if (from == null) return;
            from = from.trim();
            if (from.isEmpty()) return;
            expr = conditionParser.parse(from);
            if (expr == null) {
                log.error("cannot parse rule, rule=" + from);
            }
        }

        boolean hasDynamicConditions() {
            if (expr == null) return true;
            Set<String> keys = new HashSet<>();
            expr.getKeys(keys);

            for(String key: keys) {
                if( !key.equals("host") && !key.equals("application") ) {
                    return true;
                }
            }

            return expr.eval(matchData);
        }

        boolean match(Map<String,String> values) {
            if (expr == null) return true;
            Map<String, String> newMatchData = new HashMap<>();
            newMatchData.putAll(matchData);
            newMatchData.putAll(values);
            return expr.eval(newMatchData);
        }
    }

}
