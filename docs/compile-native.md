# Native compilation (GraalVM native image)

Both modern adapters compile to a native executable: instant startup, single-digit-MB RSS,
no JVM in the container — the server keeps answering the full Inertia protocol (root HTML
pages, JSON visits, version guard, redirects). The demo apps carry the profiles themselves:
`mvn -pl :inertia-demo-spring -Pnative package` (Boot: bound before repackage, plugin
pinned) and `mvn -pl :inertia-demo-quarkus package -Dnative` — end-to-end shipped proof: the
Spring demo binary serves the full CRUD flow (`DemoNativeHints` registers the demo's
`Todo` record, the application-side agent-equivalent the recipes teach).

> **First, a common misconception:** the old `org.springframework.experimental:spring-native`
> dependency is **not** needed. That was the Spring Boot 2.x experimental project,
> discontinued when native support went **GA inside Spring Boot 3.0**. On Boot 3.x/4.x
> native is built into the standard plugins (`spring-boot-maven-plugin` AOT processing +
> GraalVM `native-maven-plugin`), and on Quarkus it was always built in. No extra library —
> the only requirement is a GraalVM (or Mandrel) distribution with `native-image`.

## Who builds what (the ownership table)

The library never builds an image; the **application** does, with its framework's own
tooling. The library contributes bytecode and — where reflection exists — metadata:

| Layer | Role in a native build |
|---|---|
| `inertia-core` + `inertia-web` (Java 8) | **Nothing to register**: reflection-free plain Java. native-image reads classfiles ≤ its own major, so the Java-8 jars travel transparently inside a JDK 17 toolchain build. |
| `inertia-servlet` | The filter/adapter/writers are plain beans — framework AOT covers them. |
| `inertia-quarkus` | Ships `META-INF/native-image/.../reflect-config.json` (the standard GraalVM mechanism: native-image reads it from any classpath jar). |
| `inertia-spring-boot` | Ships a `RuntimeHintsRegistrar` (`InertiaNativeHints`) — Spring's AOT recognizes registrars, not files. |
| `inertia-servlet-javax` / `inertia-spring-boot2` | **Out of native by construction**: native-image is a JDK 17+ technology and Boot 2 has no AOT engine. |

The one reflective surface of the whole library is the serializer's hybrid bridge: it calls
`writeValueAsString` on the application's mapper **by name** (the zero-dependency
renegotiation across Jackson majors). In a closed-world binary that method is stripped
unless registered — the lab reproduced the exact failure
(`No String returning writeValue(Object) method found on ...ObjectMapper`) and the two
registrations above clear it. That is why the metadata cannot live in the lower layers:
the reflection happens against *your application's* mapper, on the integration module.

## Quarkus native

The `native` profile is what `-Dnative` activates — code.quarkus generates it in every
new app; in a hand-written pom, add it:

```xml
<profiles>
    <profile>
        <id>native</id>
        <activation>
            <property>
                <name>native</name>
            </property>
        </activation>
        <properties>
            <skipITs>true</skipITs>
            <quarkus.native.enabled>true</quarkus.native.enabled>
        </properties>
    </profile>
</profiles>
```

(`quarkus.native.enabled` is the current property; `quarkus.package.type=native` is
deprecated.)

Build and run (front-end first — resources are baked into the package):

```bash
npm run build
mvn package -Dnative          # runs GraalVM native-image; needs several minutes
./target/*-runner             # the executable itself; default port 8080
```

Without a local GraalVM, build through a container instead:
`mvn package -Dnative -Dquarkus.native.container-build=true` (Docker/Podman).

## Spring Boot native

The `spring-boot-starter-parent`'s `native` profile wires `process-aot` and the
reachability-metadata hook, but does **not** bind the GraalVM compile goal to a phase.
Bind it once in your pom:

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>native-compile</id>
                            <phase>package</phase>
                            <goals>
                                <goal>compile</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Build and run (front-end first, as always — the assets are classpath resources):

```bash
npm run build
mvn -Pnative package
./target/my-app               # the executable itself; default port 8080
```

(After `native:compile` the fat jar of the same build still exists:
`target/my-app.jar` — same app, JVM mode.)

## What needs attention in native

Native image is closed-world: reflection, resources and proxies only exist if they are
registered. The adapter's own surfaces are native-friendly by design, but your props
travel through serialization, so:

- **The protocol core, version guard, Vite manifest reader and the hot-flag scanner**
  are reflection-free plain Java — nothing to register.
- **Your `ObjectMapper`** (the default serializer source on both stacks) is handled by
  the framework's own AOT; the library closes the one real gap: the reflective bridge
  into it (the registrations described above — validated in the lab binaries).
- **Rich prop types** (`records`/POJOs returned by your suppliers) ride the framework's
  mapper registration; when you add exotic types: Quarkus apps put
  `@RegisterForReflection` on the type (the demo's `Todo` record carries it — the failing
  shape is Jackson's "No serializer found ... This appears to be a native image"), Spring
  apps register a hint or run the tracing agent
  (`-agentlib:native-image-agent=config-output-dir=src/main/resources/native-image`,
  exercise your pages, commit the generated `config/` files).
- **The bundled `PlainJsonPageSerializer`** (fallback when no Jackson exists) reads
  records/POJO getters reflectively — on native, prefer a Jackson-backed bean or
  register the prop types with the agent above.
- **The bundled Thymeleaf root template** loads from the jar like any classpath
  resource — validated working in the lab builds.
- **Dev mode does not apply** to a native binary: it is a build-mode artifact by
  definition. Disable hot-flag detection in the packaged/native configuration:
  `inertia.vite-dev-hot=off` (the empty default auto-scans only for dev-time runs).

## Lab evidence (GraalVM CE 17.0.9, Mandrel-compatible)

A validation lab (a minimal single-app per stack, exactly the new-project layout of the
getting-started guides) produced native binaries for both stacks. Two real findings came
out of it, both fixed **inside the library jars**, nothing to do application-side:

- **The reflective serializer bridge was stripped** (`No String returning writeValue(Object)
  method found on ...ObjectMapper`) — the registrations above clear it: the compiled binary
  answers the Inertia JSON visit with the full page object (version hash included).
- **The bundled `root.html` resource was stripped** (`Inertia bundled root template missing`)
  — native-image keeps classpath resources only when registered, and the SPLIT of web away
  from the glues put the template in a dependency jar the frameworks' AOT does not scan:
  the adapter registrations cover the `META-INF/inertia/templates/` pattern on both stacks.
  A second subtlety lives inside Spring's own conversion: `ResourcePatternHint` quotes a
  literal `.*` as a dot (`\Qtemplates\/.\E.*` — nothing matches), so the registrar uses a
  bare star (`templates/*` → `\Qtemplates\/\E.*`); the resource-config overlay of the Quarkus
  jar takes the regex form directly. Each stack was rebuilt and re-smoked after the fixes.

Green after both fixes (each stack rebuilt and re-smoked): binaries boot in
single-digit milliseconds, the root HTML visit renders the full `data-page` payload from
the template inside the inertia-web jar, the JSON visit answers the protocol with the
version hash, and the built front-end assets serve from `META-INF/resources` (the Quarkus
static-resources filter validated inside the binary).

## The front-end note (why Svelte is a good neighbor here)

A native deployment is usually about a lean footprint — and the client side is part of
that story even though it ships as static assets: **Svelte 5** compiles your pages to
vanilla DOM operations with no virtual-DOM runtime, giving the smallest bundle and
fastest hydration of the three canonical fronts (Vue 3, React 19, Svelte 5 — all
supported identically server-side). Client framework choice is orthogonal to the native
build; this is only a recommendation for the total-footprint story.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `requiredVersion 22.3` rejected your GraalVM | Upgrade GraalVM/Mandrel; the native-build-tools shipped with recent Boot parents accept the "GraalVM for JDK" versioning (17.0.9 validated here) |
| `OutOfMemoryError` during the native build | `native-image` is memory-hungry: Quarkus tunes it with `quarkus.native.native-image-xmx`; the Spring side passes `-Dorg.graalvm.nativeimage.imagegen...` via `native:compile` — or just add RAM (a small app needs ~6–8 GB) |
| Asset/manifest resource missing in the binary | Resources travel through framework AOT registration; keep them under `META-INF/resources` (Quarkus) / `static` (Boot) as the guides teach, or extend the framework's resource includes. The bundled root template is registered by the adapters (`META-INF/inertia/templates/.*`); an app-owned head-fragment override is an APP resource — register your own pattern or point `inertia.template-path` at a `file:` location |
| A prop type fails to serialize only on native | Run the [tracing agent](https://graalvm.org/latest/reference-manual/native-image/metadata/agent/) on the JVM-mode app and commit the generated config |
| `No String returning writeValue(Object) method found on ...ObjectMapper` | should not happen anymore (the adapters ship the registration); if it does with a custom mapper of your own, register it the same way or switch to a `PageSerializer` bean |
| The reachability-metadata schema requires a newer GraalVM (Boot parent-less poms) | the native-maven-plugin resolves unpinned to the LATEST (new metadata format, GraalVM 21/25 only) — pin Bespoke the Boot line's version (e.g. `0.10.6` for Boot 3.3.x) |
| `Main entry point class ... neither found on classpath` (Boot fat jar) | the native compile ran AFTER Boot's repackage (the fat jar's nested-jar layout is unusable as classpath) — bind the graal `compile` goal BEFORE `package` (e.g. `prepare-package`), so it sees plain `target/classes` |
