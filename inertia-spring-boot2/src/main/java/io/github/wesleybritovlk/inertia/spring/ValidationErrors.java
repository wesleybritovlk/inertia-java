package io.github.wesleybritovlk.inertia.spring;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wesleybritovlk.inertia.core.FlashKeys;
import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaFlashStore;

import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Validation error bag helper (the shared {@code errors} prop source) — the Boot 2 twin of
 * the Jakarta starter. Persistence travels with the flash session store: store on the
 * failed request, redirect back, and the next {@code render} adopts the bag as the
 * always-present {@code errors} prop.
 */
public final class ValidationErrors {

    private ValidationErrors() {
    }

    /**
     * @param inertia request-scoped engine
     * @param errors  Spring MVC validation result ({@code BindingResult})
     * @param bag     bag name ({@code "default"} unless a drawer bag such as form scopes)
     */
    public static void store(Inertia inertia, Errors errors, String bag) {
        String bagName = bag == null || bag.trim().isEmpty() ? "default" : bag;
        LinkedHashMap<String, Object> bagMap = new LinkedHashMap<>();
        for (ObjectError error : errors.getAllErrors()) {
            String field = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
            if (error.getDefaultMessage() != null) {
                bagMap.putIfAbsent(field, error.getDefaultMessage()); // first message per field (Laravel default)
            }
        }
        InertiaFlashStoreScope.write(inertia, bagName, bagMap);
    }

    private static final class InertiaFlashStoreScope {

        private InertiaFlashStoreScope() {
        }

        @SuppressWarnings("unchecked")
        private static void write(Inertia inertia, String bagName, Map<String, Object> bag) {
            InertiaFlashStore flash = inertia.request().flash();
            Object previous = flash.peek(FlashKeys.ERRORS);
            LinkedHashMap<String, Object> bags;
            if (previous instanceof Map<?, ?>) {
                bags = new LinkedHashMap<>((Map<String, Object>) previous);
            } else {
                bags = new LinkedHashMap<>();
            }
            bags.put(bagName, bag);
            flash.put(FlashKeys.ERRORS, bags);
        }
    }
}
