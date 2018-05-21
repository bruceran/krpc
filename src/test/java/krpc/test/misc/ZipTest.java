package krpc.test.misc;

import org.junit.Assert;
import org.junit.Test;
import krpc.util.SnappyTool;
import krpc.util.ZlibTool;

public class ZipTest {

	@Test
	public void test1() throws Exception {
 
		String s = "abcdefg2020202019199191";
		byte[] bs = s.getBytes();
		
		ZlibTool zt = new ZlibTool();
		byte[] bs2 = zt.zip(bs);
		byte[] bs3 = zt.unzip(bs2);
		
		String s2 = new String(bs3);
		
		Assert.assertEquals(s,s2);
		System.out.println(s2);
  
	}

	@Test
	public void test2() throws Exception {
 
		String s = "abcdefg2020202019199191";
		byte[] bs = s.getBytes();
		
		SnappyTool zt = new SnappyTool();
		byte[] bs2 = zt.zip(bs);
		byte[] bs3 = zt.unzip(bs2);
		
		String s2 = new String(bs3);
		
		Assert.assertEquals(s,s2);
		System.out.println(s2);
  
	}
	
}

