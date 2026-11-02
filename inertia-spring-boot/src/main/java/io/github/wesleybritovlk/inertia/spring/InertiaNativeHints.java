package io.github.wesleybritovlk.inertia.spring;

import java.util.stream.Stream;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image registration for the serializer's reflective bridge: native strips
 * methods Spring's AOT never traced, and the core's hybrid chain invokes
 * {@code writeValueAsString} on the application's mapper <strong>by name</strong> (the
 * zero-dependency renegotiation across Jackson majors, see
 * {@code ReflectiveJacksonPageSerializer}). Without these hints every Inertia visit fails
 * in the binary with "No String returning writeValue(Object) method found on ...
 * ObjectMapper".
 *
 * <p>Registration is by name, so this stays safe when the application compiles with only
 * one Jackson generation on the classpath: Spring AOT drops what is missing.</p>
 */
final class InertiaNativeHints implements RuntimeHintsRegistrar {

    private static final String JACKSON_2 = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String JACKSON_3 = "tools.jackson.databind.ObjectMapper";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerMapper(hints, JACKSON_2);
        registerMapper(hints, JACKSON_3);
        // the bundled root template travels INSIDE the inertia-web jar; native-image keeps
        // classpath resources only when registered (the lab binary reproduced the missing
        // template as a boot failure otherwise). Bare star, no ".*": Spring's
        // ResourcePatternHint conversion quotes the dot literally (\Qtemplates\/.\E.*) and
        // no file contains "templates/." — "templates/*" translates to \Qtemplates\/\E.*
        hints.resources().registerPattern("META-INF/inertia/templates/*");
    }

    private static void registerMapper(RuntimeHints hints, String mapperClassName) {
        hints.reflection().registerType(TypeReference.of(mapperClassName),
                hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS));
    }

    /** test-visible assertion helper: which mapper names the registrar covers */
    static Stream<String> registeredTypes(RuntimeHints hints) {
        return hints.reflection().typeHints().map(TypeHint::getType).map(TypeReference::getName);
    }
}
