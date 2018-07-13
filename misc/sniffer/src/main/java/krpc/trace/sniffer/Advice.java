package krpc.trace.sniffer;

public interface Advice {

    public void start(String type, String action);

    public long stop(boolean ok);

    public void logException(Throwable c);

}
