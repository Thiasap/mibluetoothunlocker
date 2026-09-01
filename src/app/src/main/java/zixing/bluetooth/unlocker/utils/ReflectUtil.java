package zixing.bluetooth.unlocker.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectUtil {

    private ReflectUtil() {
    }

    public static Class<?> findClass(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(e);
        }
    }

    public static Class<?> findClassIfExists(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + name);
    }

    public static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + name);
    }

    public static Field getFieldContainingName(Class<?> clazz, String fieldName) {
        String lowerFieldName = fieldName.toLowerCase();
        Class<?> current = clazz;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().toLowerCase().contains(lowerFieldName)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException("Failed to get field: " + fieldName,
                new NoSuchFieldException("No field containing '" + fieldName + "' found"));
    }

    public static int getStaticIntField(Class<?> clazz, String name) {
        try {
            return findField(clazz, name).getInt(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object callMethod(Object instance, String name, Object... args) {
        try {
            Method method = findBestMethod(instance.getClass(), name, args);
            method.setAccessible(true);
            return method.invoke(instance, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        try {
            Constructor<?> constructor = findBestConstructor(clazz, args);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findBestMethod(Class<?> clazz, String name, Object[] args) {
        Class<?> current = clazz;
        while (current != null) {
            Method match = null;
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (isCompatible(method.getParameterTypes(), args)) {
                    match = method;
                    break;
                }
            }
            if (match != null) {
                return match;
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + name);
    }

    private static Constructor<?> findBestConstructor(Class<?> clazz, Object[] args) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (isCompatible(constructor.getParameterTypes(), args)) {
                return constructor;
            }
        }
        throw new NoSuchMethodError(clazz.getName() + ".<init>");
    }

    private static boolean isCompatible(Class<?>[] types, Object[] args) {
        if (types.length != args.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            Object arg = args[i];
            Class<?> type = box(types[i]);
            if (arg == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!type.isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

    public static final class ClassNotFoundError extends Error {
        public ClassNotFoundError(Throwable cause) {
            super(cause);
        }
    }
}
