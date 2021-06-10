package org.asf.software.sideterminal.commands;

import org.asf.software.sideterminal.SideTermCommand;

public class ExitCommand extends SideTermCommand {

	@Override
	protected SideTermCommand newInstance() {
		return new ExitCommand();
	}

	@Override
	public int minimalArguments() {
		return 0;
	}

	@Override
	public String id() {
		return "exit";
	}

	@Override
	public String description() {
		return "closes the shell";
	}

	@Override
	public boolean run(String[] args) {
		getShell().destroy();
		return true;
	}

}
