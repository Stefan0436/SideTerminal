package org.asf.software.sideterminal;

/**
 * 
 * SideTerminal Shell Command Abstract
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
public abstract class SideTermCommand {
	protected ShellInputStream Input;
	protected ShellOutputStream Output;
	private SideTermShell shell;

	protected abstract SideTermCommand newInstance();

	void setup(ShellOutputStream output, ShellInputStream input, SideTermShell shell) {
		Output = output;
		Input = input;
		this.shell = shell;
	}

	/**
	 * Defines whether or not the id is a regex string
	 */
	public boolean idIsRegex() {
		return false;
	}
	
	/**
	 * Retrieves the shell instance.
	 */
	protected SideTermShell getShell() {
		return shell;
	}

	/**
	 * Defines the maximal amount of arguments (uses minimal by default)
	 */
	public int maximalArguments() {
		return minimalArguments();
	}

	/**
	 * Defines the minimal
	 */
	public abstract int minimalArguments();

	/**
	 * Defines the command id
	 */
	public abstract String id();

	/**
	 * Defines the command syntax message
	 */
	public String syntax() {
		return "";
	}

	/**
	 * Defines the command description
	 */
	public abstract String description();

	/**
	 * Runs the command
	 * 
	 * @param args Command arguments
	 * @return True if successful, false otherwise
	 */
	public abstract boolean run(String[] args);
}
