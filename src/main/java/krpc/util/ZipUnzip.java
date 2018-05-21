package krpc.util;

import java.io.IOException;

public interface ZipUnzip {

	byte[] zip(byte[] input) throws IOException;
	byte[] unzip(byte[] input) throws IOException;

}


