package krpc.rpc.util;

import java.security.MessageDigest;

public class Md5 {

    static public final String ALGORITHM__MD5 = "MD5";

    static public String toHexString(byte[] in) {
        int len = in.length;
        StringBuilder sb = new StringBuilder(len * 2);
        int i = 0;
        while (i < len) {
            String tmp = Integer.toHexString(in[i] & 0xFF);
            if (tmp.length() < 2) {
                sb.append(0);
            }
            sb.append(tmp);
            i += 1;
        }
        return sb.toString();
    }

    static public byte[] sign(byte[] source, String algorithm) {
        if (source != null) {
            try {
                MessageDigest md5 = MessageDigest.getInstance(algorithm);
                return md5.digest(source);
            } catch (Exception e) {
            }
        }
        return null;
    }

    static public String md5(String source) {
        return md5(source, "utf-8");
    }

    static public String md5(String source, String charset) {
        try {
            return toHexString(sign(source.getBytes(charset), ALGORITHM__MD5));
        } catch (Exception e) {
            return null;
        }
    }

}


