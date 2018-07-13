package krpc.rpc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;

public class CryptHelper {

    static Logger log = LoggerFactory.getLogger(CryptHelper.class);

    static public final String ALGORITHM__MD5 = "MD5";
    static public final String ALGORITHM__SHA = "SHA";
    static public final String ALGORITHM__HMAC_MD5 = "HmacMD5";
    static public final String ALGORITHM__MD5withRSA = "MD5withRSA";
    static public final String ALGORITHM__SHA1WithRSA = "SHA1WithRSA";

    static public final String ALGORITHM__RSA = "RSA";
    static public final String ALGORITHM__AES = "AES";
    static public final String ALGORITHM__BLOWFISH = "Blowfish";
    static public final String ALGORITHM__DES = "DES";
    static public final String ALGORITHM__DESEDE = "DESede";

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

    static public byte[] toBytes(String hexString) {
        int len = hexString.length() / 2;
        byte[] out = new byte[len];
        int pos = 0;
        int i = 0;
        while (i < len) {
            out[i] = (byte) (Character.digit(hexString.charAt(pos), 16) << 4 | Character.digit(hexString.charAt(pos + 1), 16));
            i += 1;
            pos += 2;
        }
        return out;
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


