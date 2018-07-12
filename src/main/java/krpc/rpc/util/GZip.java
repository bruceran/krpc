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

	public void zip(byte[] in, ByteBuf out) throws IOException {

		try (ByteBufOutputStream bos = new ByteBufOutputStream(out);
				GZIPOutputStream gzip = new GZIPOutputStream(bos);) {
			gzip.write(in);
			gzip.finish();
		}
	}

	public byte[] zip(byte[] in) throws IOException {

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				GZIPOutputStream gzip = new GZIPOutputStream(bos);) {
			gzip.write(in);
			gzip.finish();
			gzip.flush();
			byte[] ret = bos.toByteArray();
			return ret;
		}
	}

	public byte[] unzip(byte[] in) throws IOException {

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(in));) {

			byte[] buf = tlBuff.get();
			int num = -1;
			while ((num = gzip.read(buf, 0, buf.length)) != -1) {
				bos.write(buf, 0, num);
			}

			byte[] ret = bos.toByteArray();
			return ret;
		}
	}

}
