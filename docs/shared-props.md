# Shared props

Shared props are request-scoped values merged into **every** rendered page — the Adapter's
version of the Laravel middleware `Inertia::share` call. Page props override shared props
with the same key.

Available on the request-scoped `Inertia` bean (per current request):

```java
inertia.shared("banner", "Connected");
inertia.shared("auth.user", () -> currentUser());          // lazy shared prop
inertia.sharedAlways("permissions", () -> permissions());  // bypasses partial filtering
inertia.sharedAlways("appVersion", "1.4.2");
inertia.sharedOnce("announcements", () -> announcements()); // remembered by the client
```

Semantics per kind mirror the page props:

| Builder | Full visits | Partial reloads |
|---|---|---|
| `shared(key, value / supplier)` | included | included only when the dot-path is requested |
| `sharedAlways(key, value / supplier)` | included | included (bypass) |
| `sharedOnce(key, supplier [, options])` | included unless the client already remembers the once key (then skipped, `onceProps` metadata stays) | included when requested — the except-once header is ignored on partials |

`sharedOnce` rides the same once-props mechanics as page props (options, expiry,
`onceProps` metadata — [pages-and-props.md](pages-and-props.md#once-options)).

Dot keys unpack into nesting (`"auth.user"` → `{ "auth": { "user": ... } }`), and page
props win key-by-key while the shared entry keeps position.

## Wiring from beans

The request-scoped `Inertia` instance is the natural place for cross-cutting data:
implement `InertiaSharedPropsCustomizer` (any number of beans) and the starter applies
them when the engine is created per request:

```java
@Bean
InertiaSharedPropsCustomizer auth() {
    return inertia -> inertia.sharedAlways("auth.user", () -> security.currentUser());
}

@Bean
InertiaSharedPropsCustomizer locale() {
    return inertia -> inertia.shared("locale", () -> LocaleContextHolder.getLocale().toLanguageTag());
}
```

## Page metadata

Every page response lists the top-level shared keys in `sharedProps` (each truncated at
its first dot, deduplicated — Laravel's `expose_shared_prop_keys` default):

```json
"sharedProps": ["banner", "auth", "locale"]
```

The framework-owned `errors` prop is handled exactly like a shared Always prop — always
present, never listed in `sharedProps` metadata — keeping validation errors available on
every page shape (see [flash-and-validation.md](flash-and-validation.md)).
