package krpc.rpc.util;

import java.io.IOException;

public class Snappy implements ZipUnzip {

	public byte[] zip(byte[] input) throws IOException {
		return org.xerial.snappy.Snappy.compress(input);
	}
	
	public byte[] unzip(byte[] input) throws IOException {
		return org.xerial.snappy.Snappy.uncompress(input);
	}

}


