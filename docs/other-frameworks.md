# Other frameworks (inertia-core without Spring)

The starter is the full Spring MVC integration, but `inertia-core` alone is enough for
any servlet-based (or port-based) stack: **Quarkus (RESTEasy, coming module), plain
Tomcat, Jetty, JavaEE-era WAR apps, WS containers**. This page documents the ports you
implement and a raw-servlet example.

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

## Raw servlet example

Ten lines around the engine cover a full protocol-compliant servlet:

```java
public final class InertiaServlet extends HttpServlet {

    private final VersionProvider versionProvider = () -> "b39c07f";
    private final PageSerializer serializer = new PlainJsonPageSerializer();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        InertiaRequest inertiaRequest = ServletInertiaRequest.of(request); // your wrapper
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

Your wrapper is ~30 lines (copies of `ServletInertiaRequest` from the starter work as a
reference); `ServletShell` implements `InertiaResponseShell` around
`HttpServletResponse` (`status()`, `location()`, `status(int)`, `header(String, String)`).

## What runs where and why it stays framework-free

| Concern | Lives in | Type |
|---|---|---|
| Protocol rules (props, partials, redirects, version guard) | `inertia-core` | pure Java |
| Request/response reading sides | your adapter | the two ports |
| Root page HTML | your adapter | text layer |
| Flash storage mechanics | your adapter | `InertiaFlashStore` |

Nothing inside the core imports Spring, Servlet or Quarkus types — that is why the
Boot 4 story works for this library and why the Quarkus module will reuse the exact same
engine.
