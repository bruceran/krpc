package krpc.rpc.core;

import java.util.concurrent.ThreadPoolExecutor;

public interface ExecutorManager {
    ThreadPoolExecutor getExecutor(int serviceId, int msgId);

    void addDefaultPool(int threads, int maxThreads, int queueSize);

    void addPool(int serviceId, int threads, int maxThreads, int queueSize);

    void addPool(int serviceId, int[] msgIds, int threads, int maxThreads, int queueSize);
}

