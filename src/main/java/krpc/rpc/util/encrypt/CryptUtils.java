package krpc.rpc.util.encrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class CryptUtils {

    private static final Logger log = LoggerFactory.getLogger(CryptUtils.class);

    public static final String ALGORITHM__RSA = "RSA";
    public static final String ALGORITHM__AES = "AES";
    public static final String TRANSFORMATION__RSA_ECB_PKCS1Padding = "RSA/ECB/PKCS1Padding";
    public static final String TRANSFORMATION__AES_CBC_PKCS5Padding = "AES/CBC/PKCS5Padding";
    public static final int KEY_SIZE__RSA = 1024;
    public static final int KEY_SIZE__AES = 128;

    public static byte[] encrypt(String transformation, Key key, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            if (iv != null) {
                cipher.init(1, key, new IvParameterSpec(iv));
            } else {
                cipher.init(1, key);
            }
            return cipher.doFinal(data);
        } catch (Exception e) {
            log.error("encrypt exception",e);
        }
        return null;
    }

    public static byte[] decrypt(String transformation, Key key, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            if (iv != null) {
                cipher.init(2, key, new IvParameterSpec(iv));
            } else {
                cipher.init(2, key);
            }
            return cipher.doFinal(data);
        } catch (Exception e) {
            log.error("decrypt exception",e);
        }
        return null;
    }

    private static byte[] encrypt(String algorithm, String transformation, byte[] key, byte[] iv, byte[] data) {
        try {
            if ("RSA".equalsIgnoreCase(algorithm)) {
                X509EncodedKeySpec encodedKeySpec = new X509EncodedKeySpec(key);
                KeyFactory factory = KeyFactory.getInstance(algorithm);
                PublicKey publicKey = factory.generatePublic(encodedKeySpec);
                return encrypt(transformation, publicKey, iv, data);
            }

            SecretKey secretKey = new SecretKeySpec(key, algorithm);
            return encrypt(transformation, secretKey, iv, data);
        } catch (Exception e) {
            log.error("encrypt exception",e);
        }
        return null;
    }

    private static byte[] decrypt(String algorithm, String transformation, byte[] key, byte[] iv, byte[] data) {
        try {
            if ("RSA".equalsIgnoreCase(algorithm)) {
                PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(key);
                KeyFactory factory = KeyFactory.getInstance(algorithm);
                PrivateKey privateKey = factory.generatePrivate(encodedKeySpec);
                return decrypt(transformation, privateKey, iv, data);
            }

            SecretKey desKey = new SecretKeySpec(key, algorithm);
            return decrypt(transformation, desKey, iv, data);
        } catch (Exception e) {
            log.error("decrypt exception",e);
        }
        return null;
    }

    public static String encryptBase64Utils(String algorithm, String transformation, String key, String iv, String data) {
        return encryptBase64Utils(algorithm, transformation, key, iv, data, "UTF-8");
    }

    public static String encryptBase64Utils(String algorithm, String transformation, String key, String iv, String data, String charset) {
        try {
            if (iv != null) {
                return Base64Helper.encode(encrypt(algorithm, transformation, Base64Helper.decode(key), iv.getBytes("UTF-8"), data.getBytes(charset)));
            }
            return Base64Helper.encode(encrypt(algorithm, transformation, Base64Helper.decode(key), null, data.getBytes(charset)));
        } catch (Exception e) {
            log.error("encryptBase64Utils exception",e);
        }
        return null;
    }

    public static String decryptBase64Utils(String algorithm, String transformation, String key, String iv, String data) {
        return decryptBase64Utils(algorithm, transformation, key, iv, data, "UTF-8");
    }

    public static String decryptBase64Utils(String algorithm, String transformation, String key, String iv, String data, String charset) {
        try {
            if (iv != null) {
                return new String(decrypt(algorithm, transformation, Base64Helper.decode(key), iv.getBytes("UTF-8"), Base64Helper.decode(data)), charset);
            }
            return new String(decrypt(algorithm, transformation, Base64Helper.decode(key), null, Base64Helper.decode(data)), charset);
        } catch (Exception e) {
            log.error("decryptBase64Utils exception",e);
        }
        return null;
    }

    public static String encryptHex(String algorithm, String transformation, String key, String iv, String data) {
        return encryptHex(algorithm, transformation, key, iv, data, "UTF-8");
    }

    public static String encryptHex(String algorithm, String transformation, String key, String iv, String data, String charset) {
        try {
            if (iv != null) {
                return HexHelper.toHexStr(encrypt(algorithm, transformation, HexHelper.toBytes(key), iv.getBytes("UTF-8"), data.getBytes(charset)));
            }
            return HexHelper.toHexStr(encrypt(algorithm, transformation, HexHelper.toBytes(key), null, data.getBytes(charset)));
        } catch (Exception e) {
            log.error("encryptHex exception",e);
        }
        return null;
    }

    public static String decryptHex(String algorithm, String transformation, String key, String iv, String data) {
        return decryptHex(algorithm, transformation, key, iv, data, "UTF-8");
    }

    public static String decryptHex(String algorithm, String transformation, String key, String iv, String data, String charset) {
        try {
            if (iv != null) {
                return new String(decrypt(algorithm, transformation, HexHelper.toBytes(key), iv.getBytes("UTF-8"), HexHelper.toBytes(data)), charset);
            }
            return new String(decrypt(algorithm, transformation, HexHelper.toBytes(key), null, HexHelper.toBytes(data)), charset);
        } catch (Exception e) {
            log.error("decryptHex exception",e);
        }
        return null;
    }

    public static String encryptRsaBase64Utils(String key, String data) {
        return encryptBase64Utils("RSA", "RSA/ECB/PKCS1Padding", key, null, data, "UTF-8");
    }

    public static String decryptRsaBase64Utils(String key, String data) {
        return decryptBase64Utils("RSA", "RSA/ECB/PKCS1Padding", key, null, data, "UTF-8");
    }

    public static String encryptAesBase64Utils(String key, String iv, String data) {
        return encryptBase64Utils("AES", "AES/CBC/PKCS5Padding", key, iv, data, "UTF-8");
    }

    public static String decryptAesBase64Utils(String key, String iv, String data) {
        return decryptBase64Utils("AES", "AES/CBC/PKCS5Padding", key, iv, data, "UTF-8");
    }

    public static KeyPair generateKeyPair(String algorithm, int keySize) {
        try {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(algorithm);
            keyPairGen.initialize(keySize, new SecureRandom());
            return keyPairGen.generateKeyPair();
        } catch (Exception e) {
            log.error("generateKeyPair exception",e);
        }
        return null;
    }

    public static RSAPublicKey generateRSAPublicKey(KeyPair keyPair) {
        return (RSAPublicKey) keyPair.getPublic();
    }

    public static RSAPrivateKey generateRSAPrivateKey(KeyPair keyPair) {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    public static RSAPublicKey generateRSAPublicKey(byte[] modulus, byte[] publicExponent) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(new BigInteger(modulus), new BigInteger(publicExponent));
            return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            log.error("generateRSAPublicKey exception",e);
        }
        return null;
    }

    public static RSAPrivateKey generateRSAPrivateKey(byte[] modulus, byte[] privateExponent) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKeySpec privateKeySpec = new RSAPrivateKeySpec(new BigInteger(modulus), new BigInteger(privateExponent));
            return (RSAPrivateKey) keyFactory.generatePrivate(privateKeySpec);
        } catch (Exception e) {
            log.error("generateRSAPrivateKey exception",e);
        }
        return null;
    }

    public static SecretKey generateSecretKey(String algorithm, int keySize) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(algorithm);
            keyGen.init(keySize, new SecureRandom());
            return keyGen.generateKey();
        } catch (Exception e) {
            log.error("generateSecretKey exception",e);
        }
        return null;
    }

    public static class Base64Helper {

        public static byte[] decode(String input) {
            return Base64.getDecoder().decode(input);
        }

        public static String encode(byte[] input) {
            return Base64.getEncoder().encodeToString(input);
        }

    }

    public static class HexHelper {

        static public String toHexStr(byte[] in) {
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

    }
/*
    static public void main(String[] args) {

        String key  = HexHelper.toHexStr("a39#@@$$r2134cdc".getBytes());
        String iv = "1234567890123456";

        String text = "abcdefg";

        String s = CryptUtils.encryptHex(CryptUtils.ALGORITHM__AES,CryptUtils.TRANSFORMATION__AES_CBC_PKCS5Padding,key,iv,text);
        System.out.println(new String(s));
        String s2 = CryptUtils.decryptHex(CryptUtils.ALGORITHM__AES,CryptUtils.TRANSFORMATION__AES_CBC_PKCS5Padding,key,iv,s);
        System.out.println(new String(s2));
    }
*/
}
