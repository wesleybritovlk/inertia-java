# Differences from inertia-laravel

`inertia-java-bean` ported the Laravel adapter's **protocol semantics** file-by-file (the
resolver, response shape, metadata, redirect ids and flash rules were verified against
the actual PHP sources), but Java ≠ PHP. This page lists every behavior that is not a
1:1 port. Read each: they are intentional consequences of being framework-agnostic, not
missing features — unless told otherwise, they are roadmapped.

## The hot file — what it is, in one paragraph (and why it can never show in git)

The Spring process and the `npm run dev` process are two separate programs, and the
Spring one must answer **per render**: "deliver dev-server modules or built assets?"
The two programs talk through one cheap, reliable channel — a file. While
`npm run dev` is running, the Vite plugin **touches the flag file**; when the dev
server shuts down it **deletes the file**. Spring checks the file's existence per page
and flips:

- flag **present** → dev mode (modules streamed from the dev server, HMR socket live,
  page version `null`);
- flag **absent** → build mode (hashed manifest assets + the versioned `409` stale
  reload flow).

Laravel's adapter does exactly this: `laravel-vite-plugin` touches `public/hot`, and
`Vite::isRunningHot()` reads it. Our defaults go one step friendlier: the shipped
plugin writes the flag **inside `node_modules/`** (`node_modules/.hot-inertia`) —
universally git-ignored by construction, guaranteed to exist at dev time, and it can
never leak into a packaged artifact — and the Spring side **auto-scans** for it
(the empty `inertia.vite-dev-hot` default scans the working directory three levels
deep), so no path pairing is needed and `mvn` may run from the repository root. You
never write the file yourself; `touch`ing it by hand is the manual override — and
nothing at all is needed when you only run in build mode.

| Difference | Meaning |
|---|---|
| Flag file name | Laravel writes `public/hot`; here `node_modules/.hot-inertia`, detected automatically (`inertia.vite-dev-hot` empty by default = auto-scan; `off` disables detection; explicit paths override) |
| React refresh preamble | auto-detected from the dev server (`/@react-refresh` probe only `@vitejs/plugin-react` serves) instead of a known-file-type heuristic; `inertia.vite-react-refresh`= `auto` (default)/`true`/`false` overrides |
| **Version guard** | same parity — while hot, the page version is `null` and no `409` version checks fire (HMR owns the changes); stale-reload stays a build-mode concern |

## Compatibilities that ARE 1:1

Page JSON shape and ordering rules, redirect races (`302→303`, external 409 with
`X-Inertia-Location`, fragment `409` with `X-Inertia-Redirect`), asset-version gating
with query-string-inclusive URLs, partial matching (dot-path semantics, `except`
precedence, subtree cutting on object props), deferred groups + rescue, append/prepend/
deep-merge + `matchOn` metadata (verified against `MergesProps.php` and
`collectMergeableMetadata`), `X-Inertia-Reset` exact-match semantics, shared-prop key
truncation in `sharedProps` metadata, unwrapped default errors bag + bag renaming via
`X-Inertia-Error-Bag`, flash data as top-level page key pulled once per page,
`clearHistory`/`preserveFragment` pulled page flags, Always prop full partial bypass,
`Inertia::location()` returning a plain 302 for non-Inertia visits.

## Divergences

### 1. Asset version hashing

Laravel hashes its frontend asset manifest with `xxh128` over auto-detected paths
(`build/manifest.json`, `mix-manifest.json`). Here you configure it explicitly:

- `inertia.version-file`: SHA-256 of the file (content-changed ⇒ version-changed, the
  same property), or
- `inertia.version`: any static string, or
- your own `VersionProvider` bean.

A missing file returns `null`, which **disables versioning** (Laravel falls back to an
empty-string version instead).

### 2. Where validation errors come from

Laravel's `errors` shared prop reads MessageBags from the framework session lifecycle.
Here `ValidationErrors.store(inertia, errors, bag)` is **explicit** — Spring MVC already
delivers `BindingResult` locally, so the adapter stores it into the flash port. The
prop's usage is identical (always present, `{}` when empty, default-bag unwrapped, bag
header rename). Behavior consequence: errors persist across redirect chains until a page
renders (Laravel's MessageBag is reflashed by the framework on redirects; ours skip the
reflash because flash pulls are page-driven).

### 3. Flash keys inheritance

Both adapters merge consecutive flash calls with newer-wins semantics and clean the
store after a page render, but the session key constant names differ (`inertia.flash_data`
is the same name used here, for toolability). The distinction only matters to custom
stores backing both apps at once.

### 4. `ensureDeferredCallbacksRun` — not implemented

Laravel runs deferred-prop callbacks even when the response ends up as a client
redirect-with-fragment or a version-mismatch 409, so data can survive one more hop in
session flash. Here those responses short-circuit **before** any prop resolves,
consistent with requests that never reach a controller. If your flow depends on
deferred callbacks pre-fill on redirect pre-charts, this is not here yet (roadmap).

### 5. Not implemented (roadmap)

| Feature | State |
|---|---|
| Once props (`Inertia::once`, `X-Inertia-Except-Once-Props`) | metadata placed in the page keys model; builders v2 |
| Scroll props (`Inertia::scroll`, `X-Inertia-Infinite-Scroll-Merge-Intent`) | not implemented |
| `X-Inertia-Vary`, `Vary` handling per route, `X-Inertia-Error-Bag` full unused-bag set | core ready; page-level toggles not exposed yet |
| SSR client (`Inertia::ssr`, `@inertiaHead`, Node render server bridge) | module planned; not in the starter |
| `expose_shared_prop_keys = false` | always exposed today (Laravel default is `true`) |
| `inertia.history.encrypt` config-level default | page-level `encryptHistory()` only |
| `Inertia::throw`, `handleExceptionsUsing`, DevTools recording, Precognition | not ported |
| Per-response root template override (`ResponseFactory::view()`) | not implemented; one global `inertia.template-path` + renderer bean |
| Component existence enforcement (`inertia.pages.ensure_pages_exist`) | not implemented; components are just strings |

### 6. Serialization behavioral drifts

- With the **bundled serializer**, sets: object key ordering preserved, and `preserveBigIntegers()`
  wraps JS-unsafe integers as `{"$bigint": "..."}`.
- With **your Jackson** (the default for Spring apps): your mapper's conventions rule —
  object key order may differ (JSON-object order is not part of the protocol), and the
  bigint wrap is not applied; pass a custom `PageSerializer` when identical behavior and
  byte-for-byte determinism are required.
- Versioning via hash of the version file uses SHA-256 (namespace-sufficient; the exact
  digest does not matter to the protocol).

### 7. Java-ecosystem realities

- Validation messages are reduced to the **first error per field** (Laravel default);
  the `withAllErrors` view does not exist here yet (you can store a custom bag per field
  manually if arrays are preferred).
- Spring MVC redirects mechanics differ from Laravel's `redirect()` routes: our
  post-chain pass upgrades any redirect, including ones produced by Spring Security — an
  upgrade in coverage, not a regression.
- There is no `Route::inertia()` route macro; controllers return `InertiaPage`/`InertiaResult`
  directly.

## When parity matters more than anything

Specify a `PageSerializer` bean and `VersionProvider` bean, set `inertia.version-file`,
and the wire protocol is byte-compatible with the Laravel adapter's — the Inertia client
cannot tell the difference. Divergences above are behavior holes or intentional
Java-ecosystem remixes, not wire-format details.
