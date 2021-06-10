package org.asf.software.sideterminal.commands;

import org.asf.software.sideterminal.SideTermCommand;

public class HelpCommand extends SideTermCommand {

	@Override
	public int maximalArguments() {
		return -1;
	}

	@Override
	public int minimalArguments() {
		return 0;
	}

	@Override
	public String id() {
		return "help";
	}

	@Override
	public String description() {
		return "displays all known commands";
	}

	@Override
	public boolean run(String[] args) {
		Output.writeLine("");
		Output.writeLine("List of known commands:");
		for (SideTermCommand cmd : getShell().getCommands()) {
			if (!cmd.idIsRegex())
				Output.writeLine(" - " + cmd.id() + (cmd.syntax().isEmpty() ? "" : " " + cmd.syntax()) + " - "
						+ cmd.description());
		}
		Output.writeLine("");
		Output.writeLine("");
		Output.writeLine("The Dragonfly Shell can also invoke java methods:");
		Output.writeLine("Example: java.lang.System.getProperty \"java.class.path\"");
		Output.writeLine("");
		Output.writeLine("Another example:");
		Output.writeLine("# Instantiate a new string, save it to the 'example' variable:");
		Output.writeLine("new java.lang.String example 72 101 108 108 111 32 87 111 114 108 100");
		Output.writeLine("");
		Output.writeLine("# Write the output to the console");
		Output.writeLine("example.toString");
		Output.writeLine("");
		Output.writeLine("");
		Output.writeLine("All system commands are also available.");
		Output.writeLine("The Dragonfly Shell reads the PATH variable for this.");
		Output.writeLine("");
		Output.writeLine("");
		Output.writeLine("Setting variables:");
		Output.writeLine("Format: key=value");
		Output.writeLine("");
		Output.writeLine("Example:");
		Output.writeLine("EXAMPLE=\"Hello World\"");
		return true;
	}

	@Override
	protected SideTermCommand newInstance() {
		return new HelpCommand();
	}

}
