package krpc.rpc.util;

import java.io.IOException;

import org.xerial.snappy.Snappy;

public class SnappyTool implements ZipUnzip {

	public byte[] zip(byte[] input) throws IOException {
		return Snappy.compress(input);
	}
	
	public byte[] unzip(byte[] input) throws IOException {
		return Snappy.uncompress(input);
	}

}


