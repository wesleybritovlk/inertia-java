# The web layer (inertia-web)

`inertia-web` is the framework-neutral web machinery of the project — the
transport-free "web face" of the protocol: the root HTML document (the bundled
`root.html` layout), the Thymeleaf + Vite renderers, the asset-version providers and
the hot-reload scanner. **No servlet API, no framework dependency, Java 8 only** —
the lowest baseline of the project. Every servlet-based stack runs the exact same
rendering code, and a future javax-era glue or Boot 2 adapter reuses it unchanged.

The layers sit like this: [inertia-core](other-frameworks.md) is the protocol engine,
`inertia-web` decides how the **document** looks (version/shell/renderers),
[inertia-servlet](inertia-servlet.md) is the Jakarta transport glue (filter, request
adapter, writers — it delegates rendering here), and the two shipped adapters wire
everything through properties (the `inertia.*` names live with the hosts —
[configuration.md](configuration.md); the classes below take plain constructor args,
no property framework involved).

## The bundled root.html (usually you do not need a template)

`META-INF/inertia/templates/root.html` travels inside the jar with ALL the machinery:
the dev-mode Vite blocks, the `<script>` client/entry wiring, the root
`<div id="app">` mount and the `<script data-page>` payload (`@inertia` Blade
equivalent). No template file, no boilerplate: the served page already works in build
mode AND dev mode.

An application that wants its own head bits (title, meta, fonts, CDN scripts)
overrides with a **head fragment** — one or two lines, nothing else —:
a template that does not compose a full `<html>` document renders with the same
variables and is injected inside the bundled `<head>` (`inertiaUserHead`); the
machinery stays untouched. The advanced escape hatch is a **whole document** (a file
with `<html>`), which replaces the layout and wires the machinery variables itself.

Full users story — fragments, variables, `inertia.template-path` —
[the root template section](getting-started.md#the-root-template-usually-you-do-not-need-one)
and [configuration.md](configuration.md#root-template-override).

## What the module contains

| Class | Role |
|---|---|
| `InertiaRootTemplateRenderer` | The SPI: turns the serialized page JSON (+ `InertiaPage.withViewData` variables) into the SPA root HTML. Pluggable per host. |
| `ThymeleafInertiaRootTemplateRenderer` | The renderer over an isolated Thymeleaf engine: bundled `root.html` layout (or the override path you pass — `new ThymeleafInertiaRootTemplateRenderer(rootId, overrideTemplatePath)`; missing file → bundled template), `data-page` payload, head-fragment injection. |
| `ViteInertiaRootTemplateRenderer` | Wraps any delegate with the `@vite` equivalent: build mode resolves the hashed entry + lazy-chunk css from the manifest; dev mode (hot flag present) injects the Vite client + source entry + React preamble, with a self-healing probe (a stale flag left by a dead dev server clears itself). Configured with a `ManifestSource(manifestLocation, entry, base)` and a `HotMode(hotFiles, devUrl, entry, reactRefresh)`. |
| `ViteManifest` | Manifest JSON reader — entry detection, nested inputs, entryless manifests. |
| `FileVersionProvider` | Asset version = SHA-256 of one file (the Vite manifest, `new FileVersionProvider(location)`). |
| `HotAwareVersionProvider` | Wraps a source and goes **dark** (version `null`) while a hot flag exists — Laravel parity: no versioned `409`s during HMR. |
| `HotFileScanner` | Locates the `.hot-inertia` flags: `autoScan()` (three levels inside the working directory + one bounded parent level, per front-end `node_modules`) or `candidates(property)` for `off`/explicit comma-separated paths. |
| `InertiaResources` | Location reader for `classpath:` (jar-safe, cwd-independent) and filesystem paths — the primitive behind manifest and version reads. |

## What stays out of it

Transport. The module never touches servlet APIs, HTTP response objects, session or
flash — those live in [inertia-servlet](inertia-servlet.md) — nor configuration,
injection or validation frameworks — those live with the adapters. It also does not
own the page-object serialization (the engine core and the `PageSerializer` SPI do,
[serialization.md](serialization.md)).

Raw users (unsupported containers, tests, non-servlet stacks) can drive it directly:
`ThymeleafInertiaRootTemplateRenderer` → `ViteInertiaRootTemplateRenderer` around it,
`FileVersionProvider`/`HotAwareVersionProvider` for the `VersionProvider` port — all
Java-8 classes, no reflection gate needed (`records` are discovered when running on a
JDK ≥ 16, POJOs otherwise).
