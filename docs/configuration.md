# Configuration

All starter behavior is under the `inertia.*` namespace (`InertiaProperties`). Defaults
are sane; `inertia.version-file` is the one you will usually set.

## Property reference

| Property | Default | Meaning |
|---|---|---|
| `inertia.version` | `"1"` | Static asset version string |
| `inertia.version-file` | _(empty)_ | Path of a build manifest; SHA-256 of the file becomes the version and takes priority over `inertia.version`. Missing file disables versioning (`null`) |
| `inertia.vite-manifest` | _(empty)_ | **Vite integration switch**: location of the produced `manifest.json` — classpath: locations (jar-safe, cwd-independent, recommended for build outputs) or filesystem paths. Activates the `@vite` equivalent renderer (`viteAppJs`/`viteCss` template variables, entry detection + lazy-chunk css) and — when no explicit version source is set — doubles as the asset version source (same SHA-256) |
| `inertia.vite-base` | `/` | URL prefix for Vite asset files |
| `inertia.vite-entry` | _(empty)_ | Vite entry key override; default resolves the first `"isEntry": true` entry |
| `inertia.root-id` | `app` | DOM mount id — must equal the client's `createInertiaApp({ id })` and is used for both `data-page` and `div#id` in the root template |
| `inertia.template-path` | `classpath:templates/inertia.html` | Classpath resource for your root template override; missing → bundled template |
| `inertia.filter-order` | `Ordered.HIGHEST_PRECEDENCE + 50` | Filter registration order (outermost-outermost, letting security filters' redirects also get the 303/409 upgrades) |

```yaml
inertia:
  version-file: build/manifest.json
  root-id: app
  template-path: classpath:templates/inertia.html
  filter-order: -2147483547
```

## Bean-oriented customization

Any of these beans can be replaced by defining your own (auto-configuration uses
`@ConditionalOnMissingBean` for each):

| Bean | Wire-up field |
|---|---|
| `VersionProvider` | Asset version logic (anything that varies when the frontend changes: manifest hash, git short hash, deploy timestamp) |
| `PageSerializer` | Serialization strategy (see [serialization.md](serialization.md)) |
| `InertiaRootTemplateRenderer` | Root-page rendering — e.g. plug a richer Thymeleaf engine to reuse the application's template layout / dialects |
| `Inertia` | (request-scoped) full engine override — rarely needed; `InertiaSharedPropsCustomizer` beans are applied within the default one |
| `InertiaSharedPropsCustomizer` | Add any number of these beans — applied at engine creation ([shared-props.md](shared-props.md)) |

## Root template override

The bundled layout inside `inertia-servlet` carries ALL the machinery (dev-mode vite
blocks, entry script, lazy-chunk css and the `data-page` payload) — by default you ship
no template at all. When you want the application's own head bits (title, meta, fonts,
analytics), override with a **head fragment** — the simple case, one or two lines:

```html
<!-- classpath: templates/inertia.html (default location) -->
<title>my app</title>
```

The fragment renders with the same variables and is injected into the bundled layout's
`<head>` (`inertiaUserHead`). Full control stays available: a **whole document** (a file
composing the full `<html>` page) replaces the layout itself and wires the machinery
variables directly — advanced use, and the historical behavior.

Two ways to own it:

1. the default property: `classpath:templates/inertia.html` — ship your file there;
2. a different path via `inertia.template-path` — useful for testing overrides or
   version-specific layouts.

Variables injected into the engine for every render (fragment and whole document):
`inertiaPageJson`, `inertiaRootId`, `inertiaUserHead` (the fragment's processed output,
empty when absent), `viteDevMode`/`viteClient`/`viteAppJs`/`viteCss`/`viteReactRefresh`
(dev/vite objects), plus each `InertiaPage.withViewData(...)` value (see
[getting-started.md](getting-started.md#4-the-root-template)).

## Filter behavior

`InertiaFilter` (registered once with `addUrlPatterns("/*")`) performs:

1. pre-chain: asset-version guard comparing `X-Inertia-Version` on GET visits;
2. post-chain: `Vary: X-Inertia`, redirect upgrades (`302 → 303`, fragment escape to
   409) — skipping POST-chain responses that are already committed.

Filter order matters: keep it outermost (`HIGHEST_PRECEDENCE` family) if you want Spring
Security's redirect responses upgraded too. It is idempotent: headers are set, never
doubled.

## Boot-plus-Boot notes

- The starter compiles against Jakarta Servlet 6.x (`compileOnly`) and requires
  `@ConditionalOnWebApplication(type = SERVLET)`: it turns off gracefully on
  non-servlet (WebFlux) apps except for `spring-boot-autoconfigure` itself.
- Jackson is never a compile-time dependency of the starter; the auto-configuration
  follows both Jackson locations (`afterName` entries for Boot 3's and Boot 4's
  `JacksonAutoConfiguration`).
