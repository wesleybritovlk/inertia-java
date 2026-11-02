package io.github.wesleybritovlk.inertia.demo;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native-image hints for the demo's own prop values (the agent-equivalent, hand-written:
 * [the native doc](../docs/compile-native.md) teaches the agent as the general answer for
 * exotic types — this demo pins one explicitly so {@code mvn -Pnative package} works end
 * to end with zero extra steps). The Todo record travels JSON through the application
 * Jackson mapper, which needs its constructors/fields/methods reflectively present in the
 * binary.
 */
@Configuration
@ImportRuntimeHints(DemoNativeHints.TodoHints.class)
class DemoNativeHints {

    static final class TodoHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(TypeReference.of(SimpleFormTodoController.Todo.class),
                    hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS));
        }
    }
}
