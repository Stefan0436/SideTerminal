package org.asf.software.sideterminal.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.asf.software.sideterminal.SideTermCommand;
import org.asf.software.sideterminal.SideTermShell;

public class JavaInvoke extends SideTermCommand {

	static final Map<Class<?>, Class<?>> PRIMITIVES = Map.of(byte.class, Byte.class, short.class, Short.class,
			int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class,
			boolean.class, Boolean.class, char.class, Character.class);

	static final Map<String, Class<?>> PRIMITIVES_BY_NAME;
	static {
		PRIMITIVES_BY_NAME = new HashMap<String, Class<?>>();
		PRIMITIVES.forEach((k, v) -> PRIMITIVES_BY_NAME.put(k.getTypeName(), v));
	}

	@Override
	protected SideTermCommand newInstance() {
		return new JavaInvoke();
	}

	@Override
	public int minimalArguments() {
		return 0;
	}

	@Override
	public int maximalArguments() {
		return -1;
	}

	@Override
	public String id() {
		return "^[A-Z-a-z0-9$_\\.]+\\.[A-Z-a-z0-9$_]+";
	}

	@Override
	public boolean idIsRegex() {
		return true;
	}

	@Override
	public String syntax() {
		return "[arguments]";
	}

	@Override
	public String description() {
		return "runs the given method (reflective)";
	}

	@Override
	public boolean run(String[] args) {
		if (!getShell().data.containsKey("PACKAGE") || !(getShell().data.get("PACKAGE") instanceof String)) {
			getShell().data.put("PACKAGE", "");
		}

		String name = args[0];
		String methName = name.substring(name.lastIndexOf(".") + 1);
		name = name.substring(0, name.lastIndexOf("."));

		Class<?> cls = null;
		Method selected = null;
		Object accessor = null;

		if (getShell().data.containsKey(name)) {
			accessor = getShell().data.get(name);
			cls = accessor.getClass();
		} else {
			String pkg = getShell().data.get("PACKAGE").toString();
			if (name.contains(".")) {
				pkg = name.substring(0, name.lastIndexOf("."));
				name = name.substring(name.lastIndexOf(".") + 1);
			}
			name = (!pkg.isEmpty() ? pkg + "." : "") + name;

			try {
				cls = getShell().generatedClasses.get(name);
				if (cls == null)
					cls = Class.forName(name);
			} catch (ClassNotFoundException e) {
				return false;
			}
		}

		for (Method mth : cls.getDeclaredMethods()) {
			if (JavaInvoke.isCompatibleMethod(getShell(), mth, false, args.length - 1, methName)) {
				selected = mth;
				break;
			}
		}
		if (selected == null) {
			for (Method mth : cls.getDeclaredMethods()) {
				if (JavaInvoke.isCompatibleMethod(getShell(), mth, true, args.length - 1, methName)) {
					selected = mth;
					break;
				}
			}
		}
		if (selected == null) {
			return false;
		}

		selected.setAccessible(true);
		ArrayList<Object> params = JavaInvoke.parseParams(getShell(), Arrays.copyOfRange(args, 1, args.length),
				selected);
		try {
			Object result = selected.invoke(accessor, params.toArray());
			if (result != null)
				Output.writeLine(objectToString(result));
			return true;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			Output.writeLine("Exception was thrown: " + e.getClass().getTypeName() + ": " + e.getMessage());
			Output.writeLine("");
			return false;
		}
	}

	private String objectToString(Object object) {
		if (object instanceof String || PRIMITIVES.containsKey(object.getClass())
				|| PRIMITIVES.containsValue(object.getClass())) {
			if (object instanceof String)
				return "\"" + object.toString() + "\"";
			else
				return object.toString();
		} else {
			if (object.getClass().isArray() || object instanceof Iterable) {
				ArrayList<String> results = new ArrayList<String>();
				if (object.getClass().isArray()) {
					for (Object data : Arrays.asList((Object[]) object)) {
						results.add(objectToString(data));
					}
				} else if (object instanceof Iterable) {
					for (Object data : (Iterable<?>) object) {
						results.add(objectToString(data));
					}
				}
				StringBuilder builder = new StringBuilder();
				builder.append("Java Array: [");
				for (String result : results) {
					builder.append(" ").append(result);
				}
				builder.append("]");
				return builder.toString();
			} else if (Map.class.isAssignableFrom(object.getClass())) {
				HashMap<String, String> results = new HashMap<String, String>();
				((Map<?, ?>) object).forEach((k, v) -> {
					results.put(objectToString(k), objectToString(v));
				});
				StringBuilder builder = new StringBuilder();
				builder.append("Java Map: {");
				results.forEach((k, v) -> {
					builder.append("\n").append("    ").append(k).append("=").append(v);
				});
				builder.append("\n}");
				return builder.toString();
			} else {
				if (object instanceof Serializable) {
					try {
						ByteArrayOutputStream strm = new ByteArrayOutputStream();
						ObjectOutputStream serializer = new ObjectOutputStream(strm);
						serializer.writeObject(object);
						serializer.close();
						strm.close();
						return "Base64: " + new String(Base64.getEncoder().encode(strm.toByteArray()));
					} catch (IOException e) {
					}
				}
				return "<java object>";
			}
		}
	}

	public static Object getPrimitiveWrapper(Class<?> input, String str) {
		try {
			return input.getMethod("valueOf", String.class).invoke(null, str);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			return null;
		}
	}

	public static boolean isCompatibleMethod(SideTermShell sideTermShell, Executable meth, boolean allowVarArgs,
			int count, String name) {
		if (meth instanceof Method && !meth.getName().equalsIgnoreCase(name))
			return false;

		int paramcount = 0;
		boolean comntainsVarArgs = false;

		int index = 0;
		for (Parameter param : meth.getParameters()) {
			if ((param.isVarArgs() || (index == meth.getParameterCount() - 1 && param.getType().isArray()))
					&& !allowVarArgs)
				return false;

			if (!param.getType().isAssignableFrom(String.class)
					&& !param.getType().isAssignableFrom(sideTermShell.getClass())
					&& !PRIMITIVES.values().stream().anyMatch(t -> param.getType().isAssignableFrom(t))
					&& !PRIMITIVES.keySet().stream().anyMatch(t -> param.getType().isAssignableFrom(t))
					&& !(param.isVarArgs() || (index == meth.getParameterCount() - 1 && param.getType().isArray())))
				return false;
			else if ((param.isVarArgs() || (index == meth.getParameterCount() - 1 && param.getType().isArray()))) {
				if (!param.getType().getComponentType().isAssignableFrom(String.class)
						&& !param.getType().getComponentType().isAssignableFrom(sideTermShell.getClass())
						&& !PRIMITIVES.values().stream()
								.anyMatch(t -> param.getType().getComponentType().isAssignableFrom(t))
						&& !PRIMITIVES.keySet().stream()
								.anyMatch(t -> param.getType().getComponentType().isAssignableFrom(t)))
					return false;
			}

			if (!param.getType().isAssignableFrom(SideTermShell.class))
				paramcount++;

			if (param.isVarArgs() || (index == meth.getParameterCount() - 1 && param.getType().isArray()))
				comntainsVarArgs = true;
			index++;
		}

		if (paramcount == count)
			return true;
		else if (paramcount < count && allowVarArgs && comntainsVarArgs)
			return true;
		else
			return false;
	}

	public static ArrayList<Object> parseParams(SideTermShell sideTermShell, String[] args, Executable mth) {
		ArrayList<Object> params = new ArrayList<Object>();
		int i = 0;
		int index = 0;
		for (Parameter param : mth.getParameters()) {
			if (param.isVarArgs() || (index == mth.getParameterCount() - 1 && param.getType().isArray())) {
				int i3 = 0;
				Object arr = Array.newInstance(param.getType().getComponentType(), args.length - i);
				while (true) {
					if (param.getType().getComponentType().isAssignableFrom(sideTermShell.getClass())) {
						Array.set(arr, i3++, sideTermShell);
					} else {
						if (i >= args.length)
							break;
						if (param.getType().getComponentType().isAssignableFrom(String.class))
							Array.set(arr, i3++, args[i++]);
						else
							try {
								Class<?> primitive = PRIMITIVES_BY_NAME
										.get(param.getType().getComponentType().getTypeName());
								if (primitive == null)
									primitive = Class.forName(param.getType().getComponentType().getTypeName());
								Array.set(arr, i3++, getPrimitiveWrapper(primitive, args[i++]));
							} catch (ClassNotFoundException e) {
								return null;
							}
					}
				}
				params.add(arr);
			} else {
				if (param.getType().isAssignableFrom(sideTermShell.getClass())) {
					params.add(sideTermShell);
				} else {
					if (param.getType().isAssignableFrom(String.class))
						params.add(args[i++]);
					else
						try {
							Class<?> primitive = PRIMITIVES_BY_NAME.get(param.getType().getTypeName());
							if (primitive == null)
								primitive = Class.forName(param.getType().getTypeName());
							params.add(getPrimitiveWrapper(primitive, args[i++]));
						} catch (ClassNotFoundException e) {
							return null;
						}
				}
			}
			index++;
		}
		return params;
	}

}
