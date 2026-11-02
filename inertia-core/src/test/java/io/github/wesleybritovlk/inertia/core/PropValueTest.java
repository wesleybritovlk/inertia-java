package io.github.wesleybritovlk.inertia.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 * Value semantics of the prop wrappers: the pre-release-8 line exposed these as records,
 * so equals/hashCode/toString parity (content-based, null-tolerant, record-shaped
 * {@code Name[field=value, ...]} strings) is part of the API the parity classes preserve.
 */
class PropValueTest {

    private static final Supplier<?> SUPPLIER = () -> 1;

    @Test
    void plainValueSemantics() {
        Prop.Plain plain = new Prop.Plain(SUPPLIER);
        assertEquals(plain, new Prop.Plain(SUPPLIER));
        assertEquals(plain.hashCode(), new Prop.Plain(SUPPLIER).hashCode());
        assertNotEquals(plain, new Prop.Plain(() -> 2));
        assertNotEquals(plain, new Prop.Lazy(SUPPLIER), "different prop kinds never compare equal");
        assertNotEquals(plain, null);
        assertFalse(plain.equals(42), "foreign types compare unequal, never throw");
        assertTrue(plain.toString().startsWith("Plain[value="));
        assertSame(SUPPLIER, plain.value());
    }

    @Test
    void lazyAndAlwaysValueSemantics() {
        assertEquals(new Prop.Lazy(SUPPLIER), new Prop.Lazy(SUPPLIER));
        assertEquals(new Prop.Always(SUPPLIER), new Prop.Always(SUPPLIER));
        assertNotEquals(new Prop.Lazy(SUPPLIER), new Prop.Always(SUPPLIER));
        assertTrue(new Prop.Lazy(SUPPLIER).toString().startsWith("Lazy[value="));
        assertTrue(new Prop.Always(SUPPLIER).toString().startsWith("Always[value="));
    }

    @Test
    void deferredValueSemantics() {
        Prop.Deferred deferred = new Prop.Deferred(SUPPLIER, "page-2", true);
        assertEquals(deferred, new Prop.Deferred(SUPPLIER, "page-2", true));
        assertNotEquals(deferred, new Prop.Deferred(SUPPLIER, "page-2", false));
        assertNotEquals(deferred, new Prop.Deferred(SUPPLIER, "page-3", true));
        assertNotEquals(deferred, new Prop.Deferred(SUPPLIER, null, true));
        assertTrue(deferred.toString().startsWith("Deferred[value="));
    }

    @Test
    void mergeValueSemanticsSnapshotsPaths() {
        List<String> appends = new ArrayList<>();
        appends.add("items");
        List<String> prepends = new ArrayList<>();
        List<String> matchOn = new ArrayList<>(List.of("id"));
        Prop.Merge merge = new Prop.Merge(SUPPLIER, false, appends, prepends, true, matchOn);

        // content-based equality across distinct list instances (record parity)
        assertEquals(merge, new Prop.Merge(SUPPLIER, false, List.of("items"), List.of(), true, List.of("id")));
        assertEquals(merge.hashCode(), new Prop.Merge(SUPPLIER, false, List.of("items"), List.of(), true, List.of("id")).hashCode());

        // constructor snapshot: later mutation of the caller's lists cannot leak through
        appends.add("late");
        matchOn.add("late");
        assertEquals(List.of("items"), merge.appendsAtPaths());
        assertEquals(List.of("id"), merge.matchOn());

        assertNotEquals(merge, new Prop.Merge(SUPPLIER, true, List.of("items"), List.of(), true, List.of("id")));
        assertNotEquals(merge, new Prop.Merge(SUPPLIER, false, List.of(), List.of(), true, List.of("id")));
        assertNotEquals(merge, new Prop.Merge(SUPPLIER, false, List.of("items"), List.of(), false, List.of("id")));
        assertTrue(merge.toString().startsWith("Merge[value="));
    }

    @Test
    void onceValueSemantics() {
        Prop.Once once = new Prop.Once(new Prop.Plain(SUPPLIER), "k", 10L, Duration.ofSeconds(2), true);
        assertEquals(once, new Prop.Once(new Prop.Plain(SUPPLIER), "k", 10L, Duration.ofSeconds(2), true));
        assertEquals(once.hashCode(), new Prop.Once(new Prop.Plain(SUPPLIER), "k", 10L, Duration.ofSeconds(2), true).hashCode());
        assertNotEquals(once, new Prop.Once(new Prop.Lazy(SUPPLIER), "k", 10L, Duration.ofSeconds(2), true));
        assertNotEquals(once, new Prop.Once(new Prop.Plain(SUPPLIER), "k2", 10L, Duration.ofSeconds(2), true));
        assertNotEquals(once, new Prop.Once(new Prop.Plain(SUPPLIER), "k", 11L, Duration.ofSeconds(2), true));
        assertNotEquals(once, new Prop.Once(new Prop.Plain(SUPPLIER), "k", 10L, Duration.ofSeconds(3), true));
        assertNotEquals(once, new Prop.Once(new Prop.Plain(SUPPLIER), "k", 10L, Duration.ofSeconds(2), false));
        assertTrue(once.toString().startsWith("Once[inner="));
    }

    @Test
    void scrollValueSemantics() {
        ScrollMetadata metadata = new ScrollMetadata() {
            @Override
            public String pageName() {
                return "page";
            }

            @Override
            public Object currentPage() {
                return 1;
            }

            @Override
            public Object nextPage() {
                return null;
            }

            @Override
            public Object previousPage() {
                return null;
            }
        };
        Prop.Scroll scroll = new Prop.Scroll(SUPPLIER, metadata, "page", "items", 3, true, "default");
        assertEquals(scroll, new Prop.Scroll(SUPPLIER, metadata, "page", "items", 3, true, "default"));
        assertEquals(scroll.hashCode(), new Prop.Scroll(SUPPLIER, metadata, "page", "items", 3, true, "default").hashCode());
        assertNotEquals(scroll, new Prop.Scroll(() -> 2, metadata, "page", "items", 3, true, "default"));
        assertNotEquals(scroll, new Prop.Scroll(SUPPLIER, null, "page", "items", 3, true, "default"));
        assertNotEquals(scroll, new Prop.Scroll(SUPPLIER, metadata, "page2", "items", 3, true, "default"));
        assertNotEquals(scroll, new Prop.Scroll(SUPPLIER, metadata, "page", "itemz", 3, true, "default"));
        assertNotEquals(scroll, new Prop.Scroll(SUPPLIER, metadata, "page", "items", 4, true, "default"));
        assertNotEquals(scroll, new Prop.Scroll(SUPPLIER, metadata, "page", "items", 3, false, "default"));
        assertNotEquals(scroll, new Prop.Scroll(SUPPLIER, metadata, "page", "items", 3, true, null));
        assertTrue(scroll.toString().startsWith("Scroll[value="));
    }

    @Test
    void nullTolerantSemanticsMatchRecords() {
        // records compare null components equal through Objects.equals — the parity classes do too
        assertEquals(new Prop.Plain(null), new Prop.Plain(null));
        assertEquals(new Prop.Deferred(SUPPLIER, null, false), new Prop.Deferred(SUPPLIER, null, false));
        assertNotEquals(new Prop.Deferred(SUPPLIER, null, false), new Prop.Deferred(SUPPLIER, "default", false));
        assertEquals(new Prop.Once(new Prop.Plain(SUPPLIER), "k", null, null, false),
                new Prop.Once(new Prop.Plain(SUPPLIER), "k", null, null, false));
        assertEquals(new Prop.Scroll(SUPPLIER, null, null, "w", null, false, null),
                new Prop.Scroll(SUPPLIER, null, null, "w", null, false, null));
        assertFalse(new Prop.Plain(null).equals(new Prop.Lazy(null)), "kinds still differ with null values");
    }
}
