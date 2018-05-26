package krpc.redis.data;

public class SetReq extends BaseKeyReq<SetReq> {

	String value;
	int ex;
	int px;
	boolean nx;
	boolean xx;
	
	public SetReq(String key,String value) {
		if( isEmpty(key) || isEmpty(value)) throw new IllegalArgumentException();
		this.key = key;
		this.value = value;
	}

	public String getCmd() {
		return "set";
	}		
	
	
	public Class<?> getResCls() {
		return SetRes.class;
	}
	
	public RedisCommand toCommand() {

		RedisCommand c = super.toCommand();
		c.add(value);
		
		if( ex > 0 ) {
			c.add("EX");
			c.add(ex);
		}
		if( px > 0 ) {
			c.add("PX");
			c.add(px);
		}
		if( nx ) {
			c.add("NX");
		}
		if( xx ) {
			c.add("XX");
		}
		
		return c;
	}
	
	public String getValue() {
		return value;
	}

	public int getEx() {
		return ex;
	}

	public SetReq setEx(int ex) {
		if( ex < 0 ) throw new IllegalArgumentException();
		this.ex = ex;
		this.px = 0;
		return this;
	}

	public int getPx() {
		return px;
	}

	public SetReq setPx(int px) {
		if( ex < 0 ) throw new IllegalArgumentException();
		this.px = px;
		this.ex = 0;
		return this;
	}

	public boolean isNx() {
		return nx;
	}

	public SetReq setNx(boolean nx) {
		this.nx = nx;
		this.xx = false;
		return this;
	}

	public boolean isXx() {
		return xx;
	}

	public SetReq setXx(boolean xx) {
		this.xx = xx;
		this.nx = false;
		return this;
	}
	
}
