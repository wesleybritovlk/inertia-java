# Pages and props

Everything a controller builds with `inertia.render(...)` lands as the page object:

```json
{
  "component": "Users/Index",
  "props": { "...": "..." },
  "url": "/users?page=2",
  "version": "b39c07f"
}
```

`url` includes the query string and the servlet context path (matches Laravel's
URL-after-origin for subdirectory deployments). `version` comes from the configured
`VersionProvider` (see [redirects-and-versioning.md](redirects-and-versioning.md)).

## Building a page

```java
return inertia.render("Users/Index");
```

```java
return inertia.render("Users/Index", page -> page
    .with("users", this::users)
    .withAlways("auth.user", this::currentUser));
```

Both overloads exist; the consumer form keeps prop building in one expression. Prop keys
must not be blank; duplicate keys overwrite in-place (last wins, insertion order kept).

## Prop builders

Every builder is on `InertiaPage` and maps 1:1 to the corresponding inertia-laravel type.

| Builder | Server-side behavior | Laravel equivalence |
|---|---|---|
| `with(key, value)` | Standard prop. Sent on full visits; on partial reloads only when its dot-path matches (or through an wrapping `Always` prop on the same top key) | `'users' => $x` |
| `with(key, Supplier)` | Same, evaluated only when the response actually includes it | closures in props |
| `withLazy(key, Supplier)` | Never evaluated/sent on full visits; resolved on partial reloads that match | `Inertia::lazy(fn)` |
| `withAlways(key, value / supplier)` | Always included and evaluated; bypasses `only`/`except` partial filtering | `Inertia::always(...)` |
| `withDeferred(key, Supplier)` | Excluded from this response; recorded under `deferredProps["default"]`; the client re-requests it later | `Inertia::defer(fn)` |
| `withDeferred(key, group, Supplier)` | Same, within an explicit group | `Inertia::defer(fn, 'group')` |
| `withDeferredRescued(key / group, Supplier)` | When the supplier fails, the prop is dropped and added to `rescuedProps` metadata (the page survives) | `Inertia::defer(fn, group, rescue: true)` |
| `withMerged(key, Supplier)` | Client-side merge metadata: root append | `Inertia::merge(fn)` |
| `withMerged(key, Supplier, MergeOptions)` | Fluent merge configuration (next section) | the `MergeProp` builder |
| `withViewData(key, value)` / `withViewData(Map)` | Template variables only — never in the page JSON | `->withViewData()` |

### Merge options

```java
.withMerged("messages", () -> inbox.least(), o -> o
    .prepend()              // root family: prepend instead of append
    .matchOn("id")          // item identity for array dedup
    .appendAt("data")       // append into "messages.data" instead of the root
    .prependAt("header")    // prepend "messages.header"
    .deep())                // deep merge (objects merge recursively)
```

Semantics follow the Laravel `MergesProps` trait exactly:

- one root family flag: `append()` (default) vs `prepend()` — the last root-family call wins;
- `appendAt`/`prependAt` add sub-path entries without changing the root family;
- `deep()` gives the whole prop to `deepMergeProps` (priority over append/prepend);
- `matchOn(...)` produces `"<prop>.<key>"` entries in `matchPropsOn`;
- `X-Inertia-Reset` containing the exact prop path removes all merge metadata (details in
  [partial-reloads.md](partial-reloads.md)).

### Dot-key nesting

String keys containing dots are unpacked into nested objects:

```java
page.with("auth.user", () -> currentUser);
// props: { "auth": { "user": {...} } }
```

This applies to shared props too, and the partial-reload matching uses the resolved
nested paths.

## Page-level flags

| Method | Page JSON key | Meaning |
|---|---|---|
| `encryptHistory()` | `encryptHistory: true` | Client encrypts the history entry for this page |
| `preserveBigIntegers()` | `preserveBigIntegers: true` | Integers outside the JS safe range are wrapped as `{"$bigint": "..."}` — **only guaranteed with the bundled serializer** (see [serialization.md](serialization.md)) |

## Redirects and other outcomes

`inertia.render` is for pages. Internal navigation, external navigation and shared
configuration live on the `Inertia` bean:

- `inertia.redirect("/path")` — internal redirect;
- `inertia.location("https://...")` — full page visit (409 + `X-Inertia-Location` for
  Inertia visits; a normal 302 otherwise);
- `inertia.flash(...)`, `inertia.clearHistory()`, `inertia.preserveFragment()` — session
  one-shot state consumed by the next rendered page ([flash-and-validation.md](flash-and-validation.md));
- `inertia.shared(...)` / `inertia.sharedAlways(...)` — request-scoped shared props
  ([shared-props.md](shared-props.md)).

Details of the response-level rules for all of it: [redirects-and-versioning.md](redirects-and-versioning.md).
