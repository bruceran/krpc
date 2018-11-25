package krpc.persistqueue;

import java.io.IOException;
import java.util.List;

public interface PersistQueueManager {

    void init() throws IOException;

    void close();

    void close(String name);

    List<String> getQueueNames();

    PersistQueue getQueue(String name) throws IOException;

}
