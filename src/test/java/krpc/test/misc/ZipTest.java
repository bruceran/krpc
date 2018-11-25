package krpc.test.misc;

import krpc.rpc.util.compress.Snappy;
import krpc.rpc.util.compress.Zlib;
import org.junit.Assert;
import org.junit.Test;

public class ZipTest {

    @Test
    public void test1() throws Exception {

        String s = "abcdefg2020202019199191";
        byte[] bs = s.getBytes();

        Zlib zt = new Zlib();
        byte[] bs2 = zt.zip(bs);
        byte[] bs3 = zt.unzip(bs2);

        String s2 = new String(bs3);

        Assert.assertEquals(s, s2);
        System.out.println(s2);

    }

    @Test
    public void test2() throws Exception {

        String s = "abcdefg2020202019199191";
        byte[] bs = s.getBytes();

        Snappy zt = new Snappy();
        byte[] bs2 = zt.zip(bs);
        byte[] bs3 = zt.unzip(bs2);

        String s2 = new String(bs3);

        Assert.assertEquals(s, s2);
        System.out.println(s2);

    }

}

