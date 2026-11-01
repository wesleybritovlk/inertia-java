# Getting started (pure servlet / Jakarta EE 9+)

No Spring, no Quarkus — the Inertia protocol on any servlet container: plain Tomcat
10.1+, Jetty 11+, or a Jakarta EE 9+ WAR (Servlet 5.0+; the glue is built against
Servlet 6.0, whose port APIs used here are stable back to 5.0 — EE 10+ recommended).
Java 17+, Maven, Node 20+. The concepts:
[getting-started.md](getting-started.md). The machinery you are wiring:
[inertia-servlet.md](inertia-servlet.md) (transport) + [inertia-web.md](inertia-web.md)
(ROOT documents, renderers, versions).

## 1. The pom.xml

A WAR application with one library dependency:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0</version>
    <packaging>war</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <failOnMissingWebXml>false</failOnMissingWebXml>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.github.wesleybritovlk.inertia</groupId>
            <artifactId>inertia-servlet</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

`inertia-servlet` brings the whole chain transitively: `inertia-core` (zero
dependencies), `inertia-web` (the bundled root template + renderers + version
providers) and Thymeleaf (the template engine behind them). No Jackson needed: the
bundled `PlainJsonPageSerializer` serializes the page; swap it later if you want
(see [serialization.md](serialization.md)).

## 2. The bootstrap (one listener)

Singletons live in a `ServletContextListener`. Build mode reads the Vite manifest
(hashed assets + SHA-256 version); dev mode wraps it hot-aware so the version goes
`null` while `npm run dev` runs (Laravel parity):

```java
package com.example;

import java.nio.file.Path;
import java.util.List;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer;
import io.github.wesleybritovlk.inertia.core.VersionProvider;
import io.github.wesleybritovlk.inertia.servlet.FileVersionProvider;
import io.github.wesleybritovlk.inertia.servlet.HotAwareVersionProvider;
import io.github.wesleybritovlk.inertia.servlet.InertiaPageWriter;
import io.github.wesleybritovlk.inertia.servlet.ThymeleafInertiaRootTemplateRenderer;
import io.github.wesleybritovlk.inertia.servlet.ViteInertiaRootTemplateRenderer;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class InertiaBootstrap implements ServletContextListener {

    static VersionProvider versionProvider;
    static InertiaPageWriter pageWriter;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        var context = event.getServletContext();
        String manifest = context.getRealPath("/static/manifest.json"); // exploded deploy

        // build mode: the manifest's SHA-256 is the asset version
        VersionProvider fileVersion = new FileVersionProvider(manifest);
        // dev mode: while the hot flag exists the version goes null (no 409s during HMR)
        List<Path> hotFiles = List.of(Path.of(System.getProperty("user.dir"),
                "src", "frontend", "node_modules", ".hot-inertia"));
        versionProvider = new HotAwareVersionProvider(fileVersion, hotFiles);

        var renderer = new ViteInertiaRootTemplateRenderer(
                new ThymeleafInertiaRootTemplateRenderer("app", null), // root-id "app", bundled layout
                new ViteInertiaRootTemplateRenderer.ManifestSource(manifest, "", "/"),
                new ViteInertiaRootTemplateRenderer.HotMode(hotFiles,
                        "http://localhost:5173", "src/app.js", "auto"));

        pageWriter = new InertiaPageWriter(CurrentEngine::current, renderer);
    }
}
```

## 3. The filter (middleware + engine scoping)

One `@WebFilter` wraps the protocol filter and uses its attachment hook to build the
request-scoped engine (the same scoping the adapters do for you):

```java
package com.example;

import java.io.IOException;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaRequest;
import io.github.wesleybritovlk.inertia.servlet.InertiaFilter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter("/*")
public class InertiaProtocolFilter implements Filter {

    private InertiaFilter inertia;

    @Override
    public void init(FilterConfig config) {
        inertia = new InertiaFilter(InertiaBootstrap.versionProvider, new EngineScoper());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        inertia.doFilter(request, response, chain);
    }

    /** builds the engine per request (current request's adapter is handed over) */
    private static final class EngineScoper
            implements InertiaFilter.InertiaRequestAttachment {

        @Override
        public void bind(HttpServletRequest request, HttpServletResponse response, InertiaRequest adapter) {
            CurrentEngine.bind(new Inertia(InertiaBootstrap.versionProvider,
                    new io.github.wesleybritovlk.inertia.core.PlainJsonPageSerializer(), adapter));
        }

        @Override
        public void unbind(HttpServletRequest request, HttpServletResponse response, InertiaRequest adapter) {
            CurrentEngine.unbind();
        }
    }
}
```

```java
package com.example;

import io.github.wesleybritovlk.inertia.core.Inertia;

/** the request-scoped engine the filter binds (and unbinds) per request */
final class CurrentEngine {

    private static final ThreadLocal<Inertia> CURRENT = new ThreadLocal<>();

    private CurrentEngine() {
    }

    static void bind(Inertia inertia) {
        CURRENT.set(inertia);
    }

    static void unbind() {
        CURRENT.remove();
    }

    static Inertia current() {
        return CURRENT.get();
    }
}
```

The filter performs the protocol middleware automatically: stale GET → `409` +
`X-Inertia-Location` before your servlet runs; `Vary: X-Inertia` and redirect upgrades
(`302 → 303` on PUT/PATCH/DELETE, fragment → `409` + `X-Inertia-Redirect`) after —
including redirects your own code produces.

## 4. Pages

A servlet is the controller: build the page from the request-scoped engine and hand it
to the writer — JSON for Inertia visits, the root template for browsers:

```java
package com.example;

import java.io.IOException;

import io.github.wesleybritovlk.inertia.servlet.InertiaPageWriter;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var page = CurrentEngine.current().render("Dashboard/Index", p -> p
                .with("stats", () -> statsService.today())          // evaluated per response
                .withLazy("history", () -> statsService.history())); // partial reloads only
        InertiaBootstrap.pageWriter.write(page, response);
    }
}
```

## 5. Redirects, flash and validation errors

The engine carries the session store (built into `ServletInertiaRequest` over
`HttpSession`); flash data, error bags and page flags travel through it:

```java
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    var inertia = CurrentEngine.current();

    var errorsByField = validate(request); // your validation: Map<String, String>
    if (!errorsByField.isEmpty()) {
        // the error bag: adopted as the always-present "errors" prop on the next render
        inertia.request().flash().put(FlashKeys.ERRORS, java.util.Map.of("default", errorsByField));
        InertiaBootstrap.pageWriter.write(inertia.redirect("/users/new"), response);
        return;
    }
    inertia.flash(java.util.Map.of("message", "User created"));     // one-shot session data
    InertiaBootstrap.pageWriter.write(inertia.redirect("/users"), response);
}
```

(`FlashKeys` is `io.github.wesleybritovlk.inertia.core.FlashKeys`; the bag shape is
`Map<bagName, Map<field, message>>` — Laravel parity. The adapters' `ValidationErrors`
helpers do exactly this plus result-type plumbing.)

## 6. The front-end

The same boilerplate as every other stack
([the concepts page](getting-started.md#the-front-end-boilerplate-any-stack)) — only the
output directory differs: a WAR serves its own files, so build into `src/main/webapp/static`:

```js
build: {
    manifest: 'manifest.json',
    outDir: '../src/main/webapp/static', // the WAR serves this folder
    emptyOutDir: true,
    rollupOptions: { input: { app: 'src/app.js' } },
},
```

`getRealPath("/static/manifest.json")` in the bootstrap then finds the manifest in the
exploded deployment — assets, version guard and the `@vite` equivalent all read from
there.

## 7. Run the dev loop (HMR)

```bash
# terminal 1 — front-end (writes node_modules/.hot-inertia on boot)
npm run dev

# terminal 2 — back-end
mvn package && docker run --rm -p 8080:8080 -v "$PWD/target/my-app-1.0.0.war:/usr/local/tomcat/webapps/ROOT.war" tomcat:10.1-jre17-temurin
# — or your IDE's embedded Tomcat 10.1 / any Jakarta EE 10 server
```

While the hot flag exists, pages serve dev-server modules with HMR (the bootstrap's
`HotMode`/`HotAwareVersionProvider` wiring above); without it, hashed build assets.
Details: [dev-workflow.md](dev-workflow.md).

## 8. Package and deploy

```bash
npm run build       # front-end first: assets are ordinary webapp files
mvn package         # → target/my-app-1.0.0.war
```

Deploy the WAR on Tomcat 10.1+ or any Jakarta EE 9+ server. The root template override
works the same way: a head fragment at `WEB-INF/classes/templates/inertia.html` (or
pass a path to `ThymeleafInertiaRootTemplateRenderer`) —
[the root template section](getting-started.md#the-root-template-usually-you-do-not-need-one).

## What to read next

- What each glue class does: [inertia-servlet.md](inertia-servlet.md) · [inertia-web.md](inertia-web.md)
- Running with the engines you already have: [getting-started-spring.md](getting-started-spring.md) · [getting-started-quarkus.md](getting-started-quarkus.md)
- Every prop builder: [pages-and-props.md](pages-and-props.md)
- The redirect/version behavior: [redirects-and-versioning.md](redirects-and-versioning.md)
