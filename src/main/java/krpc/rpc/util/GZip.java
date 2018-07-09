package krpc.rpc.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class GZip implements ZipUnzip {

	ThreadLocal<byte[]> tlBuff = new ThreadLocal<byte[]>() {
		protected byte[] initialValue() {
			return new byte[1024];
		}
	};

	public void zip(byte[] in,ByteBuf out)  throws IOException  {
		ByteBufOutputStream bos = new ByteBufOutputStream(out);
		try {
			GZIPOutputStream gzip = new GZIPOutputStream(bos);
			gzip.write(in);
			gzip.finish();
			gzip.close();
		} finally {
			bos.close();
		}
	}
	
	public byte[] zip(byte[] data)  throws IOException  {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] ret = null;
		try {
			GZIPOutputStream gzip = new GZIPOutputStream(bos);
			gzip.write(data);
			gzip.finish();
			gzip.close();
			ret = bos.toByteArray();
		} finally {
			bos.close();
		}
		return ret;
	}
 
	public byte[] unzip(byte[] data) throws IOException  {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] ret = null;
		try {
			GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
			byte[] buf = tlBuff.get();
			int num = -1;
			while ((num = gzip.read(buf, 0, buf.length)) != -1) {
				bos.write(buf, 0, num);
			}
			gzip.close();
			ret = bos.toByteArray();
		} finally {
			bos.close();
		}
		return ret;
	}

}
