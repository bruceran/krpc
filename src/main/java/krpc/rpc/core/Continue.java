package krpc.rpc.core;

public interface Continue<T> {
    void readyToContinue(T value);
}
