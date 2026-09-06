```java
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.*;

public class CommandLine {

    /**
     * Construct an arbitrary Java record from command-line arguments.
     *
     * Record component names become option names:
     *
     *   record Options(String input, int width, boolean verbose) {}
     *
     * accepts:
     *
     *   --input file.v --width 32 --verbose
     *
     * Supported component types:
     *   String
     *   int, long, double, float
     *   boolean
     *   short, byte
     *   char
     *   enums
     *
     * Boolean options do not require a value.
     */
    public static <T extends Record> T parse(Class<T> type, String[] args) {
        if (!type.isRecord())
            throw new IllegalArgumentException(
                type.getName() + " is not a record");

        Map<String, String> options = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (!arg.startsWith("--"))
                throw new IllegalArgumentException(
                    "Expected option beginning with '--': " + arg);

            String name = arg.substring(2);

            RecordComponent component = findComponent(type, name);

            if (component == null)
                throw new IllegalArgumentException(
                    "Unknown option: --" + name);

            Class<?> componentType = component.getType();

            if (componentType == boolean.class ||
                componentType == Boolean.class) {

                options.put(name, "true");
            }
            else {
                if (++i >= args.length)
                    throw new IllegalArgumentException(
                        "Missing value for --" + name);

                options.put(name, args[i]);
            }
        }

        try {
            RecordComponent[] components = type.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] parameterTypes = new Class<?>[components.length];

            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];

                parameterTypes[i] = component.getType();

                String value = options.get(component.getName());

                if (value == null) {
                    throw new IllegalArgumentException(
                        "Missing required option: --" +
                        component.getName());
                }

                values[i] = convert(component.getType(), value);
            }

            Constructor<T> constructor =
                type.getDeclaredConstructor(parameterTypes);

            return constructor.newInstance(values);

        }
        catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                "Cannot construct " + type.getName(), e);
        }
    }


    private static RecordComponent findComponent(
        Class<?> type, String name) {

        for (RecordComponent c : type.getRecordComponents()) {
            if (c.getName().equals(name))
                return c;
        }

        return null;
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(
        Class<?> type, String value) {

        if (type == String.class)
            return value;

        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);

        if (type == long.class || type == Long.class)
            return Long.parseLong(value);

        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);

        if (type == float.class || type == Float.class)
            return Float.parseFloat(value);

        if (type == short.class || type == Short.class)
            return Short.parseShort(value);

        if (type == byte.class || type == Byte.class)
            return Byte.parseByte(value);

        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);

        if (type == char.class || type == Character.class) {
            if (value.length() != 1)
                throw new IllegalArgumentException(
                    "Expected one character: " + value);

            return value.charAt(0);
        }

        if (type.isEnum())
            return Enum.valueOf((Class<? extends Enum>) type, value);

        throw new IllegalArgumentException(
            "Unsupported option type: " + type.getName());
    }
}
```
