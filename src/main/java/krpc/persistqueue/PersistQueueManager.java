/*
 * Copyright (c) 2011 Shanda Corporation. All rights reserved.
 *
 * Created on 2011-12-19.
 */
package krpc.persistqueue;

import java.io.IOException;
import java.util.List;

/**
 * queue manager
 *
 */
public interface PersistQueueManager {

    void init() throws IOException;

    void close();

    void close(String name);

    List<String> getQueueNames();

    PersistQueue getQueue(String name) throws IOException;

}
