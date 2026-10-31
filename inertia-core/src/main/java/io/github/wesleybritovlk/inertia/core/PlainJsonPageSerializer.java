package io.github.wesleybritovlk.inertia.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dependency-free default {@link PageSerializer}.
 *
 * <p>Handles the value shapes Inertia props use in practice: primitives/boxes, strings,
 * enums, numbers, maps, iterables and arrays, records, POJOs via public getters,
 * {@link Optional}, {@code java.time} values (ISO-8601), {@link UUID}/{@link java.net.URI}
 * /{@link java.net.URL}, and dates. Cycle detection and a depth cap guard recursive values.</p>
 *
 * <p>With {@link PageKeys#PRESERVE_BIG_INTEGERS} set, integers outside the JavaScript
 * safe-integer range are wrapped as {@code {"$bigint": "..."}} so the client revives them
 * with BigInt. JSON escaping is root-template safe: the tag-sensitive code points are
 * emitted as unicode escapes (like Laravel's {@code JSON_HEX_TAG}).</p>
 */
public final class PlainJsonPageSerializer implements PageSerializer {

    private static final int MAX_DEPTH = 64;

    private static final BigInteger JS_SAFE_MAX = BigInteger.valueOf(2).pow(53).subtract(BigInteger.ONE);
    private static final BigInteger JS_SAFE_MIN = JS_SAFE_MAX.negate();
    private static final long JS_SAFE_MAX_LONG = JS_SAFE_MAX.longValueExact(); // 2^53 - 1

    private static final Map<Class<?>, List<Method>> RECORD_ACCESSORS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Method>> POJO_GETTERS = new ConcurrentHashMap<>();

    @Override
    public String serialize(Map<String, Object> page) {
        boolean bigIntegers = Boolean.TRUE.equals(page.get(PageKeys.PRESERVE_BIG_INTEGERS));
        StringBuilder out = new StringBuilder(512);
        writeValue(out, page, activeSet(), 0, bigIntegers);
        return out.toString();
    }

    private static Set<Object> activeSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void writeValue(StringBuilder out, Object value, Set<Object> active, int depth, boolean bigIntegers) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Inertia prop too deep (max " + MAX_DEPTH + " levels)");
        }
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Boolean b) {
            out.append(b);
            return;
        }
        if (value instanceof CharSequence s) {
            writeString(out, s.toString());
            return;
        }
        if (value instanceof Character c) {
            writeString(out, c.toString());
            return;
        }
        if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                out.append("null");
            } else {
                out.append(d);
            }
            return;
        }
        if (value instanceof Float f) {
            if (f.isNaN() || f.isInfinite()) {
                out.append("null");
            } else {
                out.append(f);
            }
            return;
        }
        if (value instanceof BigDecimal bd) {
            out.append(bd.toString());
            return;
        }
        if (value instanceof BigInteger bi) {
            writeBigInteger(out, bi, bigIntegers);
            return;
        }
        if (value instanceof Long l) {
            writeLong(out, l, bigIntegers);
            return;
        }
        if (value instanceof Number n) {
            out.append(n);
            return;
        }
        if (value instanceof Enum<?> e) {
            writeString(out, e.name());
            return;
        }
        if (value instanceof Optional<?> optional) {
            writeValue(out, optional.orElse(null), active, depth, bigIntegers);
            return;
        }
        if (value instanceof Temporal t) {
            writeString(out, t.toString());
            return;
        }
        if (value instanceof java.util.Date d) {
            writeString(out, d.toInstant().toString());
            return;
        }
        if (value instanceof java.util.Calendar c) {
            writeString(out, c.toInstant().toString());
            return;
        }
        if (value instanceof UUID || value instanceof java.net.URI || value instanceof java.net.URL
                || value instanceof java.nio.file.Path) {
            writeString(out, value.toString());
            return;
        }
        if (value instanceof Class<?> type) {
            writeString(out, type.getName());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeMap(out, map, active, depth, bigIntegers);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            writeCollection(out, iterable, active, depth, bigIntegers);
            return;
        }
        if (value instanceof Object[] array) {
            writeCollection(out, Arrays.asList(array), active, depth, bigIntegers);
            return;
        }
        if (value instanceof int[] array) {
            writeInts(out, array, active, depth, bigIntegers);
            return;
        }
        if (value instanceof long[] array) {
            writeLongs(out, array, active, depth, bigIntegers);
            return;
        }
        if (value instanceof double[] array) {
            writeDoubles(out, array, active, depth, bigIntegers);
            return;
        }
        if (value instanceof float[] array) {
            writeFloats(out, array, active, depth, bigIntegers);
            return;
        }
        if (value instanceof boolean[] array) {
            writeCollection(out, toList(array), active, depth, bigIntegers);
            return;
        }
        if (value instanceof short[] array) {
            writeCollection(out, toList(array), active, depth, bigIntegers);
            return;
        }
        if (value instanceof byte[] array) {
            writeCollection(out, toList(array), active, depth, bigIntegers);
            return;
        }
        if (value instanceof char[] array) {
            writeCollection(out, toList(array), active, depth, bigIntegers);
            return;
        }
        if (value instanceof Record record) {
            writeRecord(out, record, active, depth, bigIntegers);
            return;
        }
        if (value instanceof Supplier<?> supplier) {
            // defensive: suppliers should be resolved by the page resolver; resolve anyway
            writeValue(out, supplier.get(), active, depth, bigIntegers);
            return;
        }
        writePojo(out, value, active, depth, bigIntegers);
    }

    private static void writeBigInteger(StringBuilder out, BigInteger bi, boolean bigIntegers) {
        if (bigIntegers && (bi.compareTo(JS_SAFE_MAX) > 0 || bi.compareTo(JS_SAFE_MIN) < 0)) {
            out.append("{\"$bigint\":");
            writeString(out, bi.toString());
            out.append('}');
        } else {
            out.append(bi);
        }
    }

    private static void writeLong(StringBuilder out, long l, boolean bigIntegers) {
        if (bigIntegers && (l > JS_SAFE_MAX_LONG || l < -JS_SAFE_MAX_LONG)) {
            out.append("{\"$bigint\":");
            writeString(out, Long.toString(l));
            out.append('}');
        } else {
            out.append(l);
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map, Set<Object> active, int depth, boolean bigIntegers) {
        if (active.contains(map)) {
            throw new IllegalStateException("Inertia prop cycle detected: " + map.getClass().getName());
        }
        active.add(map);
        try {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue(), active, depth + 1, bigIntegers);
            }
            out.append('}');
        } finally {
            active.remove(map);
        }
    }

    private static void writeCollection(StringBuilder out, Iterable<?> iterable, Set<Object> active, int depth, boolean bigIntegers) {
        if (active.contains(iterable)) {
            throw new IllegalStateException("Inertia prop cycle detected: " + iterable.getClass().getName());
        }
        active.add(iterable);
        try {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item, active, depth + 1, bigIntegers);
            }
            out.append(']');
        } finally {
            active.remove(iterable);
        }
    }

    private static void writeInts(StringBuilder out, int[] array, Set<Object> active, int depth, boolean bigIntegers) {
        out.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(out, array[i], active, depth, bigIntegers);
        }
        out.append(']');
    }

    private static void writeLongs(StringBuilder out, long[] array, Set<Object> active, int depth, boolean bigIntegers) {
        out.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(out, array[i], active, depth, bigIntegers);
        }
        out.append(']');
    }

    private static void writeDoubles(StringBuilder out, double[] array, Set<Object> active, int depth, boolean bigIntegers) {
        out.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(out, array[i], active, depth, bigIntegers);
        }
        out.append(']');
    }

    private static void writeFloats(StringBuilder out, float[] array, Set<Object> active, int depth, boolean bigIntegers) {
        out.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(out, array[i], active, depth, bigIntegers);
        }
        out.append(']');
    }

    private static void writeRecord(StringBuilder out, Record record, Set<Object> active, int depth, boolean bigIntegers) {
        if (active.contains(record)) {
            throw new IllegalStateException("Inertia prop cycle detected: " + record.getClass().getName());
        }
        active.add(record);
        try {
            out.append('{');
            boolean first = true;
            for (Method accessor : recordAccessors(record.getClass())) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, accessor.getName());
                out.append(':');
                writeValue(out, invoke(accessor, record), active, depth + 1, bigIntegers);
            }
            out.append('}');
        } finally {
            active.remove(record);
        }
    }

    private static void writePojo(StringBuilder out, Object bean, Set<Object> active, int depth, boolean bigIntegers) {
        List<Method> getters = pojoGetters(bean.getClass());
        if (getters.isEmpty()) {
            writeString(out, String.valueOf(bean));
            return;
        }
        if (active.contains(bean)) {
            throw new IllegalStateException("Inertia prop cycle detected: " + bean.getClass().getName());
        }
        active.add(bean);
        try {
            out.append('{');
            boolean first = true;
            for (Method getter : getters) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, propertyName(getter.getName()));
                out.append(':');
                writeValue(out, invoke(getter, bean), active, depth + 1, bigIntegers);
            }
            out.append('}');
        } finally {
            active.remove(bean);
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003C");
                case '>' -> out.append("\\u003E");
                case '\u2028' -> out.append("\\u2028");
                case '\u2029' -> out.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u").append(String.format("%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static List<Method> recordAccessors(Class<?> type) {
        return RECORD_ACCESSORS.computeIfAbsent(type, t -> {
            RecordComponent[] components = t.getRecordComponents();
            List<Method> accessors = new ArrayList<>(components.length);
            for (RecordComponent component : components) {
                try {
                    accessors.add(t.getMethod(component.getName()));
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException("Unreadable record component: " + t.getName() + "." + component.getName(), e);
                }
            }
            return accessors;
        });
    }

    private static List<Method> pojoGetters(Class<?> type) {
        return POJO_GETTERS.computeIfAbsent(type, t -> {
            List<Method> getters = new ArrayList<>();
            for (Method m : t.getMethods()) {
                if (m.getParameterCount() != 0 || m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                String name = m.getName();
                if ("getClass".equals(name)) {
                    continue;
                }
                boolean is = name.startsWith("is");
                boolean get = name.startsWith("get");
                if (!get && !is) {
                    continue;
                }
                if (name.length() <= (get ? 3 : 2)) {
                    continue;
                }
                if (void.class == m.getReturnType()) {
                    continue;
                }
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                getters.add(m);
            }
            getters.sort(Comparator.comparing(Method::getName));
            return getters;
        });
    }

    private static String propertyName(String getterName) {
        String raw = getterName.startsWith("get") ? getterName.substring(3) : getterName.substring(2);
        if (raw.isEmpty()) {
            return getterName;
        }
        if (raw.length() > 1 && Character.isUpperCase(raw.charAt(1))) {
            return raw;
        }
        return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
    }

    private static Object invoke(Method accessor, Object target) {
        try {
            accessor.setAccessible(true);
            return accessor.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Inertia prop serialization failed for " + target.getClass(), e);
        }
    }

    private static List<Object> toList(boolean[] a) {
        List<Object> out = new ArrayList<>(a.length);
        for (boolean v : a) {
            out.add(v);
        }
        return out;
    }

    private static List<Object> toList(short[] a) {
        List<Object> out = new ArrayList<>(a.length);
        for (short v : a) {
            out.add(v);
        }
        return out;
    }

    private static List<Object> toList(byte[] a) {
        List<Object> out = new ArrayList<>(a.length);
        for (byte v : a) {
            out.add(v);
        }
        return out;
    }

    private static List<Object> toList(char[] a) {
        List<Object> out = new ArrayList<>(a.length);
        for (char v : a) {
            out.add(v);
        }
        return out;
    }
}
