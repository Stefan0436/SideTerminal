package org.asf.software.sideterminal.commands;

import java.util.HashMap;

import org.asf.software.sideterminal.SideTermCommand;

public class VarsCommand extends SideTermCommand {

	@Override
	protected SideTermCommand newInstance() {
		return new VarsCommand();
	}

	@Override
	public int minimalArguments() {
		return 0;
	}

	@Override
	public String id() {
		return "vars";
	}

	@Override
	public String description() {
		return "displays a list of defined variables";
	}

	@Override
	public boolean run(String[] args) {
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.putAll(System.getenv());
		getShell().data.forEach((k, v) -> {
			if (v instanceof String || JavaInvoke.PRIMITIVES.containsKey(v.getClass())
					|| JavaInvoke.PRIMITIVES.containsValue(v.getClass()))
				vars.put(k, v.toString());
			else
				vars.put(k, "<java object>");
		});
		Output.writeLine("Variables currently defined:");
		vars.forEach((k, v) -> Output.writeLine(" - " + k + " = " + v));
		return true;
	}

}
