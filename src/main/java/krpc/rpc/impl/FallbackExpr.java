package krpc.rpc.impl;

public interface FallbackExpr {
	
	public interface DataProvider {
		public String get(String key);
	}
	
	public boolean eval(DataProvider data);
}
