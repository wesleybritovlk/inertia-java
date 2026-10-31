# Flash and validation

One-shot session state (data that survives exactly one render, redirect-wave included)
runs through the neutral `InertiaFlashStore` port. In the Spring starter the backing
store is `HttpSession`; other adapters map it to their own session systems.

## Flash data

```java
Map<String, Object> data = Map.of("message", "User registered");
return inertia.flash(data).redirect("/users");
```

The **next rendered page** carries the values as a top-level `"flash"` key (not inside
`props` — Laravel parity), and then consumes them:

```json
{ "component": "Users.created", "props": { "...": "..." },
  "url": "/users", "version": "b39c07f", "flash": { "message": "User registered" } }
```

Merging follows Laravel's `Inertia::flash` semantics: consecutive `flash(...)` calls
accumulate, newer values overwrite same keys. Calling `flash` with an empty/null map is a
no-op. If the flow hits another redirect before a render, the data stays — flash is
pulled by page renders, not by redirects.

Adapters without session support expose the `none()` store: writes are no-ops and reads
return `null`, so pages degrade gracefully.

## Validation errors

```java
@PostMapping("/users")
InertiaResult create(UserForm form, BindingResult binding) {
    if (binding.hasErrors()) {
        ValidationErrors.store(inertia, binding, "default");
        return inertia.redirect("/users/create");
    }
    // ...
}
```

The stored bag surfaces on the next rendered page as the always-present `errors` **prop**
(empty `{}` when nothing was stored):

```json
"props": { "errors": { "name": "is required" }, "...": "..." }
```

Shaping rules (Laravel parity):

- messages are reduced to the **first error per field** (the Laravel default);
- field errors key on the field; global errors (`binding.reject(...)`) key on the object name;
- without a bag header, the `"default"` bag is sent **unwrapped**;
- a request carrying `X-Inertia-Error-Bag: create` re-names the stored default bag:
  `{ "create": { "name": "is required" } }`;
- `ValidationErrors.store(...)` merges into bags already in flash (multiple bags across a request).

Errors are pulled when a page renders them — redirect chains keep them alive, and they do
not linger: the bag is consumed by the first rendered page (like flash).

## Page flags from the session

Two request-scoped one-shot flags work the same way:

```java
inertia.clearHistory();      // next page object gains "clearHistory": true (client wipes history)
inertia.preserveFragment();  // next page object gains "preserveFragment": true
```

Both are pulled on the next render, and can survive redirects (they are never emitted
twice). `encryptHistory()` is a **page** flag instead of session — call it on the
`InertiaPage`:
[pages-and-props.md](pages-and-props.md).

## Session payload caveat

Flash values pass through your servlet container's session machinery. Like any session
data, keep payloads `Serializable` if your container persists/serialized sessions
(Redis-backed stores do). Prop resolution runs on whatever the store returns.
