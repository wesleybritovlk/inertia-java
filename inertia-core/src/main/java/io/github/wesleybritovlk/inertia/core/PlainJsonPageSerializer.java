package io.github.wesleybritovlk.inertia.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * /{@link java.net.URL}, and dates. Cycle detection and a depth cap guard recursive values.
 * User-side records are recognized through the {@link Records8} reflection gate, so they
 * keep serializing on modern runtimes while the core rides release 8.</p>
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
        if (value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof CharSequence) {
            writeString(out, value.toString());
            return;
        }
        if (value instanceof Character) {
            writeString(out, value.toString());
            return;
        }
        if (value instanceof Double) {
            Double d = (Double) value;
            if (d.isNaN() || d.isInfinite()) {
                out.append("null");
            } else {
                out.append(d);
            }
            return;
        }
        if (value instanceof Float) {
            Float f = (Float) value;
            if (f.isNaN() || f.isInfinite()) {
                out.append("null");
            } else {
                out.append(f);
            }
            return;
        }
        if (value instanceof BigDecimal) {
            out.append(value);
            return;
        }
        if (value instanceof BigInteger) {
            writeBigInteger(out, (BigInteger) value, bigIntegers);
            return;
        }
        if (value instanceof Long) {
            writeLong(out, (Long) value, bigIntegers);
            return;
        }
        if (value instanceof Number) {
            out.append(value);
            return;
        }
        if (value instanceof Enum<?>) {
            writeString(out, ((Enum<?>) value).name());
            return;
        }
        if (value instanceof Optional<?>) {
            writeValue(out, ((Optional<?>) value).orElse(null), active, depth, bigIntegers);
            return;
        }
        if (value instanceof Temporal) {
            writeString(out, ((Temporal) value).toString());
            return;
        }
        if (value instanceof java.util.Date) {
            writeString(out, ((java.util.Date) value).toInstant().toString());
            return;
        }
        if (value instanceof java.util.Calendar) {
            writeString(out, ((java.util.Calendar) value).toInstant().toString());
            return;
        }
        if (value instanceof UUID || value instanceof java.net.URI || value instanceof java.net.URL
                || value instanceof java.nio.file.Path) {
            writeString(out, value.toString());
            return;
        }
        if (value instanceof Class<?>) {
            writeString(out, ((Class<?>) value).getName());
            return;
        }
        if (value instanceof Map<?, ?>) {
            writeMap(out, (Map<?, ?>) value, active, depth, bigIntegers);
            return;
        }
        if (value instanceof Iterable<?>) {
            writeCollection(out, (Iterable<?>) value, active, depth, bigIntegers);
            return;
        }
        if (value instanceof Object[]) {
            writeCollection(out, Arrays.asList((Object[]) value), active, depth, bigIntegers);
            return;
        }
        if (value instanceof int[]) {
            writeInts(out, (int[]) value, active, depth, bigIntegers);
            return;
        }
        if (value instanceof long[]) {
            writeLongs(out, (long[]) value, active, depth, bigIntegers);
            return;
        }
        if (value instanceof double[]) {
            writeDoubles(out, (double[]) value, active, depth, bigIntegers);
            return;
        }
        if (value instanceof float[]) {
            writeFloats(out, (float[]) value, active, depth, bigIntegers);
            return;
        }
        if (value instanceof boolean[]) {
            writeCollection(out, toList((boolean[]) value), active, depth, bigIntegers);
            return;
        }
        if (value instanceof short[]) {
            writeCollection(out, toList((short[]) value), active, depth, bigIntegers);
            return;
        }
        if (value instanceof byte[]) {
            writeCollection(out, toList((byte[]) value), active, depth, bigIntegers);
            return;
        }
        if (value instanceof char[]) {
            writeCollection(out, toList((char[]) value), active, depth, bigIntegers);
            return;
        }
        List<Method> recordAccessors = Records8.accessors(value.getClass());
        if (recordAccessors != null) {
            writeRecord(out, value, recordAccessors, active, depth, bigIntegers);
            return;
        }
        if (value instanceof Supplier<?>) {
            // defensive: suppliers should be resolved by the page resolver; resolve anyway
            writeValue(out, ((Supplier<?>) value).get(), active, depth, bigIntegers);
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

    private static void writeRecord(StringBuilder out, Object record, List<Method> accessors, Set<Object> active, int depth, boolean bigIntegers) {
        if (active.contains(record)) {
            throw new IllegalStateException("Inertia prop cycle detected: " + record.getClass().getName());
        }
        active.add(record);
        try {
            out.append('{');
            boolean first = true;
            for (Method accessor : accessors) {
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
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '<':
                    out.append("\\u003C");
                    break;
                case '>':
                    out.append("\\u003E");
                    break;
                case '\u2028':
                    out.append("\\u2028");
                    break;
                case '\u2029':
                    out.append("\\u2029");
                    break;
                default:
                    if (c < 0x20) {
                        out.append("\\u").append(String.format("%04X", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
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
