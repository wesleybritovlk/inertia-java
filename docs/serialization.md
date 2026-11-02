# Serialization

The Inertia protocol lives in `inertia-core`, which has **zero third-party dependencies** —
including JSON. The `PageSerializer` SPI couples a serializer into the flow, and the
starter implements a **hybrid strategy** built to survive Jackson generations inside your
own dependency tree: Java JSON libraries change majors over the years, and a serialization
adapter should ride along with whatever version the application runs.

## Resolution order

| Priority | Source |
|---|---|
| 1 | A `PageSerializer` **bean defined by your application** (full control: any Jackson/JSON-B, custom logic) |
| 2 | The `ObjectMapper` your application context provides (Spring Boot/Quarkus built one: your modules, mixins, annotations apply) — picked reflectively per class name |
| 3 | A fresh mapper from the Jackson runtime on the classpath — Jackson 2 (`com.fasterxml...ObjectMapper`) first, then Jackson 3 (`tools.jackson...ObjectMapper`) |
| 4 | The bundled `PlainJsonPageSerializer` (dependency-free) |

Never re-serialize around major changes: the core reflection adapter binds by name/shape,
not by compiled Jackson types. In the common Spring-app case, rule 2 produces output that
looks like any other Jackson call: your `@JsonFormat`, `LocalDateTime` module formatting,
`@JsonIgnore` — all apply naturally. On the legacy Boot 2 line (Jackson 2.13) the mapper
sees standard `get`/`is` accessors — record-style `title()` accessors belong to the Boot 3+
Jackson 3 runtime (see [the divergences page](differences-from-inertia-laravel.md#6-serialization-behavioral-drifts)).

### Fallbacks are compile-free

No exact signature is required: the adapter searches for a `String writeValue(Object)`
method returning `String` (`writeValueAsString` preferred). A mapper whose signature
changes between majors keeps working if a compatible name survives — otherwise the
application-defined bean (rule 1) is the place to be explicit.

## The bundled zero-dependency writer

`PlainJsonPageSerializer` covers the value shapes Inertia props use in practice:

- primitives/boxes, strings, enums, `BigDecimal`, `BigInteger`, `Optional` (empty → `null`),
- maps (keys tokenized as strings), iterables and all primitive array types,
- **records** (component access), POJOs via public `get`/`is` getters, fallback `toString()`,
- `java.time` values (ISO-8601 via `toString()`), `Date`/`Calendar` instants, `UUID`,
  `URI`, `URL`, `Path`, `Class`,
- cycle detection (identity-based) and a 64-level depth cap with clear messages.

Two protocol niceties are guaranteed only here:

- object key order: maps preserve insertion order (Jackson may reorder object keys —
  irrelevant to the protocol, relevant for byte-exact debugging);
- `InertiaPage.preserveBigIntegers()`: JS-unsafe integers wrap as `{"$bigint": "..."}`
  exactly like the Laravel adapter, so the client revives them as `BigInt` values. With a
  Jackson backend the number is emitted raw by *your* mapper — provide a custom
  serializer if you need wrapping.

### Root-template-safe escaping

Output is protected for any HTML surface by design: `<`, `>`, U+2028 and U+2029 are
always emitted as `\uXXXX` escapes (JSON_HEX_TAG equivalent), which guarantees no
`</script>` sequence can appear inside the root `<script data-page>` block, whichever
serializer runs.

## Swapping yourself

```java
@Bean
PageSerializer customPageSerializer() {
    // your own binding: ObjectMapper (any major), Moshi, JSON-B...
    return new EnhancedJsonSerializer();
}
```

## Jackson 2 and Jackson 3 coexisting

The two runtimes use disjoint namespaces (`com.fasterxml.*` vs `tools.jackson.*`) and can
**coexist on the same classpath**. Detection order is Jackson 2, then Jackson 3; an
application-provided mapper (rule 2) is preferred regardless of generation. The one
shared artifact is `com.fasterxml.jackson.core:jackson-annotations` — Jackson 3.0
depends on the later 2.x annotations; when both lineages are present, Maven's standard
nearest-wins arbitration may need one `jackson-annotations` 2.20+ pin to keep the
runtime consistent (the tests in this repository pin 2.20 for exactly this reason).
