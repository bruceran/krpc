package krpc.rpc.util.encrypt;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Aes implements Crypt {

    private static String algorithm = CryptUtils.ALGORITHM__AES;
    private static String transformation = CryptUtils.TRANSFORMATION__AES_CBC_PKCS5Padding;
    public static byte[] iv = "2018101800112299".getBytes();

    public byte[] encrypt(byte[] input,String key) {
        SecretKey sk = new SecretKeySpec(key.getBytes(), algorithm);
        return CryptUtils.encrypt(transformation, sk, iv, input);
    }

    public byte[] decrypt(byte[] input,String key) {
        SecretKey sk = new SecretKeySpec(key.getBytes(), algorithm);
        return CryptUtils.decrypt(transformation, sk, iv, input);
    }

}


