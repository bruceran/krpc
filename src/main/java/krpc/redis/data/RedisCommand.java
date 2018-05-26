package krpc.redis.data;

import java.util.ArrayList;
import java.util.List;

public class RedisCommand {
	
	List<String> cmds = new ArrayList<>(5);

	public List<String> getCmds() {
		return cmds;
	}
	public void add(String cmd) {
		cmds.add(cmd);
	}
	public void add(int v) {
		cmds.add(String.valueOf(v));
	}
	public void add(long v) {
		cmds.add(String.valueOf(v));
	}
	public void add(float v) {
		cmds.add(String.valueOf(v));
	}
	public void add(double v) {
		cmds.add(String.valueOf(v));
	}	
}
