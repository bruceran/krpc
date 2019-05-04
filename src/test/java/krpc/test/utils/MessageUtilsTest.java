package krpc.test.utils;

import com.xxx.userservice.proto.*;
import krpc.rpc.util.*;
import org.junit.Test;

import java.util.Map;

public class MessageUtilsTest {

    @Test
    public void test1() throws Exception {

        OrderDetail d = OrderDetail.newBuilder().setName("apple").setQuantity(1).setPrice(0).setNote("").build();
        System.out.println("d=" + d);
        String json = MessageToJson.toJson(d);
        System.out.println(json);
        OrderDetail d2 = JsonToMessage.toMessage(OrderDetail.class, json);
        System.out.println("d2=" + d2);

    }
    @Test
    public void test11() throws Exception {

        OrderItemAttr attr1 = OrderItemAttr.newBuilder().setName("weight").setValue("13.1g").build();
        OrderItemAttr attr2 = OrderItemAttr.newBuilder().setName("color").setValue("red").build();
        OrderItem i = OrderItem.newBuilder().setPrice(12).setItemId("1").setName("test").addAttrs(attr1).addAttrs(attr2).build();
        System.out.println("i="+i);
        String json = MessageToJson.toJson(i);
        System.out.println(json);
        OrderItem i2 = JsonToMessage.toMessage(OrderItem.class,json);
        System.out.println("i2="+i2);

    }
    @Test
    public void test13() throws Exception {

        Order2 d = Order2.newBuilder().setAmount(10).setOrderId("1").setSession(Session2.newBuilder().setMobile("135").build()).build();
        System.out.println("d=" + d);
        String json = MessageToJson.toJson(d);
        System.out.println(json);
        Map map = MessageToMap.toMap(d);
        System.out.println(map);

    }

    @Test
    public void test2() throws Exception {
        OrderDetail d = OrderDetail.newBuilder().setName("apple").setQuantity(1).setPrice(3.3).setNote("xxx").build();
        System.out.println("d="+d);
        Map map = MessageToMap.toMap(d);
        System.out.println(map);

        OrderDetail d2 = MapToMessage.toMessage(OrderDetail.class,map);
        System.out.println("d2="+d2);
    }


    @Test
    public void test22() throws Exception {

        OrderItemAttr attr1 = OrderItemAttr.newBuilder().setName("weight").setValue("13.1g").build();
        OrderItemAttr attr2 = OrderItemAttr.newBuilder().setName("color").setValue("3333").build();
        OrderItem i = OrderItem.newBuilder().setPrice(12).setItemId("1").setName("apple").addAttrs(attr1).addAttrs(attr2).build();
        System.out.println("i="+i);
        Map json = MessageToMap.toMap(i);
        System.out.println(json);
        OrderItem i2 = MapToMessage.toMessage(OrderItem.class,json);
        System.out.println("i2="+i2);

    }


    @Test
    public void test3() throws Exception {

        OrderDetail d = OrderDetail.newBuilder().setName("apple").setQuantity(1).setPrice(3.3).setNote("1514776282000").build();
        System.out.println("d="+d);
        OrderDetailVO map = MessageToBean.toBean(d,OrderDetailVO.class);
        System.out.println(map);
        OrderDetailVO2 map2 = MessageToBean.toBean(d,OrderDetailVO2.class);
        System.out.println(map2);
        System.out.println(map2.note == null ? "null":map2.note.getTime());

        OrderDetail d2 = BeanToMessage.toMessage(OrderDetail.class,map);
        System.out.println("d2="+d2);
        OrderDetail d22 = BeanToMessage.toMessage(OrderDetail.class,map2);
        System.out.println("d22="+d22);
    }

    @Test
    public void test33() throws Exception {

        OrderItemAttr attr1 = OrderItemAttr.newBuilder().setName("weight").setValue("13.1g").build();
        OrderItemAttr attr2 = OrderItemAttr.newBuilder().setName("color").setValue("red").build();
        OrderItem i = OrderItem.newBuilder().setPrice(12).setItemId("1").setName("test").addAttrs(attr1).addAttrs(attr2).build();
        System.out.println("i="+i);
        ItemVO json = MessageToBean.toBean(i,ItemVO.class);
        System.out.println(json);
        OrderItem i2 = BeanToMessage.toMessage(OrderItem.class,json);
        System.out.println("i2="+i2);
    }

    @Test
    public void test40() throws Exception {

        Order2 d = Order2.newBuilder().setAmount(10).setOrderId("1")
                .setSession(Session2.newBuilder().setMobile("135").build())
                .addItems(OrderItem2.newBuilder().setItemId("111").setPrice(222).setName("apple").addAttrs(OrderItemAttr2.newBuilder().setName("a").setValue("1").build()).build())
                .addItems(OrderItem2.newBuilder().setItemId("333").setPrice(444).setName("orange").addAttrs(OrderItemAttr2.newBuilder().setName("b").setValue("2").build()).build())
                .build();
        Session2 s2 = d.getSession();
        System.out.println("src=" + d);
        System.out.println("src.session=" + d.getSession());
        Order dest = MessageToMessage.toMessage(Order.class, d);
        System.out.println("dest=" + dest);

    }
}

