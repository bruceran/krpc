package krpc.test.misc;

import java.util.Map;

import org.apache.velocity.anakia.Escape;
import org.junit.Assert;
import org.junit.Test;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import krpc.common.Plugin;
import krpc.rpc.util.TypeSafe;
import krpc.trace.adapter.CatTraceAdapter;

public class SandboxTest {

	@Test
	public void test1() throws Exception {
 
		String s = CatTraceAdapter.escape("123");
		Assert.assertEquals("123", s);
		s = CatTraceAdapter.escape("123\t456");
		Assert.assertEquals("123\\t456", s);
		s = CatTraceAdapter.escape("123\\456");
		Assert.assertEquals("123\\\\456", s);
		s = CatTraceAdapter.escape("123\n456");
		Assert.assertEquals("123\\n456", s);
	}
	
}

