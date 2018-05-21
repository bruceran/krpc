package krpc.web;

import java.util.Map;

import krpc.core.Continue;
import krpc.core.Plugin;

public interface SessionService extends Plugin {
	void load(String sessionId,Map<String,String> values,Continue<Integer> cont);
	void update(String sessionId, Map<String,String> values,Continue<Integer> cont);
	void remove(String sessionId,Continue<Integer> cont);
}
