package krpc.test.misc;

import com.xxx.userservice.proto.ValidateSub;
import com.xxx.userservice.proto.ValidateTestReq;
import krpc.rpc.core.ValidateResult;
import krpc.rpc.impl.DefaultValidator;
import org.junit.Assert;
import org.junit.Test;

public class ValidatorTest {

    @Test
    public void test2() throws Exception {

        DefaultValidator v = new DefaultValidator();
        v.prepare(ValidateTestReq.class);

        ValidateTestReq.Builder b = ValidateTestReq.newBuilder();

        ValidateTestReq req = b.build();
        ValidateResult s = v.validate(req);
        System.out.println(s.getRetMsg());

    }

    @Test
    public void test1() throws Exception {

        DefaultValidator v = new DefaultValidator();
        v.prepare(ValidateTestReq.class);

        ValidateTestReq.Builder b = ValidateTestReq.newBuilder();

        ValidateTestReq req = b.build();
        ValidateResult s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.userId", s.getFieldName());

        req = b.setUserId("1").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s1", s.getFieldName());

        req = b.setS1("aaa").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s1", s.getFieldName());
        req = b.setS1("ddd").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s1", s.getFieldName());
        req = b.setS1("bbb").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s2", s.getFieldName());

        req = b.setS2("baa").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s2", s.getFieldName());
        req = b.setS2("mmm").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i1", s.getFieldName());

        req = b.setI1(19).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i1", s.getFieldName());
        req = b.setI1(51).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i1", s.getFieldName());
        req = b.setI1(23).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i2", s.getFieldName());

        req = b.setI2("19").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i2", s.getFieldName());
        req = b.setI2("abc").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i2", s.getFieldName());
        req = b.setI2("20").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i4", s.getFieldName());
        req = b.setI2("20000").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i4", s.getFieldName());

        req = b.setI3(51).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i3", s.getFieldName());
        req = b.setI3(0).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i4", s.getFieldName());
        req = b.setI3(-200).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i4", s.getFieldName());

        req = b.setI4("20").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i4", s.getFieldName());
        req = b.setI4("21").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateSub.s1", s.getFieldName());

        ValidateSub.Builder bs = ValidateSub.newBuilder();
        bs.setS1("bbb").setS2("mmm").setI1(20).setI2("10000").setI3(0).setI4("21");
        req = b.setM(bs.build()).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s5", s.getFieldName());

        req = b.setS5("ddd").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s5", s.getFieldName());
        req = b.setS5("bbb").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s6", s.getFieldName());

        req = b.setS6("bbb").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s6", s.getFieldName());
        req = b.setS6("bbbbbbbbb").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s6", s.getFieldName());
        req = b.setS6("bbbbbbbb").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i5", s.getFieldName());
        req = b.setS6("bbbb").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i5", s.getFieldName());

        req = b.setI5(999).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.i5", s.getFieldName());
        req = b.setI5(1000).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s7", s.getFieldName());
        req = b.addS7("123").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s7", s.getFieldName());
        req = b.addS7("123").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s31", s.getFieldName());

        req = b.setS31("a").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s31", s.getFieldName());
        req = b.setS31("1.3").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s31", s.getFieldName());
        req = b.setS31("0").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s32", s.getFieldName());
        req = b.setS31("-1").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s32", s.getFieldName());
        req = b.setS31("1").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s32", s.getFieldName());

        req = b.setS32("a").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s32",s.getFieldName());
        req = b.setS32("1.3").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s33", s.getFieldName());
        req = b.setS32("0").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s33", s.getFieldName());
        req = b.setS32("-1.11111").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s33", s.getFieldName());

        req = b.setS33("a").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s33", s.getFieldName());
        req = b.setS33("1.3").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s33", s.getFieldName());
        req = b.setS33("0").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s34", s.getFieldName());

        req = b.setS34("0").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s34", s.getFieldName());
        req = b.setS34("a@a").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s34", s.getFieldName());
        req = b.setS34("a@a.com").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s35", s.getFieldName());

        req = b.setS35("abc").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s35", s.getFieldName());
        req = b.setS35("1982-01-13").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s36", s.getFieldName());

        req = b.setS36("abc").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s36", s.getFieldName());
        req = b.setS36("1982-01-13").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s36", s.getFieldName());
        req = b.setS36("1982-01-13 12:00:00").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s37", s.getFieldName());

        req = b.setS37("19m").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s37", s.getFieldName());
        req = b.setS37("a190ma").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s37", s.getFieldName());
        req = b.setS37("190m").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s38", s.getFieldName());
        req = b.setS37("19000000m").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s38", s.getFieldName());

        req = b.setS38("abcd").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s38", s.getFieldName());
        req = b.setS38("ac").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s39", s.getFieldName());
        req = b.setS38("abc").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s39", s.getFieldName());

        req = b.setS39("abcd").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s39", s.getFieldName());
        req = b.setS39("ac").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.k", s.getFieldName());
        req = b.setS39("abc").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.k", s.getFieldName());

        bs.setS1("bbb").setS2("mmm").setI1(20).setI2("10000").setI3(0).setI4("21");

        req = b.addK(bs.build()).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.k", s.getFieldName());
        req = b.addK(bs.build()).build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s41", s.getFieldName());

        req = b.addS41("aaa").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s41", s.getFieldName());
        req = b.addS41("222").build();
        s = v.validate(req);
        Assert.assertEquals("ValidateTestReq.s41", s.getFieldName());
        req = b.setS41(0, "111").build();
        s = v.validate(req);
        Assert.assertEquals(null, s);

    }

}

