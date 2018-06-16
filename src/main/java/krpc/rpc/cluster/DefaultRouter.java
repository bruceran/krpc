package krpc.rpc.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import krpc.rpc.core.ClientContextData;
import krpc.rpc.core.DynamicRouteConfig.RouteRule;
import krpc.rpc.util.IpUtils;

public class DefaultRouter implements Router {
	
	static Logger log = LoggerFactory.getLogger(DefaultRouter.class);

	FilterExprParser conditionParser;
	FilterExprParser targetParser;

	String host;
	Map<String,String> matchData = new HashMap<>();
	
	AtomicReference<List<Rule>> rulesList = new AtomicReference<>();
	
	public DefaultRouter(int serviceId,String application) {
		
		conditionParser = new FilterExprParser("host,application,msgId");
		targetParser = new FilterExprParser("host");
		
		host = IpUtils.localIp();
		matchData.put("application", application);
		matchData.put("host", host);
	}
	
	public void config(List<RouteRule> params) {
		Collections.sort(params);
		
		List<Rule> rules = new ArrayList<>(params.size());
		for(RouteRule rr:params) {
			Rule r = toRule(rr);
			if( r != null )
				rules.add(r);
		}
		
		rulesList.set(rules);
	}
	
	public List<Addr> select(List<Addr> addrs,ClientContextData ctx,Message req) {
		int msgId = ctx.getMeta().getMsgId();
		return select(addrs,msgId);
	}
	
	List<Addr> select(List<Addr> addrs,int msgId) {
		
		List<Rule> rules = rulesList.get();
		
		if( rules == null )
			return addrs;
		
		boolean[] matchFlag = new boolean[addrs.size()];
		for(Rule r:rules) {
			if( r.match(addrs,matchFlag,msgId) ) {
				break;
			}
		}

		List<Addr> newList = new ArrayList<>();
		for(int i=0;i<addrs.size();++i) {
			if( matchFlag[i] ) {
				newList.add(addrs.get(i));
			}
		}
		
		return newList;
	}

	// todo 语法写错了如何处理?
	
	Rule toRule(RouteRule rr) {
		String from = rr.getFrom();
		Condition condition = new Condition(from);
		if( !condition.isUseful() ) {
			return null;
		}
		
		return new Rule(condition,rr.getTo());
	}
		
	class Rule {
		
		Condition condition;
		FilterExprSimple target; 
		
		Rule(Condition condition,String to) {
			this.condition = condition;
			
			if( to == null ) return;
			to = to.trim();
			if( to.isEmpty() ) return;
			to = to.replaceAll("$host",host); // todo regex
			
			target = targetParser.parseSimple(to);
			if( target == null ) {
				log.error("cannot parse target, rule="+to);
			}
		}
		
		boolean match(List<Addr> addrs,boolean[] matchFlag,int msgId) {
			if( !condition.match(msgId) ) return false;
			if( target == null ) return false; // invalid target expr or empty expr
			
			for(int i=0;i<addrs.size();++i ) {
				String addr = addrs.get(i).getAddr();
				if( matchTarget(addr) ) {
					matchFlag[i] = true;
				}
			}
			return true;
		}
		
		boolean matchTarget(String addr) {
			if( target.operator.equals("=") ) {
				for(Pattern p:target.patterns) {
					if( p.matcher(addr).matches()) {
						return true;
					}
				}
				return false;				
			}  else {
				for(Pattern p:target.patterns) {
					if( p.matcher(addr).matches()) {
						return false;
					}
				}
				return true;					
			}
		}
	}
	
	class Condition {
		
		FilterExpr expr = null;
		
		Condition(String from) {
			if( from == null ) return;
			from = from.trim();
			if( from.isEmpty() ) return;
			expr = conditionParser.parse(from);
			if( expr == null ) {
				log.error("cannot parse rule, rule="+from);
			}
		}
		
		boolean isUseful() {
			if( expr == null ) return true;
			Set<String> keys = new HashSet<>();
			expr.getKeys(keys);
			boolean hasMsgId = keys.contains("msgId");
			if( hasMsgId ) return true;
			return expr.eval(matchData);
		}

		boolean match(int msgId) {
			if( expr == null ) return true;
			matchData.put("msgId", String.valueOf(msgId)); // already called in synchronized block
			return expr.eval(matchData);
		}
	}
	
}
