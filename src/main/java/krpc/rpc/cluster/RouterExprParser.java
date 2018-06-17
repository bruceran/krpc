package krpc.rpc.cluster;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouterExprParser {

	static final Pattern reg1 = Pattern.compile("^([a-zA-Z]+)( *!= *) *(.+)$");
	static final Pattern reg2 = Pattern.compile("^([a-zA-Z]+)( *= *) *(.+)$");

	Set<String> allowedKeySet = new HashSet<>();
	
	public RouterExprParser() {
	}
	
	public RouterExprParser(String allowedKeys) {
		String[] ss = allowedKeys.split(",");
		for(String s:ss) allowedKeySet.add(s);
	}
	
	public RouterExpr parse(String s) {
		String ns = s.replace("\n", " ").replace("\r", " ").replace("\t", " ");
		char[] chs = ns.toCharArray();

		try {
			return parse(chs, 0, chs.length);
		} catch (Exception e) {
			return null;
		}
	}

	public RouterExprSimple parseSimple(String s) {
		String ns = s.replace("\n", " ").replace("\r", " ").replace("\t", " ");

		try {
			return parseSimpleInner(ns);
		} catch (Exception e) {
			return null;
		}
	}
	
	private RouterExpr parse(char[] chs, int s, int e) {
		int i = s;
		RouterExprBuilder builder = new RouterExprBuilder();
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
				RouterExpr expr = parse(chs, i + 1, p - 1);
				builder.add(expr);
				i = p;
				break;
			}
			default: {
				int p = findExprEnd(chs, i, e);
				if (p < 0)
					throw new RuntimeException("expr not end");
				RouterExpr expr = parseSimpleInner(new String(chs, i, p - i + 1));
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
						if (i < e && chs[i + 1] != '=')
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

	private RouterExprSimple parseSimpleInner(String s)  {
		s = s.trim();
		if( s == null || s.isEmpty() ) return null;
		
		Matcher m =  reg1.matcher(s);
		if( m.matches() ) {
			String key = m.group(1).trim();
			String operator = m.group(2).trim();
			String values = removeQuota(m.group(3).trim());
			if( !checkKey(key) || !checkOperator(operator) ) 
				throw new RuntimeException("expr not valid: expr="+s);
			return new RouterExprSimple(key,operator,values);
		}
		
		m =  reg2.matcher(s);
		if( m.matches() ) {
			String key = m.group(1).trim();
			String operator = m.group(2).trim();
			String values = removeQuota(m.group(3).trim());
			if( !checkKey(key) || !checkOperator(operator) ) 
				throw new RuntimeException("expr not valid: expr="+s);
			return new RouterExprSimple(key,operator,values);
		}
 
		return null;
	}

	private boolean checkKey(String key) {
		if( allowedKeySet.size() == 0 ) return true;
		return allowedKeySet.contains(key);
	}
	
	private boolean checkOperator(String operator) {
		if( operator.equals("=") || operator.equals("!=")  ) return true;
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


class RouterExprBuilder {

	RouterExprOr or;
	RouterExprAnd and;
	boolean not;
	boolean orand;
	RouterExpr last;

	void addOr() {
		if (not)
			throw new RuntimeException("'!' operator before '||'");
		if (last == null)
			throw new RuntimeException("'||' operator not valid");
		if (or == null)
			or = new RouterExprOr();
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
			and = new RouterExprAnd();
		and.list.add(last);
		last = null;
		orand = true;
	}

	void addNot() {
		if (not)
			throw new RuntimeException("'!' operator before '!'");
		not = true;
	}

	void add(RouterExpr e) {
		if (last != null)
			throw new RuntimeException("expr duplicated");
		if (not)
			last = new RouterExprNot(e);
		else
			last = e;
		not = false;
		orand = false;
	}

	RouterExpr get() {
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

