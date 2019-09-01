package krpc.test.utils;

import com.xxx.userservice.proto.*;
import krpc.rpc.util.*;
import org.junit.Test;

import java.util.Map;

public class Login3Test {

    @Test
    public void test2() throws Exception {

        Login3Req.Builder req = Login3Req.newBuilder();

        req.setUserName("aaa");

        req.putKvs("c","33");
        req.putKvs("d","44");

        req.putKvs2("m",100);

        Apple a = Apple.newBuilder().setColor("red").setWeight(100.0).build();
        Apple b = Apple.newBuilder().setColor("black").setWeight(50).build();
        Apple c = Apple.newBuilder().setColor("green").setWeight(50).build();

        req.putApples("c",c);
        req.putApples("a",a);
        req.putApples("b",b);

        req.putKvs3(2,"b");
        req.putKvs3(1,"a");
        req.putKvs3(11,"c");

        Login3Req req1  = req.build();

        Login3ReqBean bean = MessageToBean.toBean(req1,Login3ReqBean.class);
        System.out.println("bean="+bean);
//
//        bean.apples = new HashMap<>();
//        bean.apples.put("a", new AppleBean("white"));
//        bean.apples.put("b", new AppleBean("black"));

        Login3Req req5 = BeanToMessage.toMessage(Login3Req.class,bean);
        System.out.println("req5="+req5);
    }

    @Test
    public void test1() throws Exception {

        Login3Req.Builder req = Login3Req.newBuilder();
        req.putKvs("c","33");
        req.putKvs("d","44");

        req.putKvs2("m",100);

        Apple a = Apple.newBuilder().setColor("red").setWeight(100.0).build();
        Apple b = Apple.newBuilder().setColor("black").setWeight(50).build();
        Apple c = Apple.newBuilder().setColor("green").setWeight(50).build();

        req.putApples("c",c);
        req.putApples("a",a);
        req.putApples("b",b);

        req.putKvs3(2,"b");
        req.putKvs3(1,"a");
        req.putKvs3(11,"c");

        Login3Req req1  = req.build();


        Map m = MessageToMap.toMap(req1);
        System.out.println("m="+m);
        ((Map)m.get("kvs3")).remove(1);
        ((Map)m.get("kvs3")).remove(2);
        ((Map)m.get("kvs3")).put("1","aa");

        Login3Req req3 = MapToMessage.toMessage(Login3Req.class,m);
        System.out.println("req3="+req3);

        System.out.println("--------------");

        String json = MessageToJson.toJson(req1);
        System.out.println("json="+json);
        Login3Req req2 = JsonToMessage.toMessage(Login3Req.class,json);
        System.out.println("req2="+req2);

        System.out.println("--------------");

        Login3ReqCopy req4 = MessageToMessage.toMessage(Login3ReqCopy.class,req1);
        System.out.println("req4="+req4);

    }

    @Test
    public void test0() throws Exception {

        Login3Req.Builder req = Login3Req.newBuilder();
//        req.getKvsMap().put("a","11");
        //     req.getKvsMap().put("b","22");
        req.putKvs("c","33");
        req.putKvs("d","44");
        Login3Req req1  = req.build();

        System.out.println("req1="+req1);

        byte[] b = req1.toByteArray();
        Login3Req req2 = Login3Req.parseFrom(b);
        System.out.println("req2="+req2);

        System.out.println("req2="+req2.getKvsMap());
        System.out.println("req2="+req2.getKvsCount());
        System.out.println("req2="+req2.getKvsOrDefault("d",""));
        System.out.println("req2="+req2.getKvsOrDefault("e","55"));
    }
}

