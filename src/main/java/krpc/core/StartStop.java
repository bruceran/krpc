package krpc.core;

public interface StartStop {
	void start(); // do something after init, for example:  open port to receive request
	void stop(); // do something before close, for example: stop listen, stop read data, ...
}
