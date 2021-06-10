package org.asf.software.sideterminal;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * 
 * Main Class for Side Terminal (internal)
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
public class SideTermMain {

	private static Class<? extends SideTermShell> shellClass = SideTermShell.class;

	/**
	 * Main startup method
	 * 
	 * @param args Program arguments (first is main type)
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, SecurityException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnknownHostException,
			IOException, InterruptedException {
		if (args[0].equals("--client")) {
			Socket sock = new Socket("localhost", Integer.valueOf(args[1]));
			new Thread(() -> {
				while (true) {
					int i;
					try {
						i = sock.getInputStream().read();
					} catch (IOException e) {
						break;
					}
					if (i == -1)
						break;
					System.out.print((char) i);
				}
			}).start();
			while (true) {
				int i = System.in.read();
				if (i == -1)
					break;
				sock.getOutputStream().write(i);
				sock.getOutputStream().flush();
			}
			sock.close();
			System.exit(0);
		}
		if (System.getProperty("sideterminal.shell") != null)
			shellClass = (Class<? extends SideTermShell>) Class.forName(System.getProperty("sideterminal.shell"));
		String main = args[0];
		String[] arguments = Arrays.copyOfRange(args, 1, args.length);

		Thread server = new Thread(SideTermServer::start, "SideTerminal Server Thread");
		server.setDaemon(true);
		server.start();

		Class<?> cls = Class.forName(main);
		Method mainMethod = cls.getDeclaredMethod("main", String[].class);
		mainMethod.setAccessible(true);
		mainMethod.invoke(null, new Object[] { arguments });
	}

	public static SideTermShell getNewShell() {
		try {
			return shellClass.getConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			return null;
		}
	}

}
