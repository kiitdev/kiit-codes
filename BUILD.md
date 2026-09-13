# kiit-codes — Build & Publish Guide

All Gradle commands below are run from `kiit-codes-kotlin/` — the self-contained Gradle project root (not the outer repo root; see `_prd/260910-kiit-codes-typescript/kiit-codes-structure.md` for why the repo is laid out this way).

---

## Install

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK  | 17+     | `java -version` to verify |
| Android SDK | any | Required for `androidTarget` compilation |
| GPG  | 2.x     | `gpg --version`; must have the dev.kiit secret key imported |
| Node / npm | 18+ / 10+ | Only needed for `scripts/publish-npm.sh` and `samples/sample-ts` — not for the Kotlin build itself |

Import the signing key if not already present:
```bash
gpg --import dev.kiit.seckey.asc
# Verify
gpg --list-secret-keys --keyid-format LONG
```

> **Why this import step matters:** `kiit-codes/build.gradle.kts`'s `signing { useGpgCmd() }` doesn't
> reference `dev.kiit.seckey.asc` (or any file) directly — it tells Gradle's signing plugin to shell
> out to the external `gpg` binary on your `PATH` instead of using its default in-memory PGP
> implementation. That external `gpg` process reads from your local keyring (`~/.gnupg/`) and
> selects/unlocks the key via the `signing.gnupg.keyName` / `signing.gnupg.passphrase` properties
> (see [Setup](#setup) below). So the import above is what actually makes the key available —
> Gradle itself never touches the raw key material. This is also why `useGpgCmd()` is required
> instead of `useInMemoryPgpKeys` — see the ["Could not read PGP secret key"](#could-not-read-pgp-secret-key) FAQ entry.

---

## Setup

Choose **one** of the two approaches below. Both are equivalent — pick whichever fits your workflow.

### Option A — `~/.gradle/gradle.properties` (recommended for local dev)

Add the following to `~/.gradle/gradle.properties` (create the file if it does not exist):

```properties
# Maven Central credentials (portal token — not your account password)
mavenCentralUsername=<portal-token-username>
mavenCentralPassword=<portal-token-password>

# GPG signing via system keyring
signing.gnupg.keyName=<full-key-id>
signing.gnupg.passphrase=<passphrase>
```

With this in place, publishing commands need no extra flags:
```bash
./gradlew :kiit-codes:publishAndReleaseToMavenCentral
```

### Option B — Shell environment variables (recommended for CI / scripted runs)

Export these variables in your shell profile (e.g. `~/.zshrc`) or in your CI secrets:

| Shell variable      | Gradle property             |
|---------------------|-----------------------------|
| `KIIT_MAVEN_USER`   | `mavenCentralUsername`      |
| `KIIT_MAVEN_PSWD`   | `mavenCentralPassword`      |
| `KIIT_MAVEN_GPGNAME`| `signing.gnupg.keyName`     |
| `KIIT_MAVEN_GPGPASS`| `signing.gnupg.passphrase`  |

Pass them as `-P` flags because dots in the property names are not valid bash variable names:

```bash
./gradlew :kiit-codes:publishAndReleaseToMavenCentral \
    -Psigning.gnupg.keyName=$KIIT_MAVEN_GPGNAME \
    -Psigning.gnupg.passphrase=$KIIT_MAVEN_GPGPASS \
    -PmavenCentralUsername=$KIIT_MAVEN_USER \
    -PmavenCentralPassword=$KIIT_MAVEN_PSWD
```

---

## CI — GitHub Actions

Two workflows live under [`.github/workflows`](.github/workflows):

| Workflow | File | Trigger | What it does |
|----------|------|---------|---------------|
| CI | `ci.yml` | Every PR into `main` | `ktlintCheck`, `detekt`, `jvmTest`, `jsNodeTest` on `ubuntu-latest`. iOS tests are not run in CI (no macOS runner). |
| Release | `release.yml` | Manual (`workflow_dispatch`) | Builds, tests, publishes to Maven Central, tags, and cuts a GitHub release. Runs on `macos-latest` — required to build/sign the iOS targets. |

CI runners are ephemeral — there is no persistent GPG keyring. The secret key must be imported at the start of every release run.

### 1. Repository secrets

Add these five secrets under `Settings → Secrets and variables → Actions`:

| Secret name           | Value |
|-----------------------|-------|
| `KIIT_GPG_SECRET_KEY` | Base64-encoded GPG secret key (see below) |
| `KIIT_MAVEN_GPGNAME`  | GPG key ID |
| `KIIT_MAVEN_GPGPASS`  | GPG passphrase |
| `KIIT_MAVEN_USER`     | Maven Central portal token username |
| `KIIT_MAVEN_PSWD`     | Maven Central portal token password |

Encode your secret key for the `KIIT_GPG_SECRET_KEY` secret (run locally):
```bash
gpg --armor --export-secret-keys <your-key-id> | base64 | pbcopy
```

### 2. Cutting a release

Releases are **not** triggered by pushing a tag — `release.yml` creates the tag itself, from the
version already in Gradle:

1. Bump `libraryVersion` in [`kiit-codes/build.gradle.kts`](kiit-codes/build.gradle.kts) (see the
   FAQ entry below) and merge that change to `main`.
2. From the GitHub Actions tab, run the **Release** workflow (`workflow_dispatch`, no inputs).
3. It reads the version via `./gradlew :kiit-codes:printVersion`, verifies a tag for that version
   doesn't already exist, runs the same lint/test gate as CI, publishes to Maven Central, then
   pushes tag `v<version>` and creates a GitHub release with auto-generated notes
   (`gh release create --generate-notes`) covering everything since the previous release.

A failed publish never leaves behind a tag or a release — tagging and the GitHub release both
happen only after `publishAndReleaseToMavenCentral` succeeds.

### 3. GPG pinentry (if import or signing hangs / fails)

Headless runners have no terminal, so anything that makes `gpg-agent` try to spawn an interactive
or GUI pinentry (a prompt for a passphrase, or to protect newly-imported secret key material)
fails outright — on macOS this shows up as `error sending to agent: Inappropriate ioctl for
device` during import; on Linux it more often just hangs. `release.yml`'s "Import GPG key" step
already works around this (forces loopback pinentry mode before importing):

```bash
mkdir -p ~/.gnupg
echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
gpgconf --kill gpg-agent
gpg --batch --yes --pinentry-mode loopback --import ...
```

If you hit the same class of error locally (not in CI), add the equivalent to `~/.gnupg/gpg.conf` and `~/.gnupg/gpg-agent.conf`:

```bash
echo "pinentry-mode loopback" >> ~/.gnupg/gpg.conf
echo "allow-loopback-pinentry" >> ~/.gnupg/gpg-agent.conf
gpgconf --kill gpg-agent
```

Gradle itself already passes `--batch --pinentry-mode loopback` automatically when it shells out to `gpg` for signing, so this is normally only needed for the raw `gpg --import` step, not the actual signing step.

---

## Build

```bash
# Stop the Gradle daemon (useful after changing env vars or upgrading Gradle)
./gradlew --stop

# Clean build outputs
./gradlew :kiit-codes:clean

# Compile all targets (JVM, Android, JS, iOS)
./gradlew :kiit-codes:build

# Compile only — no tests
./gradlew :kiit-codes:assemble

# Run the Kotlin sample app
./gradlew :samples:sample-kotlin:run

# Run the Java sample app
./gradlew :samples:sample-java:run
```

### Run the Swift sample app

Requires Xcode (for `swiftc`/`xcrun`/the iOS Simulator). Not Gradle-driven — SKIE compiles its
generated Swift wrapper code directly into the framework's own `.swiftmodule`, so the sample only
needs `import KiitCodes`, no separately-compiled SKIE source files.

```bash
./gradlew :kiit-codes:linkDebugFrameworkIosSimulatorArm64
cd samples/sample-swift && ./run.sh
```

`run.sh` compiles `Sources/sample-swift/main.swift` against the built `KiitCodes.framework` with
`swiftc`, targeting `arm64-apple-ios-simulator` to match how the framework was built, then runs
the binary inside a booted iOS Simulator via `xcrun simctl spawn` (running an
iOS-simulator-targeted binary directly on the host, without a simulator, fails — it needs
`dyld_sim`, only available inside an actual booted simulator).

---

## Test (local)

```bash
# JVM tests (fastest — runs on the local JVM)
./gradlew :kiit-codes:jvmTest

# All platform tests
./gradlew :kiit-codes:allTests

# Publish to Maven Local (~/.m2) for integration testing against other modules
./gradlew :kiit-codes:publishToMavenLocal
```

Maven Local artifacts are saved to:
```
~/.m2/repository/dev/kiit/kiit-codes/
```

---

## Publish

Published artifacts (once a version has actually gone through `publishAndReleaseToMavenCentral`):
[central.sonatype.com/artifact/dev.kiit/kiit-codes](https://central.sonatype.com/artifact/dev.kiit/kiit-codes)

### Publish to Maven Local

No credentials required.

```bash
./gradlew :kiit-codes:publishToMavenLocal
```

### Publish to Maven Central — Option A (gradle.properties)

Requires `~/.gradle/gradle.properties` populated per the Setup section above.

```bash
./gradlew :kiit-codes:publishAndReleaseToMavenCentral
```

### Publish to Maven Central — Option B (env vars)

```bash
./gradlew :kiit-codes:publishAndReleaseToMavenCentral \
    -Psigning.gnupg.keyName=$KIIT_MAVEN_GPGNAME \
    -Psigning.gnupg.passphrase=$KIIT_MAVEN_GPGPASS \
    -PmavenCentralUsername=$KIIT_MAVEN_USER \
    -PmavenCentralPassword=$KIIT_MAVEN_PSWD
```

### Sign artifacts only (dry-run check)

```bash
./gradlew :kiit-codes:signKotlinMultiplatformPublication
```

---

## Publish — npm (`@kiit/codes`)

Published artifacts (once a version has actually gone through `scripts/publish-npm.sh`):
[npmjs.com/package/@kiit/codes](https://www.npmjs.com/package/@kiit/codes)

**Local-only for now** — this is not wired into `.github/workflows/release.yml` yet (see the
README roadmap). Publishing to npm today is a manual, local step:

```bash
npm login          # one-time, if not already authenticated
./scripts/publish-npm.sh
```

The script builds the JS production library distribution
(`./gradlew :kiit-codes:jsBrowserProductionLibraryDistribution`), previews exactly what would be
published via `npm pack --dry-run`, asks for confirmation, then runs
`npm publish --access public` from `kiit-codes/build/dist/js/productionLibrary` — the
`--access public` flag is required the first time a new scoped (`@kiit/...`) package is
published, since scoped packages default to private on npm otherwise.

The package name/version are set in `kiit-codes/build.gradle.kts`'s `js(IR) { }` block (see the
[version FAQ entry](#how-do-i-bump-the-version) below) — there's no separate npm version to track.

---

## FAQ

### "Cannot perform signing task — has no configured signatory"

The signing plugin could not find the key. Check in order:

1. Verify the key is in the GPG keyring:
   ```bash
   gpg --list-secret-keys --keyid-format LONG
   ```
2. Confirm `signing.gnupg.keyName` matches the full key ID shown above.
3. If using `~/.gradle/gradle.properties`, make sure the file is saved and the Gradle daemon is restarted (`./gradlew --stop`).
4. Do **not** use `signAllPublications()` inside `mavenPublishing {}` — it uses `providers.gradleProperty` internally which does not reliably read `~/.gradle/gradle.properties` (Gradle issue #23572). Use the `signing {}` block directly with `useGpgCmd()`.

### "GPG prompts for the wrong key's passphrase"

`signing.gnupg.keyName` is pointing at the wrong key ID. Run `gpg --list-secret-keys --keyid-format LONG`, find the `dev.kiit` key, and update the property to its full fingerprint.

### "Could not read PGP secret key"

Do not use in-memory key signing (`useInMemoryPgpKeys`). The stripped base64 format is fragile and Bouncycastle can silently produce a null signatory. Use `useGpgCmd()` instead.

### Maven Central portal returns 401

The `mavenCentralUsername` and `mavenCentralPassword` are **portal token** credentials, not your account login. Generate a token at [central.sonatype.com](https://central.sonatype.com) under Account → Generate User Token.

### How do I bump the version?

Edit the `libraryVersion` val near the top of `kiit-codes/build.gradle.kts` (above the `kotlin { }` block):
```kotlin
val libraryVersion = "0.2.1"   // ← bump here
```
This single value feeds the published Maven coordinates, the npm `package.json` version (via the
`js(IR) { }` block's `packageJson { }`), and the `printVersion` task the release workflow reads to
tag and name the GitHub release — see [CI — GitHub Actions](#ci--github-actions) above. There's no
separate npm version to track.
