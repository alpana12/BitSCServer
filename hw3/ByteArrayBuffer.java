package hw3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class ByteArrayBuffer {
	private ByteArrayOutputStream bos = new ByteArrayOutputStream();
	private List<byte[]> byteArrays;

	public byte[] getRawBytes() {
		return bos.toByteArray();
	}

	public ByteArrayBuffer put(byte[] arr) {
		try {
			bos.write(arr);
			return this;
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ByteArrayBuffer put(int i) {
		bos.write(i);
		return this;
	}

	public ByteArrayBuffer put(double i) {
		byte[] a = new byte[Double.BYTES];
		put(a);
		return this;
	}
}
