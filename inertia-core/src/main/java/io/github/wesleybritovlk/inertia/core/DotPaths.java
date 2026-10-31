package io.github.wesleybritovlk.inertia.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dot-path handling: Inertia props chain with dot keys ({@code "auth.user"}), each segment
 * defining a nesting level, and partial reload matching works on those dotted paths &mdash;
 * exactly like Laravel's {@code Arr::set/unset} plus {@code PropsResolver} path matching.
 */
final class DotPaths {

    private DotPaths() {
    }

    /** Expands dotted top-level keys into nested maps: {@code "auth.user" &rarr; {auth: {user: value}}}. */
    static LinkedHashMap<String, Object> nest(Map<String, Prop> props) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Prop> entry : props.entrySet()) {
            insert(root, segments(entry.getKey()), entry.getValue());
        }
        return root;
    }

    /** Rebuilds nested maps from filtered {@code path -> value} entries (user-visible shape of props). */
    static LinkedHashMap<String, Object> unflatten(Map<String, Object> filtered) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : filtered.entrySet()) {
            insert(root, segments(entry.getKey()), entry.getValue());
        }
        return root;
    }

    /** Flattens dot-keyNested structures back to {@code path -> Prop}. User values are opaque leaves. */
    static LinkedHashMap<String, Prop> flatten(Map<String, Object> nested) {
        LinkedHashMap<String, Prop> out = new LinkedHashMap<>();
        walk(nested, null, out);
        return out;
    }

    /**
     * Partial reload matching: a {@code candidate} matches when it equals the path, or when
     * either one descends (dot-wise) from the other. {@code except} wins over {@code only},
     * and an empty {@code only} list means all props.
     */
    static boolean matches(String path, List<String> only, List<String> except) {
        if (!except.isEmpty() && matchesAny(path, except)) {
            return false;
        }
        if (only.isEmpty()) {
            return true;
        }
        return matchesAny(path, only);
    }

    /** True when any reset entry equals the path or is an ancestor of it (merge state reset). */
    static boolean isReset(String path, List<String> reset) {
        return matchesAny(path, reset);
    }

    static List<String> segments(String dottedKey) {
        String[] parts = dottedKey.contains(".") ? dottedKey.split("\\.", -1) : new String[] {dottedKey};
        return Arrays.asList(parts);
    }

    @SuppressWarnings("unchecked")
    private static void insert(LinkedHashMap<String, Object> branch, List<String> segments, Object leaf) {
        int size = segments.size();
        String head = segments.get(0);
        if (size == 1) {
            branch.put(head, leaf);
            return;
        }
        Object existing = branch.get(head);
        LinkedHashMap<String, Object> child = existing instanceof LinkedHashMap<?, ?> map
                ? (LinkedHashMap<String, Object>) map
                : new LinkedHashMap<>();
        branch.put(head, child);
        insert(child, segments.subList(1, size), leaf);
    }

    private static void walk(Map<String, Object> nested, String prefix, LinkedHashMap<String, Prop> out) {
        for (Map.Entry<String, Object> entry : nested.entrySet()) {
            String path = prefix == null ? entry.getKey() : prefix + '.' + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> map) {
                walk((Map<String, Object>) castTypeless(map), path, out);
            } else {
                out.put(path, (Prop) entry.getValue());
            }
        }
    }

    private static Map<?, ?> castTypeless(Object map) {
        return (Map<?, ?>) map;
    }

    private static boolean matchesAny(String path, List<String> targets) {
        for (String target : targets) {
            if (path.equals(target) || path.startsWith(target + '.') || target.startsWith(path + '.')) {
                return true;
            }
        }
        return false;
    }
}
