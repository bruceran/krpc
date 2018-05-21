package krpc.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ZlibTool implements ZipUnzip {

	ThreadLocal<byte[]> tlBuff = new ThreadLocal<byte[]>() {
		protected byte[] initialValue() {
			return new byte[1024];
		}
	};

	public byte[] zip(byte[] input) throws IOException {
		Deflater defl = new Deflater();
		defl.setInput(input);
		defl.finish();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buff = tlBuff.get();
		try {
			while (!defl.finished()) {
				int len = defl.deflate(buff);
				bos.write(buff, 0, len);
			}
			defl.end();
		} finally {
			bos.close();
		}
		return bos.toByteArray();
	}

	public byte[] unzip(byte[] input) throws IOException {
		Inflater infl = new Inflater();
		infl.setInput(input);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buff = tlBuff.get();
		try {
			while (!infl.finished()) {
				int len = infl.inflate(buff);
				if (len == 0) {
					break;
				}
				bos.write(buff, 0, len);
			}
			infl.end();
		} catch (DataFormatException e) {
			throw new IOException("unzip error", e);
		} finally {
			bos.close();
		}
		return bos.toByteArray();
	}

}
