package org.asf.software.sideterminal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class SideTermServer {

	private ServerSocket server;
	private int port;
	private Random random = new Random();

	public static void start() {
		new SideTermServer().startServer();
	}

	private void startServer() {
		while (true) {
			try {
				port = random.nextInt(Short.MAX_VALUE);
				while (port <= 1024)
					port = random.nextInt(Short.MAX_VALUE);
				server = new ServerSocket(port, 0, InetAddress.getLoopbackAddress());
				System.out.println("-- DEBUG --");
				System.out.println("Running SideTerminal on port " + getPort());
				System.out.println("-- DEBUG --");
				break;
			} catch (IOException e) {
			}
		}
		
		while (server != null) {
			try {
				Socket sock = server.accept();
				if (Thread.activeCount() > 3)
					break;
				SideTermClient.start(sock);
			} catch (IOException e) {

			}
		}
	}

	public int getPort() {
		return port;
	}

}
