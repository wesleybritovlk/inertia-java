# Other frameworks (the shared servlet glue + inertia-core without Spring)

The Spring Boot starter and the Quarkus module are full integrations, but everything
protocol-level they share now lives in **`inertia-servlet`** (the Jakarta Servlet port
implementations: request adapter, protocol filter, result/page writers, Thymeleaf/Vite
root renderers and the version providers). Any servlet-based stack — **plain Tomcat,
Jetty, JavaEE-era WAR apps, WS containers** — reuses them with zero framework code, and
non-servlet stacks keep dropping to `inertia-core` + the two ports:

## The two ports

### InertiaRequest

The only reading side — the protocol engine queries it instead of finding the web
framework on its own (no thread-locals; one implementation per request):

```java
public interface InertiaRequest {
    String method();                       // "GET"
    String header(String name);            // null when absent
    String path();                         // request URI sans query (undecoded, Laravel REQUEST_URI parity)
    String query();                        // raw query string or ""
    InertiaFlashStore flash();             // none() when the stack has no session
    // defaults: isInertiaVisit(), url() (path + query), headerList(name)
}
```

`url()` builds the exact page-object `url` value the protocol requires (path plus query).

### InertiaFlashStore

Flash data, error bags, and the `clearHistory`/`preserveFragment` flags travel through
it (the port's simple pull/put/peek contract). Without a session concept return
`InertiaFlashStore.none()` — the engine degrades gracefully.

Optional SPIs: `VersionProvider` (asset version), `PageSerializer` (JSON binding).

## What `inertia-servlet` gives a plain container

```java
// the filter (plain jakarta.servlet.Filter) wire up + writers:
InertiaFilter filter = new InertiaFilter(versionProvider); // optional request attachment
FilterRegistration registration = ...; // your container: plain servlets/embedded Tomcat
// page rendering (JSON page or root template render):
InertiaPageWriter writer = new InertiaPageWriter(engineSupplier, rootTemplateRenderer);
writer.write(returnValue, response);
```

`ServletInertiaRequest.of(request)` is the ready-made request port; `InertiaResultWriter`
writes bare results (the filter's guard/escalations build them). The Thymeleaf root
template and its Vite dev-mode support are inside the same module
([configuration.md](configuration.md) describes the `inertia.vite-*` properties on the
Spring side; the Quarkus module mirrors them as `inertia.*` config entries). For a full
zero-to-WAR walkthrough — bootstrap listener, filter wiring with the engine-scoping
attachment, page servlets — see [the pure-servlet guide](getting-started-servlet.md).

## Raw servlet example

Ten lines around the engine cover a full protocol-compliant servlet:

```java
public final class InertiaServlet extends HttpServlet {

    private final VersionProvider versionProvider = () -> "b39c07f";
    private final PageSerializer serializer = new PlainJsonPageSerializer();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        InertiaRequest inertiaRequest = ServletInertiaRequest.of(request);
        Inertia engine = new Inertia(versionProvider, serializer, inertiaRequest);

        InertiaResult conflict = InertiaVersionGuard.check(inertiaRequest, versionProvider);
        if (conflict != null) {                       // stale assets: full page visit
            writeResult(conflict, response);
            return;
        }

        InertiaResult result = engine.result(engine.render("Users/Index")
                .with("users", () -> repository.findAll()));

        writeResult(result, response);
        InertiaResponseUpgrade.apply(inertiaRequest, new ServletShell(response));
    }

    private static void writeResult(InertiaResult result, HttpServletResponse response) throws IOException {
        result.headers().forEach(response::setHeader);
        response.setStatus(result.status());
        if (result.json().isPresent()) {
            response.getOutputStream().write(result.json().orElseThrow().getBytes(StandardCharsets.UTF_8));
        }
        if (result.kind() == InertiaResult.Kind.PAGE_HTML) {
            // render result.pageJson() through your own template layer
            response.getOutputStream().write(htmlOf(result.pageJson().orElseThrow()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
```

`ServletShell` implements `InertiaResponseShell` around `HttpServletResponse`
(`status()`, `location()`, `status(int)`, `header(String, String)`) — the shared
`InertiaFilter` carries this exact shell and can also host your request-scoped engine
through its optional `InertiaRequestAttachment` (the Quarkus module uses that hook).

## What runs where and why it stays framework-free

| Concern | Lives in | Type |
|---|---|---|
| Protocol rules (props, partials, redirects, version guard) | `inertia-core` | pure Java |
| Servlet-shaped request/response wiring | `inertia-servlet` | plain Jakarta Servlet glue |
| CDI/Welding/properties per framework | adapter modules | Spring Boot auto-config / Quarkus CDI |
| Flash storage mechanics | the host | `InertiaFlashStore` |

Nothing inside the core imports Spring, Servlet or Quarkus types; the servlet glue is
visited only by the adapters. That is why the Boot 3/Boot 4 axes stay green and why the
Quarkus module (`inertia-quarkus`) reuses the exact same engine and writers.
