package krpc.rpc.util.encrypt;

public interface Crypt {

    byte[] encrypt(byte[] input,String key);

    byte[] decrypt(byte[] input,String key);

}


