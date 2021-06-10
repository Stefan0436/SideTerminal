package org.asf.software.sideterminal.commands;

import org.asf.software.sideterminal.SideTermCommand;

public class UnsetCommand extends SideTermCommand {

	@Override
	protected SideTermCommand newInstance() {
		return new UnsetCommand();
	}

	@Override
	public int minimalArguments() {
		return 1;
	}

	@Override
	public String id() {
		return "unset";
	}

	@Override
	public String syntax() {
		return "<variable>";
	}

	@Override
	public String description() {
		return "unsets the given variable (removes it)";
	}

	@Override
	public boolean run(String[] args) {
		getShell().data.remove(args[0]);
		return true;
	}

}
