package krpc.test.misc;

import com.google.protobuf.Descriptors;
import com.xxx.userservice.proto.GiclReq;
import krpc.KrpcExt;
import krpc.rpc.core.ValidateResult;
import krpc.rpc.impl.DefaultValidator;
import org.junit.Assert;
import org.junit.Test;

import com.xxx.userservice.proto.ValidateTest2Req;

import java.util.List;
import java.util.Map;

public class ValidatorTest2 {

    @Test
    public void test1() throws Exception {

        DefaultValidator v = new DefaultValidator();
        v.prepare(ValidateTest2Req.class);

        ValidateTest2Req.Builder b = ValidateTest2Req.newBuilder();

        ValidateTest2Req req = b.build();
        ValidateResult s = v.validate(req);
        Assert.assertEquals("ValidateTest2Req.userId", s.getFieldName());

        req = b.setUserId("1").setI1(20).build();
        s = v.validate(req);
        Assert.assertNull(s);

        req = b.setS1("a").build();
        s = v.validate(req);
        Assert.assertEquals(-100,s.getRetCode());
        System.out.println(s.getRetMsg());
        req = b.setS1("bbm").build();
        s = v.validate(req);
        Assert.assertNull(s);

        b.setS1("");

        req = b.setI1(11).build();
        s = v.validate(req);
        Assert.assertEquals(-621,s.getRetCode());
        System.out.println(s.getRetMsg());

        b.setI1(50);

        req = b.setS5("abc").build();
        s = v.validate(req);
        Assert.assertEquals(-101,s.getRetCode());
        System.out.println(s.getRetMsg());

        req = b.setS5("bbb").build();
        s = v.validate(req);
        Assert.assertNull(s);

        req = b.setS6("abc").build();
        s = v.validate(req);
        Assert.assertEquals(-621,s.getRetCode());
        System.out.println(s.getRetMsg());
        req = b.setS6("abcd").build();
        s = v.validate(req);
        Assert.assertNull(s);
        req = b.setS6("abcd1234").build();
        s = v.validate(req);
        Assert.assertNull(s);


        req = b.addS7("abc").build();
        s = v.validate(req);
        Assert.assertEquals(-621,s.getRetCode());
        System.out.println(s.getRetMsg());

        req = b.addS7("abc").addS7("def").build();
        s = v.validate(req);
        Assert.assertNull(s);

        req = b.setS31("abc").build();
        s = v.validate(req);
        Assert.assertEquals(-621,s.getRetCode());
        System.out.println(s.getRetMsg());


        req = b.setS31("100").build();
        s = v.validate(req);
        Assert.assertNull(s);
    }

    @Test
    public void test2() throws Exception {

        GiclReq b = GiclReq.newBuilder().build();
        List<Descriptors.FieldDescriptor> l = b.getDescriptorForType().getFields();

        Descriptors.FieldDescriptor f1 = l.get(0);
        Descriptors.FieldDescriptor f2 = l.get(1);

        for (Map.Entry<Descriptors.FieldDescriptor, Object> entry : f1.getOptions().getAllFields().entrySet()) {
            if (!entry.getKey().getFullName().equals("krpc.gicl"))
                continue;
            KrpcExt.Gicl v = (KrpcExt.Gicl) entry.getValue();
            System.out.println("v="+v);
        }

        for (Map.Entry<Descriptors.FieldDescriptor, Object> entry : f2.getOptions().getAllFields().entrySet()) {
            if (!entry.getKey().getFullName().equals("krpc.gicl"))
                continue;
            KrpcExt.Gicl v = (KrpcExt.Gicl) entry.getValue();
            System.out.println("v="+v);
        }

    }
}

