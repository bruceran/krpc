package krpc.rpc.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackExprParser {

	static Logger log = LoggerFactory.getLogger(FallbackExprParser.class);
	
	static final Pattern reg1 = Pattern.compile("^([a-zA-Z0-9_.]+)( +in +| +not_in +) *(.+)$");
	static final Pattern reg2 = Pattern.compile("^([a-zA-Z0-9_.]+)( *== *| *!= *) *(.+)$");
	static final Pattern reg3 = Pattern.compile("^([a-zA-Z0-9_.]+)( *=~ *| *!~ *) *(.+)$");

	public FallbackExpr parse(String s) {
		String ns = s.replace("\n", " ").replace("\r", " ").replace("\t", " ");
		char[] chs = ns.toCharArray();

		try {
			return parse(chs, 0, chs.length);
		} catch (Exception e) {
			log.error("invalid expr: s="+s);
			return null;
		}
	}
	
	private FallbackExpr parse(char[] chs, int s, int e) {
		int i = s;
		FallbackExprBuilder builder = new FallbackExprBuilder();
		while (i < e) {
			char ch = chs[i];
			switch (ch) {
			case '|':
				if (i + 1 >= e)
					throw new RuntimeException("'||' not valid");
				i += 1;
				if (ch != '|')
					throw new RuntimeException("'||' not valid");
				builder.addOr();
				break;
			case '&':
				if (i + 1 >= e)
					throw new RuntimeException("'&&' not valid");
				i += 1;
				if (ch != '&')
					throw new RuntimeException("'&&' not valid");
				builder.addAnd();
				break;
			case '!':
				builder.addNot();
				break;
			case '(': {
				int p = findMatchBracket(chs, i, e);
				if (p < 0)
					throw new RuntimeException("() not match ");
				FallbackExpr expr = parse(chs, i + 1, p - 1);
				builder.add(expr);
				i = p;
				break;
			}
			default: {
				int p = findExprEnd(chs, i, e);
				if (p < 0)
					throw new RuntimeException("expr not end");
				FallbackExpr expr = parseSimpleInner(new String(chs, i, p - i + 1));
				if (expr != null)
					builder.add(expr);
				i = p;
				break;
			}
			}
			i = i + 1;
		}
		return builder.get();
	}

	private int findMatchBracket(char[] chs, int s, int e) {
		int i = s;
		int lvl = 0;
		boolean inQuota = false;
		while (i < e) {
			char ch = chs[i];
			switch (ch) {
			case '\"':
				inQuota = !inQuota;
				break;
			case '(':
				if (!inQuota)
					lvl += 1;
				break;
			case ')':
				if (!inQuota) {
					lvl -= 1;
					if (lvl == 0)
						return i;
				}
				break;
			default:
				break;
			}
			i += 1;
		}
		return -1;
	}

	private int findExprEnd(char[] chs, int s, int e) {
		int i = s;
		boolean inQuota = false;
		while (i < e) {
			char ch = chs[i];
			switch (ch) {
			case '\"':
				inQuota = !inQuota;
				break;
			case '(':
			case ')':
			case '|':
			case '&':
			case '!':
				if (!inQuota) {
					if (ch == '!') {
						if (i < e && chs[i + 1] != '=' && chs[i + 1] != '~' )
							return i - 1;
					} else {
						return i - 1;
					}
				}
			default:
				break;
			}
			i += 1;
		}
		return e - 1;
	}

	private FallbackExpr parseSimpleInner(String s)  {
		s = s.trim();
		if( s == null || s.isEmpty() ) return null;
		
		Matcher m =  reg1.matcher(s);
		if( m.matches() ) {
			String key = m.group(1).trim();
			String operator = m.group(2).trim();
			String values = removeQuota(m.group(3).trim());
			if( !checkOperator(operator) ) 
				throw new RuntimeException("expr not valid: expr="+s);
			return new FallbackExprIn(key,operator,values);
		}
		
		m =  reg2.matcher(s);
		if( m.matches() ) {
			String key = m.group(1).trim();
			String operator = m.group(2).trim();
			String values = removeQuota(m.group(3).trim());
			if( !checkOperator(operator) ) 
				throw new RuntimeException("expr not valid: expr="+s);
			return new FallbackExprEqual(key,operator,values);
		}
 
		m =  reg3.matcher(s);
		if( m.matches() ) {
			String key = m.group(1).trim();
			String operator = m.group(2).trim();
			String values = removeQuota(m.group(3).trim());
			if( !checkOperator(operator) ) 
				throw new RuntimeException("expr not valid: expr="+s);
			return new FallbackExprPattern(key,operator,values);
		}
		
		return null;
	}

	private boolean checkOperator(String operator) {
		if( operator.equals("in") || operator.equals("not_in")  ) return true;
		if( operator.equals("==") || operator.equals("!=")  ) return true;
		if( operator.equals("=~") || operator.equals("!~")  ) return true;
		return false;
	}
	
	private String removeQuota(String s) {
		if (s.startsWith("\"")) {
			if (s.endsWith("\"")) {
				return s.substring(1, s.length() - 1);
			}
			throw new RuntimeException("quota string not valid, expr=" + s);
		}
		return s;
	}
}


class FallbackExprBuilder {

	FallbackExprOr or;
	FallbackExprAnd and;
	boolean not;
	boolean orand;
	FallbackExpr last;

	void addOr() {
		if (not)
			throw new RuntimeException("'!' operator before '||'");
		if (last == null)
			throw new RuntimeException("'||' operator not valid");
		if (or == null)
			or = new FallbackExprOr();
		if (last != null) {
			if (and != null) {
				and.list.add(last);
				last = null;
				or.list.add(and);
				and = null;
			} else {
				or.list.add(last);
				last = null;
			}
		}
		if (and != null) {
			or.list.add(and);
			and = null;
		}
		orand = true;
	}

	void addAnd() {
		if (not)
			throw new RuntimeException("'!' operator before '&&'");
		if (last == null)
			throw new RuntimeException("'&&' operator not valid");
		if (and == null)
			and = new FallbackExprAnd();
		and.list.add(last);
		last = null;
		orand = true;
	}

	void addNot() {
		if (not)
			throw new RuntimeException("'!' operator before '!'");
		not = true;
	}

	void add(FallbackExpr e) {
		if (last != null)
			throw new RuntimeException("expr duplicated");
		if (not)
			last = new FallbackExprNot(e);
		else
			last = e;
		not = false;
		orand = false;
	}

	FallbackExpr get() {
		if (not)
			throw new RuntimeException("'!' operator is last");
		if (orand)
			throw new RuntimeException(" && || operator is last");

		if (last != null) {
			if (and != null) {
				and.list.add(last);
				last = null;
			} else if (or != null) {
				or.list.add(last);
				last = null;
			} else {
				return last;
			}
		}
		if (and != null) {
			if (or != null) {
				or.list.add(and);
				return or;
			} else {
				return and;
			}
		}
		if (or != null) {
			return or;
		}

		throw new RuntimeException("not valid expr");
	}
}


class FallbackExprAnd implements FallbackExpr {
	List<FallbackExpr> list = new ArrayList<>();

	public boolean eval(DataProvider data) {
		for (FallbackExpr expr : list) {
			if (!expr.eval(data))
				return false;
		}
		return true;
	}

}

class FallbackExprOr implements FallbackExpr {
	List<FallbackExpr> list = new ArrayList<>();

	public boolean eval(DataProvider data) {
		for (FallbackExpr expr : list) {
			if (expr.eval(data))
				return true;
		}
		return false;
	}

}

class FallbackExprNot implements FallbackExpr {
	FallbackExpr expr;

	FallbackExprNot(FallbackExpr expr) {
		this.expr = expr;
	}

	public boolean eval(DataProvider data) {
		return !expr.eval(data);
	}

}

class FallbackExprEqual implements FallbackExpr {

	String key;
	String operator;
	String value;

	FallbackExprEqual(String key, String operator, String value) {
		this.key = key;
		this.operator = operator;
		this.value = value;
		if( this.value == null ) this.value = "";
	}

	public boolean eval(DataProvider data) {
		String v = data.get(key);
		if (v == null) // invalid key
			return false;
		
		boolean ok = v.equals(value);
		if( operator.equals("==") ) return ok;
		else return !ok;
	}
}


class FallbackExprPattern implements FallbackExpr {

	String key;
	String operator;
	Pattern pattern;

	FallbackExprPattern(String key, String operator, String value) {
		this.key = key;
		this.operator = operator;
		pattern = Pattern.compile(value);
	}

	public boolean eval(DataProvider data) {
		String v = data.get(key);
		if (v == null) // invalid key
			return false;
		
		boolean ok = pattern.matcher(v).matches();
		if( operator.equals("=~") ) return ok;
		else return !ok;		
	}
	
}

class FallbackExprIn implements FallbackExpr {

	String key;
	String operator;
	Set<String> strs = new HashSet<>();

	FallbackExprIn(String key, String operator, String values) {
		this.key = key;
		this.operator = operator;
		String[] toss = values.split(",");
		for (String s : toss) {
			s = s.trim();
			if (s.isEmpty())
				continue;
			strs.add(s);
		}
	}

	public boolean eval(DataProvider data) {
		String v = data.get(key);
		if ( v == null)
			return false;

		boolean ok = strs.contains(v);
		if( operator.equals("in") ) return ok;
		else return !ok;		
	}

}
