package krpc.test.misc;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import krpc.rpc.core.Plugin;
import krpc.rpc.util.TypeSafe;

public class SandboxTest {

	@Test
	public void test1() throws Exception {
 
		HttpHeaders h = new DefaultHttpHeaders();
		h.set("a","123");
		Assert.assertEquals("123",  h.get("A") );
		h.set("Content-type","application/json");
		Assert.assertEquals("application/json",  h.get("content-type") );
	}
	
}

