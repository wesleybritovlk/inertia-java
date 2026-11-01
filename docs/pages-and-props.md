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
| `withOnce(key, Supplier / options)` | Resolved a single time per client; remembered keys skip resolution on later full visits (`onceProps` metadata stays); partial reloads always resolve | `Inertia::once(fn)` |
| `withScroll(key, Supplier / options)` | Infinite scroll: value labeled for client merge + paginator cursor in `scrollProps` | `Inertia::scroll(...)` |
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

### Once options

Once props are remembered by the client: the first response resolves them and every later
page carrying the same once key receives the remembered keys in the
`X-Inertia-Except-Once-Props` header. On full visits the server then skips resolution and
leaves the value out of `props` (the `onceProps` metadata `{prop, expiresAt}` stays, letting
the client re-hydrate its copy). Partial reloads ignore that header — a requested once prop
always resolves, which is also how the client refreshes it (`router.reload({only: [...]})`).

```java
.withOnce("plans", () -> planRepository.findAll(), o -> o
    .as("plans")                       // once key across pages (default: the prop key)
    .until(Duration.ofHours(1))        // relative expiry (or until(Instant)); → expiresAt ms
    .fresh(isMaintenanceWindow())      // force a refresh even when the client remembers it
    .deferred("stats"))                // compose: deferred/merged()/lazy() wrap other kinds
```

Wire shape:

```json
{
  "props": { "current": "starter" },
  "onceProps": { "plans": { "prop": "plans", "expiresAt": null } }
}
```

Keyed linking lets props with different names share one remembered value (Laravel `as()`):
the key identifies the prop across pages, `prop` names this page's prop. Shared once data
works identically through `inertia.sharedOnce(key, Supplier)` (Laravel `shareOnce()`).

### Scroll options (infinite scroll)

Scroll props are merging props (inertia-laravel `Inertia::scroll()`): the client appends or
prepends the returned data instead of replacing it. A bare list value is wrapped as
`{"data": [...]}`; the merge label lands in `mergeProps` as `<path>.<wrapper>` and is
re-emitted on every response that carries the value. The `scrollProps` cursor is resolved
per request from the `pageName` query parameter (`?page=2` → `currentPage: 2`,
`previousPage: 1`) — Laravel paginator parity — falling back to the page declared while
building the prop; `hasMore` decides whether a next page exists. `X-Inertia-Reset` requests
flag the cursor `reset: true` (the label stays) and tell the client to replace its
accumulated data. The `X-Inertia-Infinite-Scroll-Merge-Intent` header (client `append` /
`prepend` intent) is informational and does not change the resolved metadata.

```java
.withScroll("posts", () -> postRepository.page(pageNumber, size), o -> o
    .page(pageNumber, hasMorePages)    // explicit pagination facts (query parameter still wins)
    .pageName("page")                  // request parameter name (default)
    .wrapper("data")                   // inner array key (default); label = "posts.data"
    .deferred("feed"))                 // optional deferral: cursor only on the resolving partial
```

Cursor pagination (opaque string cursors) uses a full-control provider, which also wins
over the derived values:

```java
.withScroll("messages", () -> repository.pageByCursor(), o -> o
    .metadata(new ScrollMetadata() {
        public String pageName() { return "cursor"; }
        public Object currentPage() { return cursor; }
        public Object nextPage() { return hasNewer ? nextCursor : null; }
        public Object previousPage() { return olderCursor; }
    }))
```

Wire shape:

```json
{
  "props": { "posts": { "data": [{"id": 1}, {"id": 2}] } },
  "mergeProps": ["posts.data"],
  "scrollProps": { "posts": { "pageName": "page", "previousPage": null, "nextPage": 2, "currentPage": 1, "reset": false } }
}
```

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
- `inertia.shared(...)` / `inertia.sharedAlways(...)` / `inertia.sharedOnce(...)` — request-scoped
  shared props ([shared-props.md](shared-props.md)).

Details of the response-level rules for all of it: [redirects-and-versioning.md](redirects-and-versioning.md).
