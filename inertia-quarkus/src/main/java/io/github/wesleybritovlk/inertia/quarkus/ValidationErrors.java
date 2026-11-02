package io.github.wesleybritovlk.inertia.quarkus;

import java.util.LinkedHashMap;
import java.util.Set;

import io.github.wesleybritovlk.inertia.core.FlashKeys;
import io.github.wesleybritovlk.inertia.core.Inertia;

import jakarta.validation.ConstraintViolation;

/**
 * Validation error bag helper (the shared {@code errors} prop source), mirroring the Spring
 * adapter's helper: persistence travels with the flash session store — store on the failed
 * request, redirect back, and the next {@code render} adopts the bag as the always-present
 * {@code errors} prop. Bean Validation integration:
 *
 * <pre>{@code
 * Set&lt;ConstraintViolation&lt;User&gt;&gt; violations = validator.validate(user);
 * ValidationErrors.store(inertia, violations, "default");
 * return inertia.redirect("/users/create");
 * }</pre>
 */
public final class ValidationErrors {

    private ValidationErrors() {
    }

    /** Maps one message per violation (first wins per field, inertia-laravel default). */
    public static void store(Inertia inertia, Set<? extends ConstraintViolation<?>> violations) {
        store(inertia, violations, "default");
    }

    /** Same, into an explicit bag name ({@code "default"} unless a form-scoped bag). */
    public static void store(Inertia inertia, Set<? extends ConstraintViolation<?>> violations, String bag) {
        String bagName = bag == null || bag.isBlank() ? "default" : bag;
        LinkedHashMap<String, Object> bagMap = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : violations) {
            String field = violation.getPropertyPath() == null || violation.getPropertyPath().toString().isBlank()
                    ? "global"
                    : violation.getPropertyPath().toString();
            if (violation.getMessage() != null) {
                bagMap.putIfAbsent(field, violation.getMessage());
            }
        }
        storeBag(inertia, bagName, bagMap);
    }

    /** Direct map form (any validation backend that builds its own field → message map). */
    public static void store(Inertia inertia, java.util.Map<String, Object> errorsByField, String bag) {
        storeBag(inertia, bag == null || bag.isBlank() ? "default" : bag, errorsByField);
    }

    @SuppressWarnings("unchecked")
    private static void storeBag(Inertia inertia, String bagName, java.util.Map<String, Object> bag) {
        var flash = inertia.request().flash();
        Object previous = flash.peek(FlashKeys.ERRORS);
        LinkedHashMap<String, Object> bags = previous instanceof java.util.Map<?, ?> existing
                ? new LinkedHashMap<>((java.util.Map<String, Object>) existing)
                : new LinkedHashMap<>();
        bags.put(bagName, bag);
        flash.put(FlashKeys.ERRORS, bags);
    }
}
