package krpc.rpc.core;

import java.util.List;

public interface HealthPlugin  {

    void healthCheck(List<HealthStatus> list);

}
