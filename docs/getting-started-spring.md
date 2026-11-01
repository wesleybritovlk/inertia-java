# Getting started (Spring Boot)

A new project from an empty folder to a packaged app, with the Inertia loop working the
whole way: dev mode (HMR) while you build, build mode + version guard when you ship.
Works on **Spring Boot 3.x and 4.x** (Java 17+, Maven, Node 20+). The concepts behind
every step: [getting-started.md](getting-started.md).

## 1. The pom.xml

The starter brings the protocol engine, the servlet glue (filter, writers, Thymeleaf
root template, Vite/versioning providers) and the auto-configuration — the only other
dependency is the web starter:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.12</version> <!-- 4.x works the same -->
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>io.github.wesleybritovlk.inertia</groupId>
            <artifactId>inertia-spring-boot</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Nothing else is required — not even Thymeleaf (the root template renderer brings its own
engine). If your application uses Spring Boot's Jackson, the starter reuses **your**
mapper for serialization ([serialization.md](serialization.md)).

## On a Spring Boot 2.7 application instead

Everything in this guide — the application class, the controllers, the properties, the
front-end, the validation round trip — is **identical** on the legacy line. Only the
first dependency swaps:

```xml
<dependency>
    <groupId>io.github.wesleybritovlk.inertia</groupId>
    <artifactId>inertia-spring-boot2</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

That artifact (Java 8, `javax.servlet`, Spring 5.3, Boot 2.7 line) carries the same auto-configuration through
`META-INF/spring.factories` — same `inertia.*` properties, request-scoped `Inertia` bean,
filter, MVC bridges; neither the code nor the front-end changes. Two Java-ecosystem notes:

- prop value classes need Jackson-visible accessors (`getId`/`isDone`) — record-style
  `id()` accessors belong to the Boot 3+ Jackson 3 runtime ([why](serialization.md#resolution-order));
- the runnable proof is the demo: `mvn -pl :inertia-demo-spring-boot2 spring-boot:run` serves
  the same pages the Boot 3 demo does ([demo README](../inertia-demo/README.md)).

## 2. The application class

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

## 3. The first controller

Both `@Controller` and `@RestController` work — return an `InertiaPage` for pages and
`InertiaResult` for redirects:

```java
package com.example;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DashboardController {

    private final Inertia inertia; // request-scoped bean from the starter

    DashboardController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/dashboard")
    InertiaPage dashboard() {
        return inertia.render("Dashboard/Index", page -> page
                .with("stats", () -> statsService.today())          // evaluated per response
                .withLazy("history", () -> statsService.history())  // partial reloads only
                .withDeferred("insights", () -> insights.calc()));  // client resolves later
    }
}
```

## 4. The configuration

`src/main/resources/application.properties` — for real applications, point
`inertia.vite-manifest` at the manifest your front-end build produces (from the
[boilerplate](getting-started.md#the-front-end-boilerplate-any-stack), that is
`classpath:static/manifest.json`): it enables the `@vite` equivalent renderer **and**
the asset version (manifest SHA-256) in one line:

```properties
inertia.vite-manifest=classpath:static/manifest.json
# inertia.root-id=app        # default; must match createInertiaApp({ id })
# inertia.vite-dev-hot=off   # recommended on production servers
```

Every property and bean override: [configuration.md](configuration.md).

## 5. The front-end

Follow [the boilerplate section](getting-started.md#the-front-end-boilerplate-any-stack)
of the concepts page — it owns the `vite.config.js` (with the inline hot-file plugin),
the folder layout and the `@inertiajs` entrypoint, identically for Vue/React/Svelte.
The short version:

```bash
npm init -y && npm i vite @vitejs/plugin-vue @inertiajs/vue3 vue
# vite.config.js + src/app.js + src/Pages/... from the boilerplate section
```

`npm run build` then writes the hashed assets + `manifest.json` into
`src/main/resources/static/` — where the Spring static handler (and the version
provider) reads them from.

## 6. The root template

None needed — the bundled layout already renders the page. For a title/fonts/CDN of
your own, ship a two-line **head fragment** at
`src/main/resources/templates/inertia.html`:

```html
<title>my app</title>
```

See [the root template section](getting-started.md#the-root-template-usually-you-do-not-need-one)
for the fragment vs whole-document forms.

## 7. Run the dev loop (HMR)

Two terminals:

```bash
# terminal 1 — front-end (writes node_modules/.hot-inertia on boot)
npm run dev

# terminal 2 — back-end (auto-detects the flag; pages serve dev modules)
mvn spring-boot:run        # → http://localhost:8080
```

Edit any file under `src/frontend/src` — the running page updates without reload.
No build, no property, no path pairing. Details and React specifics:
[dev-workflow.md](dev-workflow.md).

## 8. Run the build mode

```bash
npm run build
mvn spring-boot:run        # same command, no dev server → hashed assets + version guard
```

With the manifest configured, the asset version is its SHA-256: rebuild the front-end
and stale browser sessions get the standard `409` full-page-visit flow on the next
navigation ([redirects-and-versioning.md](redirects-and-versioning.md)).

## 9. Validation round trip

```java
@PostMapping("/users")
InertiaResult create(UserForm form, BindingResult binding) {
    if (binding.hasErrors()) {
        ValidationErrors.store(inertia, binding, "default");
        return inertia.redirect("/users/create");
    }
    service.create(form);
    return inertia.redirect("/users");
}
```

On the redirect target, the next page carries the `errors` prop (Laravel-compatible
shape, bag renaming through `X-Inertia-Error-Bag`):
[flash-and-validation.md](flash-and-validation.md).

## 10. SPA routing (catch-all endpoint)

When the SPA has client-side routes, back them with one server mapping so first visits
render the right component (explicit mappings always win over the catch-all):

```java
@GetMapping("/{*path}")
InertiaPage anyPath(@PathVariable("path") String path) {
    return inertia.render(pageComponentFor(path));
}
```

## 11. Package and run

Build the front-end first (the assets are ordinary classpath resources), then package:

```bash
npm run build
mvn package
java -jar target/my-app-1.0.0.jar
```

GraalVM native: the demo apps carry the profile — install a GraalVM 17+ with
`native-image` and `mvn -Pnative package` writes an instant-boot executable with no JVM
([the native guide](compile-native.md) for the ownership table, the reflective
registration story, recipes and troubleshooting).

The jar is self-contained: hashed assets, manifest, root template and the whole
protocol machinery. SPA catch-all routing (`@GetMapping("/{*path}")`) keeps first
visits of client-side routes working the same way as any other page.

## What to read next

- The same guide for Quarkus: [getting-started-quarkus.md](getting-started-quarkus.md)
- On a pre-Jakarta Spring Boot 2.7 app: the same properties and beans come from
  [`inertia-spring-boot2`](../inertia-spring-boot2) (javax + Java 8, auto-configuration
  via `spring.factories`) — swap only the dependency coordinates.
- Every prop builder: [pages-and-props.md](pages-and-props.md)
- The redirect/version behavior: [redirects-and-versioning.md](redirects-and-versioning.md)
- A runnable proof: the [demo tree](../inertia-demo/README.md)
