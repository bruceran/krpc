package krpc.test.misc;
 

import org.junit.Assert;
import org.junit.Test;
 
import krpc.rpc.cluster.Weights;

public class WeightsTest {

	@Test
	public void test1() throws Exception {
		
		Weights w = new Weights();
		w.addWeight("192.168.1.3", 15);
		w.addWeight("192.168.1.5:1800", 30);
		w.addWeight("192.168.1.5", 20);
		
		int v = w.getWeight("1.1.1.1:80");
		Assert.assertEquals(100,v);
		v = w.getWeight("192.168.1.3:80");
		Assert.assertEquals(15,v);
		v = w.getWeight("192.168.1.5:1800");
		Assert.assertEquals(30,v);
		v = w.getWeight("192.168.1.5:1900");
		Assert.assertEquals(20,v);
	}
	
	@Test
	public void test2() throws Exception {
		
		Weights w = new Weights();
		w.addWeight("192.168.1.3", 15);
		w.addWeight("192.168.1.*:1800", 30);
		w.addWeight("192.168.1.*", 20);
		
		int v = w.getWeight("1.1.1.1:80");
		Assert.assertEquals(100,v);
		v = w.getWeight("192.168.1.3:80");
		Assert.assertEquals(15,v);
		v = w.getWeight("192.168.1.5:1800");
		Assert.assertEquals(30,v);
		v = w.getWeight("192.168.1.5:1900");
		Assert.assertEquals(20,v);
		v = w.getWeight("192.168.1.6:80");
		Assert.assertEquals(20,v);
	}
	
	
}

