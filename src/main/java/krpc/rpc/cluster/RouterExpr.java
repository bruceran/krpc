package krpc.rpc.cluster;

import java.util.Map;
import java.util.Set;

public interface RouterExpr {
	public boolean eval(Map<String, String> data);
	public void getKeys(Set<String> keys);
}
