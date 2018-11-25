package krpc.persistqueue;

import java.io.IOException;

public interface PersistQueue {

    void init() throws IOException;

    void close();

    void purge() throws IOException;

    void put(String s) throws IOException;

    void put(byte[] bs) throws IOException;

    /**
     * get the data index
     * wait until interrupted
     */
    long get() throws IOException, InterruptedException;

    /**
     * get the data index
     * <p/>
     * wait=-1 wait until interrupted
     * wait=0 no wait
     * wait>0 wait x milliseconds
     * <p/>
     * return -1,  timeout, no data found
     */
    long get(long wait) throws IOException, InterruptedException;

    /**
     * get the real content
     */
    String getString(long idx) throws IOException;

    /**
     * get the real content
     */
    byte[] getBytes(long idx) throws IOException;

    /**
     * commit processed data
     */
    void commit(long idx);

    /**
     * rollback data
     */
    void rollback(long idx);
    
    /**
     * check queue is empty
     * 
     */
    public boolean empty();

    /**
     * put and return data index
     */
    long putAndReturnIdx(String s) throws IOException;

    /**
     * put and return data index
     */
    long putAndReturnIdx(byte[] bs) throws IOException;
    
    /**
     * get queue size
     * 
     */
    public int size();

    /**
     * get cache size
     * 
     */
    public int cacheSize();
}
