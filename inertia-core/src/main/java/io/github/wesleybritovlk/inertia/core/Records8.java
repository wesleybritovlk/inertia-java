package io.github.wesleybritovlk.inertia.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Release-8 window for user-side records: {@code java.lang.Record} and its component API
 * are runtime 14+/16+ features a release-8 compilation cannot reference, so the default
 * serializer probes them by reflection. On the modern runtimes (the JDK 17/21 the tests
 * and native-image work on) user records still serialize as first-class objects through
 * their component accessors; on a bare Java 8 runtime no value can be a record and the
 * probe answers {@code null} without any reflective lookup, falling through to POJO
 * getters exactly like the legacy line.
 */
final class Records8 {

    private static final Class<?> RECORD_CLASS = resolve("java.lang.Record");
    private static final Map<Class<?>, List<Method>> ACCESSORS = new ConcurrentHashMap<>();

    private Records8() {
    }

    /**
     * Record component accessors in declaration order, or {@code null} when {@code type}
     * is not a record on this runtime.
     */
    static List<Method> accessors(Class<?> type) {
        if (RECORD_CLASS == null || !RECORD_CLASS.isAssignableFrom(type)) {
            return null;
        }
        return ACCESSORS.computeIfAbsent(type, Records8::resolveAccessors);
    }

    private static List<Method> resolveAccessors(Class<?> type) {
        Object[] components = invokeComponents(type);
        List<Method> accessors = new ArrayList<>(components.length);
        for (Object component : components) {
            String name = componentName(type, component);
            try {
                accessors.add(type.getMethod(name));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Unreadable record component: " + type.getName() + "." + name, e);
            }
        }
        return accessors;
    }

    /** Invokes {@code Class.getRecordComponents} without naming the runtime-only API. */
    private static Object[] invokeComponents(Class<?> type) {
        try {
            return (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Inertia record component lookup failed for " + type.getName(), e);
        }
    }

    /** Reads {@code RecordComponent.getName()} without naming the runtime-only class. */
    private static String componentName(Class<?> type, Object component) {
        try {
            return (String) component.getClass().getMethod("getName").invoke(component);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Inertia record component name lookup failed for " + type.getName(), e);
        }
    }

    private static Class<?> resolve(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException absent) {
            return null; // bare Java 8 runtime: nothing can be a record
        }
    }

    private static Method resolveComponentsFetcher() {
        try {
            return Class.class.getMethod("getRecordComponents");
        } catch (ReflectiveOperationException absent) {
            return null; // pre-16 runtime that somehow ships Record
        }
    }
}
