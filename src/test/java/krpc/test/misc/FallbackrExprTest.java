package krpc.test.misc;

import krpc.rpc.impl.FallbackExpr;
import krpc.rpc.impl.FallbackExprParser;
import org.junit.Assert;
import org.junit.Test;

public class FallbackrExprTest {

    @Test
    public void test1() throws Exception {

        FallbackExpr.DataProvider data = new FallbackExpr.DataProvider() {
            public String get(String key) {
                switch (key) {
                    case "a":
                        return "abc";
                    case "b":
                        return "1";
                    case "c":
                        return "235567";
                    case "d":
                        return "2";
                    default:
                        return null;
                }
            }
        };

        FallbackExprParser parser = new FallbackExprParser();
        String s = "   a == abc  && b == 1 && c =~ 235.* && d in 1,2,3   ";
        FallbackExpr expr = parser.parse(s);
        boolean match = expr.eval(data);
        Assert.assertTrue(match);

        s = "!( a == abc && b == 1 )";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "!( a == abc && b == 1 ) || c == 2355 ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "!( a == abc && b == 1 ) || c =~ 2355.* ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "!( a == abc && b == 1 ) || c !~ 2355.* ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "!( a == abc && b == 1 ) || (c =~ 2355.* && d in 1,2,3) ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertTrue(match);

        s = "!( a == abc && b == 1 ) || (c =~ 2355.* && d not_in 1,2,3) ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);

        s = "  c == abc ";
        expr = parser.parse(s);
        match = expr.eval(data);
        Assert.assertFalse(match);
    }


}

