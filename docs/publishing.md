# Publishing to Maven Central

The publication pipeline is wired: a `central` profile stages the Central bundle (local
`central-bundle.zip` files) — the release day only adds YOUR two secrets.

## What the machinery does (validated, dry)

`mvn -Pcentral -Drevision=1.0.0 clean deploy -DskipTests -Dgpg.skip=true -DskipUpload=true`
produces `target/central-publishing/central-bundle.zip` with every library artifact at
`1.0.0`: the poms (flattened — the `${revision}` resolves to the literal), the sources
jars, the javadoc jars and the checksums. The `inertia-demo*` artifacts are excluded by
artifactId (they are runnable applications, not library coordinates). No upload happens
(`skipUpload`), no token needed (`gpg.skip` — signing is the one secret-shaped step).

## The version scheme

Plain semver — `1.0.0` — with the Inertia.js client target living in the metadata (the
root pom description and the [protocol status page](index.md) carry it: verified against
the `@inertiajs` 3.10 client). Qualifier-shaped versions like `1.0.0-inertia_3.6.0`
read as PRERELEASES (they sort BELOW `1.0.0`, so a later `1.0.1` can shadow a
`1.0.0-inertia_4.x` and range resolution gets weird), and the underscore degrades to
lexical junk the day `inertia_11` shows up (`_11` sorts before `_8`). If the quarkus
kernel target becomes a real fork decision someday, it is a MAJOR (`2.0.0`), not a
qualifier.

## The secrets that belong to you (one-time setup)

1. **Central Portal account**: register at [central.sonatype.com](https://central.sonatype.com)
   (the `io.github.wesleybritovlk` namespace verifies against your GitHub account).
2. **User token**: portal → "Generate User Token" → paste into `~/.m2/settings.xml`:

   ```xml
   <servers>
     <server>
       <id>central</id>
       <username><!-- token username --></username>
       <password><!-- token password --></password>
     </server>
   </servers>
   ```

3. **GPG signing key** (Central requirement): generate and publish the public key:

   ```bash
   gpg --full-generate-key          # RSA 4096, your name/email
   gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>
   ```

## The release day (one command each)

```bash
VERSION=1.0.0
git tag -a v$VERSION -m "inertia-java $VERSION — protocol parity for the @inertiajs 3.10 client"
mvn -Pcentral -Drevision=$VERSION clean deploy -DskipTests -Dgpg.skip=false
# → bundle staged, uploaded, validated; with autoPublish unset, click Publish at
#   https://central.sonatype.com/publishing/deployments
```

(The `revision` property is the single-source version — the flatten plugin has already
resolved `${revision}` into every deployed pom; no edits needed. Optional flags:
`-DautoPublish=true -DwaitUntil=published` make the pipeline upload→validate→publish
unattended.)

## What ships, what consumers pull

The bundle carries the SEVEN library artifacts + the **parent pom** (`inertia-java`).
The parent is NOT a thing anyone declares — it is the few-KB pom descriptor the Maven
model builder downloads automatically to read the children's inherited metadata
(license, url, plugin defaults); it cannot be left out of the publication because the
published child poms reference it. Consumers never touch it directly:

| Consumer depends on | Maven pulls (transitive) | Never pulled |
|---|---|---|
| `inertia-spring-boot` (Boot 3/4) | it + `inertia-core` + `inertia-web` + `inertia-servlet` + Thymeleaf | the demos, the Boot/Spring stack (provided scopes stay out of consumers) |
| `inertia-quarkus` (Quarkus) | it + `inertia-core` + `inertia-web` + `inertia-servlet` | same |
| `inertia-servlet-javax` (Java-EE 8 era glue) | it + `inertia-core` + `inertia-web` | same |
| raw servlets + manual wiring | `inertia-servlet` / `inertia-servlet-javax` + the same two below | same |

The `provided`-scope dependencies (servlet API, Spring surfaces) do not travel to
consumers — they come from the consumer's own container/framework.

## What NOT to do

- Do not publish `-SNAPSHOT` from this profile unless you actually want snapshot
  consumers (`inertia-core/1.0.0`): the `-Drevision` flip is what turns a
  snapshot build into a release.
- Do not skip the GPG signatures: the bundle validation rejects unsigned files.
