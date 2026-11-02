# Partial reloads

Partial reloads let the client fetch a subset of props on the **same page component**. The
server honors the standard protocol headers:

| Header | Meaning |
|---|---|
| `X-Inertia-Partial-Component` | The component being reloaded; partial behavior only applies when it matches the rendered component |
| `X-Inertia-Partial-Data` | Comma-separated dot-paths the client wants |
| `X-Inertia-Partial-Except` | Comma-separated dot-paths to exclude — takes precedence over the `only` list |
| `X-Inertia-Reset` | Comma-separated prop keys whose merge state must be cleared |

When `X-Inertia-Partial-Component` does not equal the rendered component, the request is
treated as a **full** response (the Laravel adapter silently downgrades the same way).

## Matching rules

For each resolved prop path `p` against a partial request:

- `matchesOnly(p)` — `p` equals a requested path or descends from it (`only=["users"]`
  matches `"users"`, `"users.0"`, `"users.profile.name"`);
- `leadsToOnly(p)` — a requested path descends from `p` (`only=["users.name"]` keeps
  `"users"` in the tree so the deeper path can be cut);
- `matchesExcept(p)` — same relation as `only`, but marks the path **excluded**; the
  exclude list wins over the only list;
- an absent/empty `only` list means *all* props.

## Subtree cutting

Container values (JSON objects) are **walked**, so partial matching prunes inside values:

```
props: page.with("users", () -> Map.of("name", "wei", "age", 31))
only = "users.name"
response.props = { "users": { "name": "wei" } }
```

Arrays are sent whole when their containing path matches (no index-level matching) — the
same practical outcome for the use cases only-paths exist for. Values that are *not* maps
(lists, scalars, POJOs, records) bypass subtree cutting, mirroring Laravel's
"parentWasResolved" escape hatch.

Prop types interact with partials like this:

| Prop | Full visit (or unsolicited partial) | Partial with matching path |
|---|---|---|
| `with` | included | included |
| `withLazy` | excluded (never evaluated) | included |
| `withAlways` | included | included (bypass) |
| `withDeferred` | excluded + `deferredProps[group][path]` | included |
| any failing `withDeferredRescued` | — | dropped + reported in `rescuedProps` |
| `withMerged` | included with its merge metadata | included + metadata when the path matches |
| `withOnce` | skipped when the client remembers the key (`onceProps` metadata stays) | always resolves — the except-once header is ignored |
| `withScroll` | value wrapped `{data: [...]}` + cursor in `scrollProps` | included + merge label and cursor when matched |

`deferredProps` metadata is emitted **only on full visits** (the client triggers each
group later as a partial reload, and the deferred prop then behaves like a plain one).

## Deferred groups

```java
page.withDeferred("permissions", () -> permissions())             // group "default"
    .withDeferred("analytics", "slow", () -> analytics.weekly()); // group "slow"
```

Metadata emitted on the full visit:

```json
"deferredProps": {
  "default": ["permissions"],
  "slow": ["analytics"]
}
```

The client then issues a partial reload per group with `only` set to those paths.
`withDeferredRescued` guards callback failures: the error goes to the engine's failure
handler (`inertia.onPropFailure(...)` — wire it to your logging), the prop is omitted,
and the path is listed in `rescuedProps`. Non-rescued deferred failures bubble up
intentionally (a broken page data pipeline should surface at 500).

## Merge metadata on partials

Merge metadata is collected only for props that would be **included** on the partial:

- `matchesOnly` (without ancestor inclusion) gates the metadata; meta only appears when
  the path is requested exactly or is a descendant of a requested path;
- `X-Inertia-Reset` containing the **exact** prop path drops all of its merge metadata
  without changing the prop's inclusion.

## What the client expects back

A partial response carries the complete page object shape (`component`, `props`, `url`,
`version`, plus the metadata arrays needed by the received props). The client merges the
props it received with its current state — merge semantics (`mergeProps`,
`prependProps`, `deepMergeProps`, `matchPropsOn`), once props and scroll props are all
implemented (wire shapes in [pages-and-props.md](pages-and-props.md)); every remaining
divergence from the Laravel adapter is listed in
[differences-from-inertia-laravel.md](differences-from-inertia-laravel.md).
