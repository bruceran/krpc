package krpc.rpc.cluster;

import java.util.Map;
import java.util.Set;

public interface RouterExpr {
    boolean eval(Map<String, String> data);

    void getKeys(Set<String> keys);
}
