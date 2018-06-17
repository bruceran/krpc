package krpc.rpc.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public interface RouterExpr {
	public boolean eval(Map<String, String> data);
	public void getKeys(Set<String> keys);
}

class RouterExprAnd implements RouterExpr {
	List<RouterExpr> list = new ArrayList<>();

	public boolean eval(Map<String, String> data) {
		for (RouterExpr expr : list) {
			if (!expr.eval(data))
				return false;
		}
		return true;
	}
	
	public void getKeys(Set<String> keys) {
		for (RouterExpr expr : list) {
			expr.getKeys(keys);
		}
	}
}

class RouterExprOr implements RouterExpr {
	List<RouterExpr> list = new ArrayList<>();

	public boolean eval(Map<String, String> data) {
		for (RouterExpr expr : list) {
			if (expr.eval(data))
				return true;
		}
		return false;
	}
	
	public void getKeys(Set<String> keys) {
		for (RouterExpr expr : list) {
			expr.getKeys(keys);
		}
	}	
}

class RouterExprNot implements RouterExpr {
	RouterExpr expr;

	RouterExprNot(RouterExpr expr) {
		this.expr = expr;
	}

	public boolean eval(Map<String, String> data) {
		return !expr.eval(data);
	}
	
	public void getKeys(Set<String> keys) {
			expr.getKeys(keys);
	}	
}

class RouterExprSimple implements RouterExpr {

	String key;
	String operator;
	Set<String> strs = new HashSet<>();
	List<Pattern> patterns = new ArrayList<>();

	public void getKeys(Set<String> keys) {
		keys.add(key);
	}	
	
	RouterExprSimple(String key, String operator, String values) {
		this.key = key;
		this.operator = operator;
		String[] toss = values.split(",");
		for (String s : toss) {
			s = s.trim();
			if (s.isEmpty())
				continue;

			if( s.indexOf("*") < 0 ) {
				strs.add(s);
			} else {
				s = s.replaceAll("\\*", "[0-9]*");
				s = s.replaceAll("\\.", "[.]");
				Pattern p = Pattern.compile(s);
				patterns.add(p);
			}				
 
		}
	}

	public boolean eval(Map<String, String> data) {
		String value = data.get(key);
		if (value == null || value.isEmpty())
			return false;

		if (operator.equals("=") ) {
			
			if( strs.contains(value)) return true;
			
			for (Pattern p : patterns) {
				if (p.matcher(value).matches()) {
					return true;
				}
			}
			return false;
		}
		
		if (operator.equals("!=") ) {
			
			if( strs.contains(value)) return false;
			
			for (Pattern p : patterns) {
				if (p.matcher(value).matches()) {
					return false;
				}
			}
			return true;
		}
		
		return false;
	}

}
