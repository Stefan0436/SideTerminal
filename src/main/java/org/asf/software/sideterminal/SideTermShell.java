package org.asf.software.sideterminal;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.asf.software.sideterminal.commands.CdCommand;
import org.asf.software.sideterminal.commands.ExitCommand;
import org.asf.software.sideterminal.commands.HelpCommand;
import org.asf.software.sideterminal.commands.JavaInvoke;
import org.asf.software.sideterminal.commands.NewCommand;
import org.asf.software.sideterminal.commands.UnsetCommand;
import org.asf.software.sideterminal.commands.VarsCommand;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * 
 * SideTerminal Shell - Parent class for SideTerminal-compatible shells.
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
public class SideTermShell {

	private BinaryClassLoader binLoader = new BinaryClassLoader();

	/**
	 * Shell name
	 */
	protected String getName() {
		return "Dragonfly Shell";
	}

	/**
	 * Shell version
	 */
	protected String getVersion() {
		return "1.0.0.A1";
	}

	private SideTermCommand[] commands = null;

	/**
	 * Retrieves the known shell commands (embedded commands only)
	 */
	public SideTermCommand[] getCommands() {
		if (commands == null)
			commands = defaultCommands();
		return commands;
	}

	/**
	 * Defines the default known commands
	 */
	protected SideTermCommand[] defaultCommands() {
		return new SideTermCommand[] { new CdCommand(), new ExitCommand(), new UnsetCommand(), new JavaInvoke(),
				new NewCommand(), new VarsCommand(), new HelpCommand() };
	}

	/**
	 * Compiles java code into a class wrapper
	 * 
	 * @param javaCode     Java file content
	 * @param className    Java class simple name (must match name specified in
	 *                     class)
	 * @param classPackage Java class package
	 * @return Class instance
	 * @throws IOException If compiling fails
	 */
	protected Class<?> compileJavaCode(String javaCode, String className, String classPackage) throws IOException {
		File javacode = new File("java-temp-" + System.currentTimeMillis(), "/" + className + ".java");
		try {
			javacode.getParentFile().mkdirs();
			Files.write(javacode.toPath(), javaCode.getBytes());
			ProcessBuilder builder = new ProcessBuilder("javac", "-classpath", data.get("CLASS.PATH").toString(),
					javacode.getAbsolutePath());
			Process proc = builder.start();
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
			}
			javacode.delete();
			if (proc.exitValue() != 0) {
				throw new IOException("Compiler exited with non-zero exit code.\n"
						+ new String(proc.getErrorStream().readAllBytes()));
			}

			File classFile = new File(javacode.getParentFile(), className + ".class");
			byte[] bytecode = Files.readAllBytes(classFile.toPath());

			ClassReader reader = new ClassReader(bytecode);
			ClassNode cls = new ClassNode();
			reader.accept(cls, 0);
			cls.name = ((classPackage.isEmpty() ? "" : classPackage + ".") + className).replace(".", "/") + "$AutoGen_"
					+ System.currentTimeMillis() + "_" + System.nanoTime();
			if (cls.interfaces == null)
				cls.interfaces = new ArrayList<String>();
			if (!cls.interfaces.contains(Serializable.class.getTypeName().replace(".", "/"))) {
				cls.interfaces.add(Serializable.class.getTypeName().replace(".", "/"));
			}
			ClassWriter writer = new ClassWriter(0);
			cls.accept(writer);
			bytecode = writer.toByteArray();

			classFile.delete();
			classFile.getParentFile().delete();
			return binLoader.loadClass(cls.name.replace("/", "."), bytecode);
		} finally {
			javacode.delete();
			File classFile = new File(javacode.getParentFile(), className + ".class");
			classFile.delete();
			classFile.getParentFile().delete();
		}
	}

	/**
	 * Map of generated classes
	 */
	public HashMap<String, Class<?>> generatedClasses = new HashMap<String, Class<?>>();

	/**
	 * All child processes attached to this shell
	 */
	public ArrayList<Process> childProcesses = new ArrayList<Process>();

	/**
	 * Variable objects
	 */
	public HashMap<String, Object> data = new HashMap<String, Object>();

	/**
	 * System output stream (network)
	 */
	public ShellOutputStream systemOutput = new ShellOutputStream();

	/**
	 * System input stream (network)
	 */
	public ShellInputStream systemInput = new ShellInputStream();

	/**
	 * Current working directory
	 */
	public File pwd = new File(".");

	private String[] split(String input, char delim) {
		ArrayList<String> result = new ArrayList<String>();

		String buffer = "";
		for (char ch : input.toCharArray()) {
			if (ch == delim) {
				if (!buffer.isEmpty())
					result.add(buffer);
				buffer = "";
			} else {
				buffer += ch;
			}
		}

		return result.toArray(t -> new String[t]);
	}

	/**
	 * Retrieves the command file path by using the system variables
	 * 
	 * @param file Input command
	 * @return Full path or null
	 */
	public String getCommandPath(String file) {
		for (String segment : split(
				"." + File.pathSeparatorChar + data.getOrDefault("PATH", System.getenv("PATH")).toString(),
				File.pathSeparatorChar)) {
			File bin = new File(segment, file);
			if (bin.exists())
				return bin.getAbsolutePath();
			bin = new File(segment, file + ".exe");
			if (bin.exists())
				return bin.getAbsolutePath();
			bin = new File(segment, file + ".bat");
			if (bin.exists())
				return bin.getAbsolutePath();
			bin = new File(segment, file + ".cmd");
			if (bin.exists())
				return bin.getAbsolutePath();
			bin = new File(segment, file + ".com");
			if (bin.exists())
				return bin.getAbsolutePath();
		}
		return null;
	}

	/**
	 * Runs the given process
	 * 
	 * @param command Command and arguments to run
	 * @param outp    Shell output stream
	 * @param inp     Shell input stream
	 * @return Process instance
	 * @throws IOException If starting the process fails
	 */
	public Process runProcess(String[] command, ShellOutputStream outp, ShellInputStream inp) throws IOException {
		ProcessBuilder builder = new ProcessBuilder();
		String pth = getCommandPath(command[0]);
		if (pth == null)
			throw new IOException("Command not recognized");
		command[0] = pth;
		builder.command(command);

		data.forEach((k, v) -> {
			if (k.isEmpty())
				return;
			if (v instanceof String || v.getClass().isPrimitive()) {
				builder.environment().put(k, v.toString());
			}
		});

		builder.directory(pwd.getCanonicalFile());
		Process proc = builder.start();

		Thread th = new Thread(() -> {
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
			}
			if (childProcesses.contains(proc))
				childProcesses.remove(proc);
		}, "Shell process cleanup");
		th.setDaemon(true);
		th.start();

		attachLoggers(proc, outp, inp);
		childProcesses.add(proc);
		return proc;
	}

	private void attachLoggers(Process proc, ShellOutputStream outp, ShellInputStream inp) {
		Thread th = new Thread(() -> {
			while (true) {
				try {
					int i = proc.getInputStream().read();
					if (i == -1)
						break;
					outp.write(i);
				} catch (IOException e) {
					break;
				}
			}
		}, "Process Output Logger");
		th.setDaemon(true);
		th.start();
		th = new Thread(() -> {
			while (true) {
				try {
					int i = proc.getErrorStream().read();
					if (i == -1)
						break;
					outp.write(i);
				} catch (IOException e) {
					break;
				}
			}
		}, "Process Error Logger");
		th.setDaemon(true);
		th.start();
		th = new Thread(() -> {
			while (true) {
				try {
					int i = inp.read(() -> proc.isAlive());
					if (i == -1)
						break;
					proc.getOutputStream().write(i);
					proc.getOutputStream().flush();
				} catch (IOException e) {
					break;
				}
			}
			try {
				proc.getOutputStream().close();
			} catch (IOException e) {
			}
		}, "Process Input Handler");
		th.setDaemon(true);
		th.start();
	}

	private class CommandEntry {
		public ArrayList<String> command = new ArrayList<String>();
		public boolean pipeToRight = false;
		public boolean receivePipe = false;
		public boolean nextOnlyIf = false;
		public boolean nextOnlyIfNot = false;
	}

	void start() {
		data.put("CLASS.PATH", System.getProperty("java.class.path"));
		Thread th = new Thread(() -> {
			systemOutput.writeLine("");
			systemOutput.writeLine("Welcome to SideTerminal!");
			systemOutput.writeLine(
					"SideTerminal is a debug shell running in the background of the program currently being debugged.");
			systemOutput.writeLine("");
			systemOutput.writeLine("Running on Java " + System.getProperty("java.vm.version") + ", vendor: "
					+ System.getProperty("java.vm.vendor"));
			systemOutput.writeLine(
					"PID: " + ProcessHandle.current().pid() + ", SideTerminal " + getName() + " " + getVersion());
			while (data != null) {
				if (!data.containsKey("SIDETERM.HIDE.INPUT") || !data.get("SIDETERM.HIDE.INPUT").equals("true")) {
					systemOutput.writeLine("");
					try {
						systemOutput.write(pwd.getCanonicalPath() + "> ");
					} catch (IOException e) {
						systemOutput.write(pwd.getAbsolutePath() + "> ");
					}
				}
				String input = systemInput.readStringUntilDelim('\n');
				if (input == null)
					return;
				if (data == null)
					return;
				try {
					processInput(input, () -> systemInput.readStringUntilDelim('\n'), systemInput, systemOutput);
				} catch (IOException e) {
				}
				if (data == null)
					return;
			}
		}, "SideTerminal Shell");
		th.setDaemon(true);
		th.start();
	}

	static final Map<Class<?>, Class<?>> PRIMITIVES = Map.of(byte.class, Byte.class, short.class, Short.class,
			int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class,
			boolean.class, Boolean.class, char.class, Character.class);

	/**
	 * Runs the given input
	 * 
	 * @param input        Command input
	 * @param nextLine     Method to call the get the next line
	 * @param systemInput  System input stream
	 * @param systemOutput System output stream
	 * @throws IOException If running the command fails
	 */
	protected boolean processInput(String input, Supplier<String> nextLine, ShellInputStream systemInput,
			ShellOutputStream systemOutput) throws IOException {
		ArrayList<File> pipeFiles = new ArrayList<File>();
		ArrayList<CommandEntry> commands = new ArrayList<CommandEntry>();

		CommandEntry last = null;
		String buffer = "";
		ArrayList<String> commandArgs = new ArrayList<String>();

		boolean quote = false;
		boolean escape = false;
		boolean filePipeIn = false;
		boolean delimRead = false;

		String inputFile = "";
		boolean par = false;
		boolean skip = false;
		int index = 0;
		for (char ch : input.toCharArray()) {
			if (skip) {
				index++;
				skip = true;
				continue;
			}
			if (filePipeIn) {
				if (ch == ' ' && inputFile.isBlank()) {
					index++;
					continue;
				}
				if (ch == '<' && inputFile.isBlank()) {
					delimRead = true;
					index++;
					continue;
				}
				if (!escape) {
					if (ch == '\\') {
						escape = true;
						inputFile += ch;
						index++;
						continue;
					} else if (ch == '\"' || ch == '(' || ch == ')') {
						if (!quote) {
							par = false;
							if (ch == '(')
								par = true;
							quote = true;
							if (!par) {
								index++;
								continue;
							}
						} else {
							if (ch == '\"' && !par) {
								quote = false;
								if (!par) {
									index++;
									continue;
								}
							} else if (ch == ')' && par) {
								quote = false;
								if (!par) {
									index++;
									continue;
								}
							}
						}
					} else {
						if (!quote) {
							if (ch == ' ') {
								inputFile = inputFile.trim();
								if (delimRead) {
									StringBuilder data = new StringBuilder();
									while (true) {
										String d = nextLine.get();
										if (d.equals(inputFile))
											break;
										data.append(d).append(System.lineSeparator());
									}
									File pipeFile = File.createTempFile("stsh", ".pipe");
									Files.write(pipeFile.toPath(), data.toString().getBytes());
									pipeFiles.add(pipeFile);
									commandArgs.add(pipeFile.getAbsolutePath());
								} else {
									boolean valid = false;
									if (inputFile.startsWith("(")) {
										inputFile = inputFile.substring(1);
										if (inputFile.contains(")")) {
											inputFile = inputFile.substring(0, inputFile.lastIndexOf(")"));
											valid = true;
											File pipeFile = File.createTempFile("stsh", ".pipe");
											ShellOutputStream output = new ShellOutputStream();
											try {
												processInput(input, nextLine, systemInput, output);
											} catch (IOException e) {
											}
											Files.write(pipeFile.toPath(), output.readAllBytes());
											output.close();
											pipeFiles.add(pipeFile);
											commandArgs.add(pipeFile.getAbsolutePath());
										}
									}
									if (!valid) {
										File inputData = new File(inputFile);
										File pipeFile = File.createTempFile("stsh", ".pipe");
										if (!inputData.exists()) {
											Files.write(pipeFile.toPath(), new byte[0]);
										} else {
											Files.copy(inputData.toPath(), pipeFile.toPath());
										}
										pipeFiles.add(pipeFile);
										commandArgs.add(pipeFile.getAbsolutePath());
									}
								}
								filePipeIn = false;
								delimRead = false;
								inputFile = "";
								index++;
								continue;
							}
						}
					}
				}

				inputFile += ch;
				escape = false;
				index++;
				continue;
			}
			if (!escape) {
				if (ch == '\\') {
					escape = true;
					index++;
					continue;
				} else if (ch == '\"') {
					quote = !quote;
					index++;
					continue;
				} else {
					if (!quote) {
						if (ch == ' ') {
							buffer = buffer.trim();
							if (!buffer.isBlank()) {
								commandArgs.add(buffer);
							}
							buffer = "";
							index++;
							continue;
						} else if (ch == '<') {
							filePipeIn = true;
							index++;
							continue;
						} else if (ch == ';') {
							buffer = buffer.trim();
							if (!buffer.isBlank()) {
								commandArgs.add(buffer);
							}
							buffer = "";

							if (commandArgs.size() != 0 && !commandArgs.get(0).startsWith("#")) {
								CommandEntry command = new CommandEntry();
								if (last != null && last.pipeToRight) {
									command.receivePipe = true;
								}
								command.command = new ArrayList<String>(commandArgs);
								commands.add(command);
								commandArgs.clear();
								last = command;
							}
							index++;
							continue;
						} else if (ch == '|' && (index == input.length() || input.charAt(index + 1) != '|')) {
							buffer = buffer.trim();
							if (!buffer.isBlank()) {
								commandArgs.add(buffer);
							}
							buffer = "";

							if (commandArgs.size() != 0 && !commandArgs.get(0).startsWith("#")) {
								CommandEntry command = new CommandEntry();
								if (last != null && last.pipeToRight) {
									command.receivePipe = true;
								}
								command.command = new ArrayList<String>(commandArgs);
								command.pipeToRight = true;
								commands.add(command);
								commandArgs.clear();
								last = command;
							}
							index++;
							continue;
						} else if (ch == '|') {
							buffer = buffer.trim();
							if (!buffer.isBlank()) {
								commandArgs.add(buffer);
							}
							buffer = "";

							if (commandArgs.size() != 0 && !commandArgs.get(0).startsWith("#")) {
								CommandEntry command = new CommandEntry();
								if (last != null && last.pipeToRight) {
									command.receivePipe = true;
								}
								command.command = new ArrayList<String>(commandArgs);
								command.nextOnlyIfNot = true;
								commands.add(command);
								commandArgs.clear();
								last = command;
							}
							index++;
							continue;
						} else if (ch == '&' && (index < input.length() && input.charAt(index + 1) != '&')) {
							buffer = buffer.trim();
							if (!buffer.isBlank()) {
								commandArgs.add(buffer);
							}
							buffer = "";

							if (commandArgs.size() != 0 && !commandArgs.get(0).startsWith("#")) {
								CommandEntry command = new CommandEntry();
								if (last != null && last.pipeToRight) {
									command.receivePipe = true;
								}
								command.command = new ArrayList<String>(commandArgs);
								command.nextOnlyIf = true;
								commands.add(command);
								commandArgs.clear();
								last = command;
							}
							index++;
							continue;
						}
					}
				}
			} else {
				if (ch != '\\' && ch != '\"' && ch != '<' && ch != ';' && ch != '|')
					buffer += '\\';
			}
			buffer += ch;
			escape = false;
			index++;
		}
		if (!inputFile.isBlank()) {
			inputFile = inputFile.trim();
			if (delimRead) {
				StringBuilder data = new StringBuilder();
				while (true) {
					String d = nextLine.get();
					if (d.equals(inputFile))
						break;
					data.append(d).append(System.lineSeparator());
				}
				File pipeFile = File.createTempFile("stsh", ".pipe");
				Files.write(pipeFile.toPath(), data.toString().getBytes());
				pipeFiles.add(pipeFile);
				commandArgs.add(pipeFile.getAbsolutePath());
			} else {
				boolean valid = false;
				if (inputFile.startsWith("(")) {
					inputFile = inputFile.substring(1);
					if (inputFile.contains(")")) {
						inputFile = inputFile.substring(0, inputFile.lastIndexOf(")"));
						valid = true;

						File pipeFile = File.createTempFile("stsh", ".pipe");
						ShellOutputStream output = new ShellOutputStream();
						try {
							processInput(input, nextLine, systemInput, output);
						} catch (IOException e) {
						}
						Files.write(pipeFile.toPath(), output.readAllBytes());
						output.close();
						pipeFiles.add(pipeFile);
						commandArgs.add(pipeFile.getAbsolutePath());
					}
				}
				if (!valid) {
					File inputData = new File(inputFile);
					File pipeFile = File.createTempFile("stsh", ".pipe");
					if (!inputData.exists()) {
						Files.write(pipeFile.toPath(), new byte[0]);
					} else {
						Files.copy(inputData.toPath(), pipeFile.toPath());
					}
					pipeFiles.add(pipeFile);
					commandArgs.add(pipeFile.getAbsolutePath());
				}
			}
			filePipeIn = false;
			delimRead = false;
			inputFile = "";
		}
		buffer = buffer.trim();
		if (!buffer.isBlank()) {
			commandArgs.add(buffer);
		}
		buffer = "";

		if (commandArgs.size() != 0 && !commandArgs.get(0).startsWith("#")) {
			CommandEntry command = new CommandEntry();
			if (last != null && last.pipeToRight) {
				command.receivePipe = true;
			}
			command.command = new ArrayList<String>(commandArgs);
			commands.add(command);
			commandArgs.clear();
			last = command;
		}

		ShellInputStream stdIn = systemInput;
		ShellOutputStream stdOut = systemOutput;
		int i = 0;
		for (CommandEntry cmd : commands) {
			int i2 = 0;
			for (String arg : cmd.command) {
				arg = arg.replace("**RP", "****RP");
				if (data == null)
					return false;
				for (String k : data.keySet()) {
					Object v = data.get(k);
					arg = arg.replace("\\$", "**RP");
					if (k.isEmpty())
						continue;
					if (v instanceof String || PRIMITIVES.containsKey(v.getClass())
							|| PRIMITIVES.containsValue(v.getClass())) {
						if (k.matches("^[A-Za-z0-9_]+$") || k.equals("?"))
							arg = arg.replace("$" + k, v.toString());
						arg = arg.replace("${" + k + "}", v.toString());
					}
				}
				if (data == null)
					return false;
				for (String k : System.getenv().keySet()) {
					String v = System.getenv(k);
					arg = arg.replace("\\$", "**RP");
					if (k.isEmpty())
						continue;
					if (k.matches("^[A-Za-z0-9 _]+$"))
						arg = arg.replace("$" + k, v.toString());
					arg = arg.replace("${" + k + "}", v.toString());
				}
				arg = arg.replace("**RP", "$");
				arg = arg.replace("****RP", "**RP");
				cmd.command.set(i2++, arg);
			}
			if (i != 0 && commands.get(i - 1).nextOnlyIf) {
				if ((int) data.getOrDefault("?", 0) != 0)
					continue;
			}
			if (i != 0 && commands.get(i - 1).nextOnlyIfNot) {
				if ((int) data.getOrDefault("?", 0) == 0)
					continue;
			}
			if (!cmd.receivePipe && i < commands.size()) {
				stdIn = systemInput;
			} else {
				stdIn = new ShellInputStream(stdOut);
				stdIn.autoClose();
			}
			if (cmd.pipeToRight && i < commands.size()) {
				stdOut = new ShellOutputStream();
			} else {
				stdOut = systemOutput;
			}

			String command = cmd.command.get(0);
			if (command.startsWith("!JAVA")) {
				String name = nextLine.get();
				if (name.equals("!ENDJAVA"))
					continue;
				name = name.trim();
				if (name.startsWith("//"))
					name = name.substring(2);
				if (name.startsWith("#"))
					name = name.substring(1);
				name = name.trim();

				StringBuilder javaCode = new StringBuilder();
				while (true) {
					String line = nextLine.get();
					if (line == null) {
						if (stdIn != systemInput)
							stdIn.close();
						if (stdOut != systemOutput)
							stdOut.close();

						data.put("?", 1);
						return false;
					}
					if (line.trim().equals("!ENDJAVA"))
						break;
					javaCode.append(line).append("\n");
				}

				String pkg = "";
				String fullName = name;
				if (name.contains(".")) {
					pkg = name.substring(0, name.lastIndexOf("."));
					name = name.substring(name.lastIndexOf(".") + 1);
				}
				try {
					Class<?> cls = compileJavaCode(javaCode.toString(), name, pkg);
					generatedClasses.put(fullName, cls);
				} catch (IOException e) {
					stdOut.writeLine("Failed to compile java code: " + e.getClass() + ": " + e.getMessage());
					data.put("?", 1);
				}
			} else {
				ArrayList<String> args = new ArrayList<String>(cmd.command);
				String id = args.get(0);
				args.remove(0);

				boolean found = false;
				if (id.contains("=")) {
					String key = id.substring(0, id.indexOf("="));
					if (data != null)
						data.put("?", 0);

					String value = input.substring(input.indexOf("=") + 1);
					data.put(key, value);

					found = true;
				}

				if (!found) {
					for (SideTermCommand cmdInst : getCommands()) {
						if (cmdInst.id().equalsIgnoreCase(id) || (cmdInst.idIsRegex() && id.matches(cmdInst.id()))) {
							found = true;

							if (!checkCommand(cmdInst, args.size())) {
								invalidSyntax(cmdInst, stdOut, "");
							} else {
								cmdInst = cmdInst.newInstance();
								cmdInst.setup(stdOut, stdIn, this);

								if (cmdInst.idIsRegex()) {
									ArrayList<String> newArgs = new ArrayList<String>();
									newArgs.add(id);
									newArgs.addAll(args);
									args = newArgs;
								}

								boolean success = cmdInst.run(args.toArray(t -> new String[t]));
								if (!success && !cmd.nextOnlyIfNot && !cmdInst.idIsRegex()) {
									invalidSyntax(cmdInst, stdOut, "");
								} else if (!success && !cmd.nextOnlyIfNot) {
									stdOut.writeLine("Unrecognized command, use help for a list of known commands.");
								}

								if (stdIn != systemInput)
									stdIn.autoClose();
								if (stdOut != systemOutput)
									stdOut.autoClose();

								pipeFiles.forEach(t -> t.delete());
								if (data != null)
									data.put("?", success ? 0 : 1);
							}

							break;
						}
					}
				}

				if (!found) {
					try {
						Process proc = runProcess(cmd.command.toArray(t -> new String[t]), stdOut, stdIn);
						proc.waitFor();

						if (stdIn != systemInput)
							stdIn.autoClose();
						if (stdOut != systemOutput)
							stdOut.autoClose();

						pipeFiles.forEach(t -> t.delete());
						if (data != null)
							data.put("?", proc.exitValue());
					} catch (IOException | InterruptedException e) {
						stdOut.writeLine("Unrecognized command, use help for a list of known commands.");

						if (stdIn != systemInput)
							stdIn.autoClose();
						if (stdOut != systemOutput)
							stdOut.autoClose();

						pipeFiles.forEach(t -> t.delete());
						if (data != null)
							data.put("?", 1);
					}
				}
			}

			i++;
			if (stdOut != systemOutput)
				stdOut.autoClose();
		}

		if (stdIn != systemInput)
			stdIn.close();
		if (stdOut != systemOutput)
			stdOut.close();

		pipeFiles.forEach(t -> t.delete());

		if (data == null)
			return false;

		return (int) data.getOrDefault("?", 1) == 0;
	}

	public static boolean checkCommand(SideTermCommand command, int arguments) {
		if (arguments < command.minimalArguments()
				|| (command.maximalArguments() != -1 && arguments > command.maximalArguments()))
			return false;
		else
			return true;
	}

	public static void invalidSyntax(SideTermCommand command, ShellOutputStream output, String prefix) {
		output.writeLine(
				"Usage: " + prefix + command.id() + (command.syntax().isEmpty() ? "" : " " + command.syntax()));
		output.writeLine("------ Description: " + command.description());
	}

	/**
	 * Destroys this shell and all its child processes
	 */
	public void destroy() {
		if (data == null)
			return;
		for (Process proc : childProcesses.toArray(t -> new Process[t])) {
			proc.destroy();
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
			}
		}
		systemOutput.close();
		systemInput.close();
		childProcesses.clear();
		generatedClasses.clear();
		data.clear();
		data = null;
	}

}
