package io.github.wesleybritovlk.inertia.demo.quarkus;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Demo launcher: only depends on the Quarkus adapter (which brings Undertow, RESTEasy and
 * Jackson through the Quarkus platform). Runs via {@code mvn quarkus:dev} or the packaged jar.
 */
@QuarkusMain
public class DemoApplication {

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
