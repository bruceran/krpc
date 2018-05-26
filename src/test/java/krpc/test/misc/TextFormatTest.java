package krpc.test.misc;

import org.junit.Test;

import com.google.protobuf.Message;
import com.xxx.userservice.proto.LoginReq;
import com.xxx.userservice.proto.LoginRes;
import com.xxx.userservice.proto.Order;
import com.xxx.userservice.proto.OrderItem;
import com.xxx.userservice.proto.OrderItemAttr;

import krpc.rpc.monitor.JacksonLogFormatter;
import krpc.rpc.monitor.SimpleLogFormatter;

public class TextFormatTest {

	@Test
	public void test1() throws Exception {
 
		SimpleLogFormatter f = new SimpleLogFormatter();
		f.config("maxRepeatedSizeToLog=2");
		JacksonLogFormatter f2 = new JacksonLogFormatter();
		f2.config("maxRepeatedSizeToLog=2");

		Message m1 = LoginReq.newBuilder().setUserName("mike").setPassword("how ^ are you :").build();
		Message m2 = LoginRes.newBuilder().setRetCode(0).setRetMsg("hello, friend mike. receive req#").build();
		System.out.println("s="+f.toLogStr(false,m1));
		System.out.println("s="+f2.toLogStr(false,m1));

		System.out.println("s="+f.toLogStr(false,m2));
		System.out.println("s="+f2.toLogStr(false,m2));
		
		OrderItemAttr attr1 = OrderItemAttr.newBuilder().setName("color").setValue("red").build();
		OrderItemAttr attr2 = OrderItemAttr.newBuilder().setName("weight").setValue("1.3kg").build();
		OrderItem item1 = OrderItem.newBuilder().setItemId("111").setName("apple").addAttrs(attr1).addAttrs(attr2).build();
		OrderItem item2 = OrderItem.newBuilder().setItemId("222").setPrice(132).addAttrs(attr1).addAttrs(attr2).build();
		Order o = Order.newBuilder().addItems(item1).addItems(item2).setOrderId("100001").build();
		System.out.println("s="+f.toLogStr(true,o));
		System.out.println("s="+f2.toLogStr(true,o));

	}

}

