package org.asf.software.sideterminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.function.Supplier;

public class ShellInputStream extends InputStream {

	private boolean closed = false;
	private ArrayList<Integer> buffer = new ArrayList<Integer>();

	private ShellOutputStream delegate = null;
	private OutputStream delegateOutput = null;

	public ShellInputStream() {
	}

	public ShellInputStream(ShellOutputStream delegate) {
		this.delegate = delegate;
	}

	public ShellInputStream(OutputStream delegate) {
		this.delegateOutput = delegate;
	}

	@Override
	public void close() {
		closed = true;
		buffer.clear();
	}

	public void write(int i) throws IOException {
		if (delegate != null)
			delegate.write(i);
		if (delegateOutput != null)
			delegateOutput.write(i);
		if (delegate == null && delegateOutput == null)
			buffer.add(i);
	}

	public String readStringUntilDelim(int delim) {
		StringBuilder buffer = new StringBuilder();
		while (true) {
			int d = read();
			if (d == -1)
				return null;
			if (d == delim)
				break;
			buffer.append((char) d);
		}
		return buffer.toString();
	}

	public byte[] readUntilDelim(int delim) {
		ArrayList<Integer> buffer = new ArrayList<Integer>();
		while (true) {
			int d = read();
			if (d == -1)
				return null;
			if (d == delim)
				break;
			buffer.add(d);
		}
		byte[] data = new byte[buffer.size()];
		for (int i = 0; i < data.length; i++)
			data[i] = (byte) (int) buffer.get(i);
		return data;
	}

	@Override
	public int read() {
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
		int i = buffer.get(0);
		buffer.remove(0);
		return i;
	}

	public int read(Supplier<Boolean> check) {
		if (delegate != null)
			try {
				return delegate.read();
			} catch (IOException e1) {
			}
		while (buffer.size() == 0) {
			if (autoclose)
				close();
			if (closed || !check.get())
				return -1;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				break;
			}
		}
		if (closed)
			return -1;
		if (!check.get())
			return -1;
		int i = buffer.get(0);
		buffer.remove(0);
		return i;
	}

	public String readLine() {
		return readStringUntilDelim('\n').replace("\r", "");
	}

	boolean autoclose = false;

	public void autoClose() {
		autoclose = true;
	}

}
