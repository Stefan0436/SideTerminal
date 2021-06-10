package org.asf.software.sideterminal.commands;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;

import org.asf.software.sideterminal.SideTermCommand;

public class NewCommand extends SideTermCommand {

	@Override
	protected SideTermCommand newInstance() {
		return new NewCommand();
	}

	@Override
	public int minimalArguments() {
		return 2;
	}

	@Override
	public int maximalArguments() {
		return -1;
	}

	@Override
	public String id() {
		return "new";
	}

	@Override
	public String syntax() {
		return "<type> <variable> [arguments]";
	}

	@Override
	public String description() {
		return "instantiates a new variable from a class type";
	}

	@Override
	public boolean run(String[] args) {
		String type = args[0];
		String var = args[1];

		if (!getShell().data.containsKey("PACKAGE") || !(getShell().data.get("PACKAGE") instanceof String)) {
			getShell().data.put("PACKAGE", "");
		}
		if (!type.contains("."))
			type = (getShell().data.get("PACKAGE").toString().isEmpty() ? "" : getShell().data.get("PACKAGE") + ".")
					+ type;

		Class<?> cls = getShell().generatedClasses.get(type);
		if (cls == null) {
			try {
				cls = Class.forName(type);
			} catch (ClassNotFoundException e) {
				Output.writeLine("Class not found exception was thrown: " + e.getMessage());
				Output.writeLine("");
				return false;
			}
		}
		ArrayList<Object> params = new ArrayList<Object>();

		Constructor<?> selectedCtor = null;
		for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
			if (JavaInvoke.isCompatibleMethod(getShell(), ctor, false, args.length - 2, "")) {
				selectedCtor = ctor;
				break;
			}
		}
		if (selectedCtor == null) {
			for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
				if (JavaInvoke.isCompatibleMethod(getShell(), ctor, true, args.length - 2, "")) {
					selectedCtor = ctor;
					break;
				}
			}
		}

		if (selectedCtor != null) {
			selectedCtor.setAccessible(true);
			params.addAll(JavaInvoke.parseParams(getShell(), Arrays.copyOfRange(args, 2, args.length), selectedCtor));
			try {
				getShell().data.put(var, selectedCtor.newInstance(params.toArray()));
				return true;
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				Output.writeLine("Exception was thrown: " + e.getClass().getTypeName() + ": " + e.getMessage());
				Output.writeLine("");
				return false;
			}
		}

		return false;
	}

}
