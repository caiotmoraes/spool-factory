# Build and test

The APK uses Android's standard Java/WebView APIs with no app runtime dependencies. It is built with JDK 17 and Android platform/build-tools 35 using the included PowerShell scripts. No Gradle or Android Studio installation is required.

On Windows with PowerShell 7:

```powershell
./setup.ps1
./test.ps1
./build.ps1
```

`setup.ps1` downloads tooling from Eclipse Adoptium, Google and Maven Central into the ignored `tools/` folder. SDK ZIP checksums are verified. Review the respective SDK/tool licenses before use. The JSON JAR is a desktop test dependency only and is not bundled into the APK.

Output: `release/Spool-Factory.apk`, a versioned copy, and `release/SHA256SUMS.txt`.

## Signing

By default the build creates a **local development key** in ignored `signing/`. Keep that key if you want subsequent development builds to update each other. It is not the maintainer's release key. A development build cannot be installed over an official release signed with another certificate.

For a release build, configure these environment variables without committing their values:

- `SPOOL_SIGNING_KEY`: absolute path to your private keystore.
- `SPOOL_SIGNING_PASSWORD`: keystore/key password.
- `SPOOL_SIGNING_ALIAS`: key alias (default `spool-factory`).

Private keys and passwords must never be uploaded to GitHub. CI compiles with its own temporary development key and uploads test build artifacts; it does not publish official releases.

`SPOOL_TOOLS_DIR` can point to an existing tools directory containing `jdk/`, `platform/`, `build/` and `json.jar` to avoid downloading those tools again.

## Test coverage

`test.ps1` tests feeder selection, ambiguity, timeout/cancellation, UID handling, synthetic HTTP integration, identity continuity and manual-action guards. All fixture inventory is synthetic. No physical commands are sent to a real printer.

Optional read-only checks use `./test.ps1 -Live`, with your own `SPOOL_PRINTER_URL` and `SPOOLMAN_URL` environment variables. These checks require reachability and deployments that permit those read requests without additional keys. Never put private deployment addresses or credentials in the repository.

Visual tests require Node.js and Chrome:

```powershell
npm install --prefix tools/ui-test playwright-core --no-audit --no-fund
node tests/ui-test.cjs
```

The test uses the standard Windows Chrome path; adjust it locally for another browser installation. Screenshots are saved to ignored `build/ui/` with synthetic sample data. They test the bundled interface in a desktop browser; they are not a substitute for Android and hardware validation.

## Layout

- `src/`: native NFC, foreground monitor, HTTP integration, settings and native confirmations.
- `assets/`: offline pixel-art interface, animation, font/license and illustration.
- `res/`, `AndroidManifest.xml`: icon, permissions and application metadata.
- `tests/`: Java and browser regression tests and synthetic fixtures.
- `docs/`: the GitHub Pages landing page and public documentation images.
