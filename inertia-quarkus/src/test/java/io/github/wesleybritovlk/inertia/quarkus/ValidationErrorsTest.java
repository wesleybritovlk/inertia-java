package io.github.wesleybritovlk.inertia.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.wesleybritovlk.inertia.core.FlashKeys;
import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaFlashStore;
import io.github.wesleybritovlk.inertia.core.InertiaHeaders;
import io.github.wesleybritovlk.inertia.core.InertiaRequest;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;

/** Unit coverage of the validation bag helper (constraint violations + plain map form). */
class ValidationErrorsTest {

    /** Minimal core request port over a map flash store (no servlet, no CDI in the unit). */
    private static final class TestRequest implements InertiaRequest {

        final Map<String, Object> flashAttributes = new LinkedHashMap<>();
        final InertiaFlashStore store = new InertiaFlashStore() {
            @Override
            public Object pull(String key) {
                return flashAttributes.remove(key);
            }

            @Override
            public void put(String key, Object value) {
                flashAttributes.put(key, value);
            }

            @Override
            public Object peek(String key) {
                return flashAttributes.get(key);
            }
        };

        @Override
        public String method() {
            return "GET";
        }

        @Override
        public String header(String name) {
            return InertiaHeaders.INERTIA;
        }

        @Override
        public String path() {
            return "/test";
        }

        @Override
        public String query() {
            return "";
        }

        @Override
        public InertiaFlashStore flash() {
            return store;
        }
    }

    private Inertia engine(TestRequest request) {
        return new Inertia(() -> "v", new PlainJsonPageSerializer(), request);
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> violation(String propertyPath, String message, boolean nullPath) {
        return (ConstraintViolation<Object>) Proxy.newProxyInstance(ValidationErrorsTest.class.getClassLoader(),
                new Class<?>[] { ConstraintViolation.class }, (proxy, m, args) -> switch (m.getName()) {
                    case "getPropertyPath" -> nullPath ? null : proxyPath(propertyPath);
                    case "getMessage" -> message;
                    default -> methodDefault(m);
                });
    }

    private static Path proxyPath(String value) {
        return (Path) Proxy.newProxyInstance(ValidationErrorsTest.class.getClassLoader(), new Class<?>[] { Path.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "toString" -> value;
                    default -> methodDefault(m);
                });
    }

    private static Object methodDefault(Method m) {
        Class<?> t = m.getReturnType();
        if (t == boolean.class) {
            return false;
        }
        if (t == int.class) {
            return 0;
        }
        if (t == long.class) {
            return 0L;
        }
        return null;
    }

    @Test
    void violationsMapIntoBags() {
        TestRequest request = new TestRequest();
        Inertia inertia = engine(request);

        ValidationErrors.store(inertia, Set.of(
                violation("name", "is required", false),
                violation("email", "not an email", false)), "default");

        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) request.store.peek(FlashKeys.ERRORS);
        @SuppressWarnings("unchecked")
        Map<String, Object> bag = (Map<String, Object>) bags.get("default");
        assertEquals("is required", bag.get("name"));
        assertEquals("not an email", bag.get("email"));
    }

    @Test
    void nullPropertyPathFallsToTheGlobalField() {
        TestRequest request = new TestRequest();
        Inertia inertia = engine(request);

        ValidationErrors.store(inertia, Set.of(violation(null, "form invalid", true)), null);

        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) request.store.peek(FlashKeys.ERRORS);
        @SuppressWarnings("unchecked")
        Map<String, Object> defaultBag = (Map<String, Object>) bags.get("default");
        assertEquals("form invalid", defaultBag.get("global"));
    }

    @Test
    void secondStoreMergesBagsOnTheSameErrorsFlashEntry() {
        TestRequest request = new TestRequest();
        Inertia engine = engine(request);

        engine.request().flash().put(FlashKeys.ERRORS, new LinkedHashMap<>(Map.of(
                "previous", Map.of("field", "old"))));
        ValidationErrors.store(engine, Set.of(violation("name", "is required", false)), "another");

        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) request.store.peek(FlashKeys.ERRORS);
        @SuppressWarnings("unchecked")
        Map<String, Object> previousBag = (Map<String, Object>) bags.get("previous");
        assertEquals("old", previousBag.get("field"));
        @SuppressWarnings("unchecked")
        Map<String, Object> anotherBag = (Map<String, Object>) bags.get("another");
        assertEquals("is required", anotherBag.get("name"));
        assertTrue(bags.size() == 2, "both bags coexist: " + bags.keySet());
    }

    @Test
    void violationsWithNullMessagesAreSkipped() {
        TestRequest request = new TestRequest();
        Inertia inertia = engine(request);

        ValidationErrors.store(inertia, Set.of(violation("name", null, false)), "");

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> bags = (java.util.Map<String, Object>) request.store.peek(FlashKeys.ERRORS);
        assertTrue(bags.get("default") instanceof java.util.Map<?, ?> blankBag && ((java.util.Map<?, ?>) blankBag).isEmpty(),
                "violations without a message contribute nothing");
    }

    @Test
    void mapFormStoresAnyValidationBackendShape() {
        TestRequest request = new TestRequest();
        Inertia inertia = engine(request);

        ValidationErrors.store(inertia, Map.of("title", "too long"), "form");

        @SuppressWarnings("unchecked")
        Map<String, Object> bags = (Map<String, Object>) request.store.peek(FlashKeys.ERRORS);
        @SuppressWarnings("unchecked")
        Map<String, Object> bag = (Map<String, Object>) bags.get("form");
        assertEquals("too long", bag.get("title"));
    }
}
