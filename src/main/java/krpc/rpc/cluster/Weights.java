package krpc.rpc.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Weights {
	
	Map<String,Integer> weights = new HashMap<>();
	
	static class PatternWeight {
		Pattern p;
		int weight;
		PatternWeight(Pattern p,int weight) {
			this.p = p;
			this.weight = weight;
		}
	}
	
	List<PatternWeight> patterns = new ArrayList<>();
	
    public void addWeight(String addr,int weight) {
    	if( addr.indexOf("*") < 0 ) {
    		weights.put(addr, weight);
    	} else {
    		String s = addr;
			s = s.replaceAll("\\*", "[0-9]*");
			s = s.replaceAll("\\.", "[.]");
			Pattern p = Pattern.compile(s);
			patterns.add(new PatternWeight(p,weight));    		
    	}
    }

	public int getWeight(String addr) {
		Integer i = weights.get(addr);
		if( i != null )  return i;
			
		for(PatternWeight pw:patterns) {
			if( pw.p.matcher(addr).matches() ) return pw.weight;
		}
		
		String ip = getIp(addr);
		if( ip == null ) return 100;
		
		i = weights.get(ip);
		if( i != null )  return i;
			
		for(PatternWeight pw:patterns) {
			if( pw.p.matcher(ip).matches() ) return pw.weight;
		}
		
		return 100;
	}    

	String getIp(String addr) {
		int p = addr.lastIndexOf(":");
		if( p < 0 ) return null;
		return addr.substring(0,p);
	}
	
}
