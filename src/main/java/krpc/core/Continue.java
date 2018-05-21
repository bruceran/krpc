package krpc.core;

public interface Continue<T> {
	void readyToContinue(T value);
}
