package krpc.test.misc;

import krpc.rpc.cluster.RouterExpr;
import krpc.rpc.cluster.RouterExprParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RouterExprTest {

    @Test
    public void test1() throws Exception {

        Map<String, String> data = new HashMap<>();
        data.put("application", "abc");
        data.put("host", "192.168.1.3");

        RouterExprParser parser = new RouterExprParser();
        String s = "application == abc";
        RouterExpr expr = parser.parse(s);
        boolean match = expr.eval(data);
        Assert.assertTrue(match);

        s = "application == abcd";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "application == abcd,abc";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "host == 192.168.1.3";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "host == 192.168.1.31";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "host == 192.168.1.31,192.168.1.3";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "host == 192.168.1.*";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "host == 192.168.*.*";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "host != 192.168.1.3";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "host != 192.168.1.*";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);
    }


    @Test
    public void test2() throws Exception {

        Map<String, String> data = new HashMap<>();
        data.put("application", "abc");
        data.put("host", "192.168.1.3");
        data.put("msgId", "1");

        RouterExprParser parser = new RouterExprParser();
        String s = "application == abc && host == 192.168.1.3 ";
        RouterExpr expr = parser.parse(s);
        boolean match = expr.eval(data);
        Assert.assertTrue(match);

        s = "application == abcd || host == 192.168.1.3 ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);


        s = "application == abcd || host == 192.168.1.* ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "application == abcd || host == 192.168.1.* && msgId==2";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "application == abcd || host == 192.168.1.* && msgId==1";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "( application == abcd || host == 192.168.1.* ) && msgId==1";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "( application == abcd || host == 192.168.1.* ) && msgId==2";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "! ( application == abcd || host == 192.168.1.* ) && msgId==1";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);
    }

    @Test
    public void test3() throws Exception {

        Map<String, String> data = new HashMap<>();
        data.put("application", "abc");
        data.put("host", "192.168.1.3");
        data.put("addr", "192.168.1.3:1860");
        data.put("msgId", "1");

        RouterExprParser parser = new RouterExprParser("host,addr");
        String s = "application == abc && host == 192.168.1.3 ";
        RouterExpr expr = parser.parse(s);
        Assert.assertEquals(null, expr);

        s = " host == 192.168.1.3 ";
        expr = parser.parse(s);
        boolean match = expr.eval(data);
        Assert.assertTrue(match);

        s = " addr == 192.168.1.3:1860 ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);
    }

    @Test
    public void test4() throws Exception {

        Map<String, String> data = new HashMap<>();
        data.put("application", "abc");
        data.put("host", "192.168.1.3");
        data.put("addr", "192.168.1.3:1860");
        data.put("msgId", "1");

        RouterExprParser parser = new RouterExprParser();
        String s = " addr == 192.168.1.3:1860";
        RouterExpr expr = parser.parse(s);
        boolean match = expr.eval(data);
        Assert.assertTrue(match);

        s = " addr!=192.168.1.3:1860";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "  addr  !=  192.168.1.3:1860   ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = " ! ( addr  ==  192.168.1.3:1860  ) ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = " ! ( addr  ==  192.168.1.3:1860  ) || application == abc ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = " ! ( addr  ==  192.168.1.3:1860  || application == abc  ) ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = " ! application == abc  ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);
    }

}

