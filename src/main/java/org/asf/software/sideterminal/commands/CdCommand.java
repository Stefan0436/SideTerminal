package org.asf.software.sideterminal.commands;

import java.io.File;

import org.asf.software.sideterminal.SideTermCommand;

public class CdCommand extends SideTermCommand {

	@Override
	protected SideTermCommand newInstance() {
		return new CdCommand();
	}

	@Override
	public int minimalArguments() {
		return 1;
	}

	@Override
	public String id() {
		return "cd";
	}

	@Override
	public String syntax() {
		return "<directory>";
	}

	@Override
	public String description() {
		return "changes the current working directory";
	}

	@Override
	public boolean run(String[] args) {
		File pwd = new File(args[0]);
		if (!pwd.isAbsolute())
			pwd = new File(getShell().pwd, args[0]);
		if (pwd.exists() && pwd.isDirectory()) {
			getShell().pwd = pwd;
			return true;
		}
		return false;
	}

}
