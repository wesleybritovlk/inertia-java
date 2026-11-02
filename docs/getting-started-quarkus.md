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
            <version>1.0.0</version>
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
inertia.vite-manifest=classpath:META-INF/resources/manifest.json
# inertia.root-id=app        # default; must match createInertiaApp({ id })
# inertia.vite-dev-hot=off   # recommended on production servers
```

## 4. The front-end

Identical to every other stack — follow
[the boilerplate section](getting-started.md#the-front-end-boilerplate-any-stack) of the
concepts page (`vite.config.js` with the inline hot-file plugin, `src/app.js`,
`src/Pages/...`) with ONE quarkus-specific override: the build writes into
`../src/main/resources/META-INF/resources/` — the classpath convention the adapter's
static-resources filter serves (plain `static/` is invisible on Quarkus):

```js
// vite.config.js (quarkus flavour) — besides the boilerplate's config:
build: { outDir: '../src/main/resources/META-INF/resources' },
```

`npm run build` bakes front-end assets and manifest into
`src/main/resources/META-INF/resources/` — served by the adapter's filter AND read as
the version source by the `inertia.*` config below (`classpath:META-INF/resources/manifest.json`).

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

GraalVM native: install a GraalVM 17+ with `native-image` and `mvn package -Dnative`
writes a single executable that boots in milliseconds
([the native guide](compile-native.md) for the ownership table, the reflective
registration story, recipes and troubleshooting).

## Containerizing the published coordinates

The same project as an image, assuming the library ships on Maven Central — three
stages, no reactor copies: the `frontend/` build (boilerplate out
`../src/main/resources/static`) is baked in every image build, the application compiles
against the published `inertia-quarkus` coordinates (swap the pom placeholder for the
released version when publishing), and the fast-jar layout travels whole (the Quarkus
runtime is a folder, not a single file):

```dockerfile
# 1) the front-end, rebuilt on every image build (assets bake in each time)
FROM node:20-alpine AS front
WORKDIR /app
COPY frontend frontend
RUN cd frontend && npm ci && npm run build

# 2) the application compiles against the PUBLISHED inertia coordinates (Central)
FROM bellsoft/liberica-openjdk-alpine:17 AS build
WORKDIR /app
COPY --from=maven:3.9 /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/local/bin/mvn
COPY pom.xml .
COPY src src
# graft the front build into the quarkus convention (the adapter's static-resources
# filter serves META-INF/resources; the front stage wrote exactly there)
COPY --from=front /app/src/main/resources/META-INF/resources src/main/resources/META-INF/resources
RUN mvn -B package -DskipTests

# 3) fast-jar runtime: the quarkus-app FOLDER is the runnable unit
FROM bellsoft/liberica-openjre-alpine:17
WORKDIR /app
COPY --from=build /app/target/quarkus-app quarkus-app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-app/quarkus-run.jar"]
```

```bash
docker build -t my-app .
docker run --rm -p 8080:8080 my-app   # → localhost:8080
```

Until the release is public, `mvn clean install` on the libraries fills `~/.m2` and your
host `mvn package` resolves the snapshot — the published version drops in the same
placeholder afterwards. [The native variant](compile-native.md).

## What to read next

- The same guide for Spring Boot: [getting-started-spring.md](getting-started-spring.md)
- Adapter specifics (overrides, CDI details): [inertia-quarkus.md](inertia-quarkus.md)
- Every prop builder: [pages-and-props.md](pages-and-props.md)
- A runnable proof: the [demo tree](../inertia-demo/README.md)
