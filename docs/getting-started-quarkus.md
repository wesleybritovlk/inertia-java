# Getting started (Quarkus)

A new project from an empty folder to a packaged app, with the Inertia loop working the
whole way: dev mode (HMR) while you build, build mode + version guard when you ship.
Quarkus 3.15+ (Java 17+, Maven, Node 20+). The concepts behind every step:
[getting-started.md](getting-started.md). The protocol rules are identical to the Spring
side — same filter, same engine, same pages.

## 1. The pom.xml

The adapter brings Undertow servlet registration, the RESTEasy writers and the CDI
config mapping — the platform BOM manages versions:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <quarkus.platform.version>3.15.3</quarkus.platform.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.github.wesleybritovlk.inertia</groupId>
            <artifactId>inertia-quarkus</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

That single dependency is everything: core protocol, servlet glue, Undertow filter,
RESTEasy writers, CDI producers. No configuration class, no `beans.xml` in your app
(the adapter ships its own bean-archive marker).

## 2. The first controller

Inject the engine — the same ergonomics as the Spring side (a request-scoped bean,
delivered through the CDI client proxy):

```java
package com.example;

import java.util.List;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/dashboard")
public class DashboardController {

    private final Inertia inertia;

    @Inject
    DashboardController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GET
    public InertiaPage dashboard() {
        return inertia.render("Dashboard/Index", page -> page
                .with("stats", () -> statsService.today())          // evaluated per response
                .withLazy("history", () -> statsService.history())  // partial reloads only
                .withDeferred("insights", () -> insights.calc()));
    }
}
```

Resource methods return `InertiaPage` (pages + root renders) or `InertiaResult`
(redirects, external locations). The JAX-RS writer answers Inertia visits with the page
JSON and browser visits with the root HTML; the shared filter does the version guard,
`Vary` and redirect upgrades — exactly like Spring.

## 3. The configuration

`src/main/resources/application.properties` — same names and defaults as the Spring
properties ([configuration.md](configuration.md)). For real applications:

```properties
inertia.vite-manifest=classpath:static/manifest.json
# inertia.root-id=app        # default; must match createInertiaApp({ id })
# inertia.vite-dev-hot=off   # recommended on production servers
```

## 4. The front-end

Identical to every other stack — follow
[the boilerplate section](getting-started.md#the-front-end-boilerplate-any-stack) of the
concepts page (`vite.config.js` with the inline hot-file plugin, `src/app.js`,
`src/Pages/...`). `npm run build` writes into `src/main/resources/static/`; the
classpath property above points at it.

## 5. The root template

None needed — the same bundled layout the Spring side uses. For a title/fonts of your
own, ship a two-line **head fragment** at
`src/main/resources/templates/inertia.html`:

```html
<title>my app</title>
```

Fragment vs whole-document forms:
[the root template section](getting-started.md#the-root-template-usually-you-do-not-need-one).

## 6. Run the dev loop (HMR)

Two terminals:

```bash
# terminal 1 — front-end (writes node_modules/.hot-inertia on boot)
npm run dev

# terminal 2 — back-end (auto-detects the flag; pages serve dev modules)
mvn quarkus:dev            # → http://localhost:8080
```

`quarkus:dev` forks the process from the module directory — the hot-flag auto-scan
covers that layout (it also scans one bounded parent level), so `npm run dev` +
`mvn quarkus:dev` is the whole pairing. Edits under `src/frontend/src` update the
running page without reload. React Fast Refresh: auto-detected
([dev-workflow.md](dev-workflow.md)).

## 7. Run the build mode

```bash
npm run build
mvn quarkus:dev            # or the packaged run below — no dev server → hashed assets
```

With the manifest configured, the asset version is its SHA-256: a rebuild flips stale
clients into the standard `409` full-page-visit flow
([redirects-and-versioning.md](redirects-and-versioning.md)). Static assets are served
by the adapter's filter from the classpath (`META-INF/resources` convention), in dev
and packaged runs alike.

## 8. Validation round trip

```java
@POST
public InertiaResult create(UserForm form) {
    Set<ConstraintViolation<UserForm>> violations = validator.validate(form);
    if (!violations.isEmpty()) {
        ValidationErrors.store(inertia, violations, "default");
        return inertia.redirect("/users/create");
    }
    service.create(form);
    return inertia.redirect("/users");
}
```

`ValidationErrors` (this module) also takes a plain `Map<String, Object>` for any other
validation backend. The stored bag surfaces as the always-present `errors` prop on the
next rendered page — same contract as the Spring side:
[flash-and-validation.md](flash-and-validation.md).

## 9. Package and run

Build the front-end first, then package — and note the Quarkus nuance: `quarkus:run`
serves the resources **baked into the last `mvn package`**, so a fresh front-end build
requires re-packaging (`quarkus:dev` picks resource changes live):

```bash
npm run build
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## What to read next

- The same guide for Spring Boot: [getting-started-spring.md](getting-started-spring.md)
- Adapter specifics (overrides, CDI details): [inertia-quarkus.md](inertia-quarkus.md)
- Every prop builder: [pages-and-props.md](pages-and-props.md)
- A runnable proof: the [demo tree](../inertia-demo/README.md)
