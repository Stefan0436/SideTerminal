package org.asf.software.sideterminal;

import java.io.IOException;
import java.net.Socket;

public class SideTermClient {

	private Socket client;

	private SideTermClient(Socket client) {
		this.client = client;
	}

	public static void start(Socket sock) {
		new SideTermClient(sock).startThread();
	}

	public void write(String message) {
		try {
			client.getOutputStream().write(message.getBytes());
		} catch (IOException e) {
		}
	}

	public void writeLine(String message) {
		write(message + "\n");
	}

	private void startThread() {
		SideTermShell shell = SideTermMain.getNewShell();
		writeLine("Starting SideTerminal client connection...");
		Thread clientThread = new Thread(() -> {
			while (true) {
				try {
					int i = client.getInputStream().read();
					if (i == -1) {
						shell.destroy();
						break;
					}
					if (i == '\r')
						continue;
					shell.systemInput.write(i);
				} catch (IOException ex) {
					shell.destroy();
					break;
				}
			}
		}, "SideTerminal Client " + client);
		clientThread.setDaemon(true);
		clientThread.start();
		Thread clientOutputThread = new Thread(() -> {
			while (true) {
				try {
					int i = shell.systemOutput.read();
					if (i == -1) {
						shell.destroy();
						client.close();
						break;
					}
					client.getOutputStream().write(i);
				} catch (IOException ex) {
					shell.destroy();
					try {
						client.close();
					} catch (IOException e) {

					}
					break;
				}
			}
		}, "SideTerminal Client Output " + client);
		clientOutputThread.setDaemon(true);
		clientOutputThread.start();
		shell.start();
	}

}
