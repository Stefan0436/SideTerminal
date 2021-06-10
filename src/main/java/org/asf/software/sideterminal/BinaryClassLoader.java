package org.asf.software.sideterminal;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.CodeSource;
import java.security.cert.Certificate;

public class BinaryClassLoader extends URLClassLoader {

	public BinaryClassLoader(ClassLoader parent) {
		super(new URL[0], parent);
	}

	public BinaryClassLoader() {
		this(BinaryClassLoader.class.getClassLoader());
	}

	@Override
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		Class<?> cls = getParent().loadClass(name);

		if (resolve)
			this.resolveClass(cls);

		return cls;
	}

	public Class<?> loadClass(String name, byte[] bytecode) {
		return defineClass(name, ByteBuffer.wrap(bytecode),
				new CodeSource(getClass().getProtectionDomain().getCodeSource().getLocation(), (Certificate[]) null));
	}

}
