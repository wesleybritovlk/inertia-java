# Redirects and versioning

Three response families configure the Inertia protocol on top of normal HTTP, and the
starter filter applies them automatically — controllers never write these headers by hand.

## Internal redirects

```java
return inertia.redirect("/users");
```

Produces a normal `302` + `Location`. The post-chain pass then upgrades it per protocol:

| Situation | Response |
|---|---|
| Inertia visit + redirect + method `PUT`/`PATCH`/`DELETE`/`QUERY` | `303 See Other` |
| Inertia visit + redirect whose `Location` contains `#` | `409` + `X-Inertia-Redirect: <location>` |
| any other redirect | unchanged (plus `Vary: X-Inertia`) |

Fragment redirects exist because client-side scroll-restore and fragment bookkeeping need
a round trip the plain `302` cannot express.

This behavior applies to **any** redirect the Spring handler produces — not only ones built
through `inertia.redirect` — so Spring Security's post-login redirects also upgrade.

## External redirects (full page visits)

```java
return inertia.location("https://sso.example/login");
```

| Request kind | Response |
|---|---|
| Inertia visit (`X-Inertia` present) | `409 Conflict` + `X-Inertia-Location: <url>` — the client performs a full browser visit |
| other visit | normal `302` redirect |

## Asset version guard

The starter wires a `VersionProvider`:

- `inertia.version` — static string (default `"1"`);
- `inertia.version-file` — path of a build manifest (Vite/Rollup style); its SHA-256 is the
  version, matching Laravel's `hash_file(...)` intent;
- your own `VersionProvider` bean replaces both;
- a `null` version disables checks completely.

Before the controller runs, the filter checks GET Inertia visits:

```
if (X-Inertia-Version != currentVersion) -> 409, X-Inertia-Location: <path + query>,
                                            X-Inertia-Version: <current version>
```

The `Location` value includes the **path and query string**; the client's full visit
reloads the exact URL with fresh assets, then sends future requests with the new version.
Non-GET visits are not gated (empirantically the client reloads through the result of the
follow-up GET redirect, matching Laravel).

## The `Vary` header (standing)

Every response passing through the filter gains `Vary: X-Inertia`, telling caches that
the same URL answers HTML to browsers and JSON to Inertia visits.

## Order of guarantees

1. Version guard (filter start) — stale assets never reach a controller.
2. Controller runs (or any handler in the chain — security, etc.).
3. Post-chain: `Vary`, 303 upgrade, fragment escalation — skipped when the response is
   already committed.
