package krpc.rpc.core;

import java.util.Map;

public interface DumpPlugin {

    void dump(Map<String,Object> metrics);

}
