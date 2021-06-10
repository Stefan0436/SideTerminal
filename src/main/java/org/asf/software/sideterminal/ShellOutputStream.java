package org.asf.software.sideterminal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class ShellOutputStream extends OutputStream {

	private OutputStream delegate;
	private boolean closed = false;
	private ArrayList<Integer> buffer = new ArrayList<Integer>();

	public ShellOutputStream() {
		this(null);
	}

	public ShellOutputStream(OutputStream delegate) {
		this.delegate = delegate;
	}

	@Override
	public void close() {
		closed = true;
		buffer.clear();
	}

	@Override
	public void write(int i) {
		if (delegate != null)
			try {
				delegate.write(i);
			} catch (IOException e) {
			}
		else
			buffer.add(i);
	}

	public void write(byte i) {
		if (delegate != null)
			try {
				delegate.write(i);
			} catch (IOException e) {
			}
		else
			buffer.add((int) i);
	}

	public void write(byte[] i) {
		for (byte b : i)
			write(b);
	}

	public void writeLine(String line) {
		write(line + "\n");
	}

	public void write(String string) {
		write(string.getBytes());
	}

	public int read() throws IOException {
		while (buffer.size() == 0) {
			if (autoclose)
				close();
			if (closed)
				return -1;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				break;
			}
		}
		if (closed)
			return -1;
		while (buffer.get(0) == null)
			buffer.remove(0);
		int i = buffer.get(0);
		buffer.remove(0);
		return i;
	}

	private boolean autoclose = false;

	public void autoClose() {
		autoclose = true;
	}

	public byte[] readAllBytes() {
		ArrayList<Byte> bytes = new ArrayList<Byte>();
		while (true) {
			int i;
			try {
				i = read();
			} catch (IOException e) {
				break;
			}
			if (i == -1)
				break;

			if (bytes.size() == Integer.MAX_VALUE)
				break;
			bytes.add((byte) i);
		}
		byte[] arr = new byte[bytes.size()];
		int i = 0;
		for (byte b : bytes)
			arr[i++] = b;
		return arr;
	}

}
