# SPEC-05 · Quality, Release & Operations

| | |
|---|---|
| **Status** | Draft v0.1 — for review |
| **Date** | 2026-10-26 |
| **Related** | [Overview](SPEC.md) · [01 Product](SPEC-01-product.md) · [03 Architecture](SPEC-03-architecture.md) · [04 Catalog](SPEC-04-catalog.md) · [06 Roadmap & Risks](SPEC-06-roadmap-risks.md) |

Decisions taken with the maintainer:
- **Quality:** unit tests, Compose UI tests, screenshot tests (Roborazzi), GitHub Actions CI, static analysis (Detekt + ktlint/Spotless), baseline profiles.
- **Telemetry:** opt-in crash reports via **ACRA** (email or share sheet).
- **License:** **GPL-3.0**.
- **Distribution:** **GitHub Releases** (signed APK, works with Obtainium) now; **Google Play** later.

---

## 1. Test strategy

| Layer | Scope (examples) | Tooling | Runs |
|---|---|---|---|
| **Unit (JVM)** | Search normalization & ranking (F-02); catalog parsing, validation, merge, mass-deletion guard (F-18); PLS/M3U resolver; ICY title parsing & Windows-1255 repair (F-06); now-playing field paths; schedule logic across midnight/DST (F-12); sleep timer with a fake clock (F-07); data-saver stream choice (F-13); auto-resume rules (F-14); favorites ordering & migration (F-03) | JUnit 4, Truth, Turbine, kotlinx-coroutines-test; **fakes over mocks** (MockK only where unavoidable) | Every PR |
| **Integration (Robolectric)** | Room DAOs and migrations; DataStore; catalog sync against MockWebServer (ETag, mirror fallback, bad schema); playback service with the Media3 test utilities (`TestExoPlayerBuilder`, `FakeClock`, `TestPlayerRunHelper`) against **MockWebServer fixtures**: an ICY MP3 stream with Hebrew Windows-1255 metadata, an HLS stream, a redirecting `.pls`, a dead URL that triggers fallback | Robolectric, OkHttp MockWebServer, Media3 test-utils | Every PR |
| **Compose UI** | Navigation; play from Browse; search; favorites reorder via touch **and** TalkBack custom actions; settings toggles; RTL layout; error/offline states | `createComposeRule` on Robolectric, plus a small instrumented smoke suite | PR (Robolectric) · nightly (instrumented) |
| **Screenshot** | Every screen and key component across the [SPEC-02 §10](SPEC-02-design.md#10-design-workflow--deliverables) matrix: light/dark/pure-black × en/he (LTR/RTL) × font 1.0/2.0 × compact/expanded | **Roborazzi** (JVM); golden PNGs in the repo (Git LFS if they grow) | Every PR (`verifyRoborazzi…`) |
| **Accessibility** | Automated checks on UI tests; lint a11y rules; manual TalkBack pass per release | Compose UI-test accessibility checks, Android Lint, Accessibility Scanner | PR + release |
| **Performance** | Cold start and Browse scrolling (NFR-01, NFR-03); baseline-profile generation | Macrobenchmark, `androidx.baselineprofile` plugin (Gradle Managed Device) | Profiles: per release · Benchmarks: on a physical device before releases |
| **Catalog tools** | Schema validation; build golden outputs; health-check classifier; schedule adapters against **recorded fixtures** (HTML/JSON snapshots) | JUnit + fixtures | PRs touching `catalog*/**` |
| **Manual test plans** | Android Auto on the Desktop Head Unit; Cast to a Chromecast/Nest device; Wear OS controls (emulator pairing + a real watch if available); Bluetooth auto-resume on Pixel and Samsung; widget on Pixel Launcher and One UI; tablet and foldable postures | Checklists in `docs/testing/*.md` | Before each minor release |

**Coverage goals:** ≥ 80% line coverage for `:core:model`, `:core:data`, and `:core:playback` logic (Kover). UI is covered through screenshot and UI tests rather than a line-coverage target.

## 2. Static analysis & code health

- **Formatting:** Spotless + ktlint, with an auto-fix task and a check in CI.
- **Detekt** with the Compose rules set (`io.nlopez.compose.rules`).
- **Android Lint** with `warningsAsErrors` for: `MissingTranslation`, `HardcodedText`, `RtlHardcoded`, `RtlEnabled`, `UnusedResources`, and the accessibility checks.
- **Dependencies:**
  - Gradle **dependency verification** (`verification-metadata.xml`);
  - a version catalog (`libs.versions.toml`);
  - a CI **deny-list task** that fails if a tracker or ads SDK shows up in the dependency graph (NFR-11).
- **Module boundaries:** `:core:*` modules never depend on `:app`; `:core:model` stays pure Kotlin.
- **Dependency updates:** Renovate (or Dependabot) runs weekly, grouping AndroidX, Kotlin/Compose, and GitHub Actions updates.

## 3. CI/CD (GitHub Actions)

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | PR, push to `main` | JDK 21 + `gradle/actions/setup-gradle` (cache) → `spotlessCheck detekt lint testDebugUnitTest verifyRoborazziDebug assembleDebug` + `:catalog-tools:test`; upload reports; post a PR summary |
| `instrumented.yml` | nightly, pre-release | Gradle Managed Devices (ATD images, API 31 + latest) → smoke UI tests |
| `baseline-profile.yml` | manual dispatch, pre-release | Generate baseline + startup profiles → open a PR with the updated profiles |
| `release.yml` | tag `v*` | Build the **signed** release APK (and an AAB once Play is in place) → `apksigner verify` → `SHA256SUMS` → GitHub Release with he/en notes from `CHANGELOG.md`. Runs in a protected `release` environment that requires the maintainer's approval |
| `catalog-*.yml` | see [SPEC-04 §6](SPEC-04-catalog.md#6-automation-github-actions) | Catalog validate / health / sync / schedules / publish / keepalive |

## 4. Versioning & release process

- **SemVer** `MAJOR.MINOR.PATCH`, with `versionCode = MAJOR·1,000,000 + MINOR·1,000 + PATCH`.
- **Pre-releases** are tagged `v1.1.0-beta.1` and published as GitHub *pre-releases*. Obtainium users can opt in to them.
- **Changelogs:** `CHANGELOG.md` (Keep a Changelog), plus Fastlane-style metadata: `fastlane/metadata/android/{en-US,iw-IL}/changelogs/<versionCode>.txt`, store texts, and screenshots. The same tree later feeds Google Play (Gradle Play Publisher) and any F-Droid-style repository.
- **Cadence:** milestone releases until 1.0 ([SPEC-06](SPEC-06-roadmap-risks.md)); after that, roughly monthly minor releases and patch releases as needed. Catalog fixes never need an app release.
- **Release checklist:**
  1. CI green, including the nightly instrumented run.
  2. Manual plans executed (Auto, Cast, Wear, Bluetooth, widget, tablet/foldable).
  3. Screenshot diffs reviewed.
  4. The catalog snapshot builds and validates at the tagged commit.
  5. Baseline profiles are fresh.
  6. Changelog written in Hebrew and English.
  7. Tag, approve the release job, then verify the signing certificate fingerprint matches the published one.

## 5. Signing & key management

**This must be decided before the first public APK.**

- Generate **one long-lived app signing key** now: RSA 4096, valid ≥ 30 years. Store it in a password manager plus two encrypted offline backups. CI gets it only through secrets of the protected `release` environment.
- Publish the **certificate SHA-256 fingerprint** in the README so users and Obtainium can verify APKs.
- **Moving to Google Play later:** enroll in Play App Signing with **your existing key** (exported with Google's PEPK tool), and create a separate **upload key** for Play.
  - Result: GitHub and Play builds share one signature, so users can switch between sources and keep receiving updates.
  - If Google generates a fresh key instead, GitHub and Play builds become **incompatible**, and users would have to uninstall to switch.
- **Package name:** `io.github.<owner>.shidur`. It is permanent once published.
- **Rotation:** APK Signature Scheme v3 supports key rotation if it is ever needed.
- **Android developer verification:** before 1.0, register the package name and this key's SHA-256 fingerprint in Google's *Android Developer Console* (Full distribution: one-time US $25 + ID). From 2027, Google enforces this globally for sideloaded apps on certified devices. Details are in [SPEC-03 §14.1](SPEC-03-architecture.md#141-android-developer-verification-applies-to-github-distributed-apks); risk R-09 in [SPEC-06](SPEC-06-roadmap-risks.md).
- **Play's hybrid signing:** new Play apps are auto-enrolled in *quantum-ready hybrid signing* (RSA + ML-DSA, scheme v3.2, Android 17+). Confirm at Play onboarding that GitHub and Play APKs remain update-compatible.

## 6. Distribution

### 6.1 GitHub Releases (primary for 1.x)

- **Artifact:** one universal APK, `shidur-<version>.apk` (no native code), plus `SHA256SUMS`.
- **Obtainium:** the README carries an *Add to Obtainium* badge pointing at the repo. Updates are picked up from GitHub Releases automatically.
- **In-app update notice (GitHub builds only):**
  - An *Update available* banner on Home, from a check against the GitHub Releases API at most once a day.
  - *Settings → About → Check for updates* runs it on demand; a switch turns it off. Default: on.
  - The check reveals only the device's IP address to GitHub (stated in the privacy policy).
  - Compiled out of Play builds, because Play policy forbids self-update mechanisms.
- **Build config:** `DISTRIBUTION = github | play` controls update notices, links (GitHub vs Play listing), and Play-specific metadata.
- **Testers before 1.0:** pre-releases go to testers as normal APKs. Once developer verification is enforced in a tester's country, the free *Limited distribution* tier covers up to 20 invited devices.

### 6.2 Google Play (later)

| Topic | Plan |
|---|---|
| Account | One-time **US $25** registration fee and identity verification. New **personal** accounts must run a **closed test with ≥ 12 testers opted in for 14 consecutive days** before production access. Recruit testers from the community early |
| Target SDK | Meets Play's target-API requirement: API 36 for new apps and updates from 2026-11-01 ([SPEC-03 §2](SPEC-03-architecture.md#2-technology-stack)) |
| Data safety | *No data collected or shared*. Crash reports are user-initiated emails, declared conservatively as optional crash logs. Cast discovery is handled by Google Play services |
| Android Auto | Opt in to the Android Auto form factor in Play Console, which triggers a car quality review |
| Store listing | Hebrew + English; phone, 7″ and 10″ tablet screenshots; feature graphic; **unofficial-app disclaimer** in the description; privacy-policy URL on GitHub Pages |
| Policy alignment | Intellectual Property and Impersonation (neutral branding, disclaimer, takedown policy); no self-update (§6.1) |
| Publishing | Gradle Play Publisher uploads from CI to the **internal** track; promotion to production is manual |

### 6.3 F-Droid / IzzyOnDroid (not planned)

These would need a `foss` flavor **without** the Google Cast SDK (proprietary Play services) and, for F-Droid, builds from source. The architecture isolates Cast behind an interface in its own source set ([SPEC-03 §8](SPEC-03-architecture.md#8-google-cast)), so adding this later is cheap.

## 7. Crash reporting (ACRA, opt-in per report)

- **Modules:** `acra-mail` (send through an email intent) and `acra-dialog` (consent UI). No HTTP sender and no backend.
- **Flow:**
  1. The app crashes.
  2. Immediately, ACRA's dialog (running in its own process) asks: *"Shidur stopped unexpectedly. Send a crash report to help fix it?"*, with an optional comment field. It offers *Send* (email/share sheet with the report as text) and *Don't send*, plus a *Never ask* checkbox.
  3. The user sees and can edit everything before sending.
- **Report contents (allow-list):**
  - app version/code and distribution;
  - Android version, manufacturer/model;
  - stack trace, thread, timestamp, available memory;
  - `catalogVersion`;
  - current station ID (catalog stations only);
  - user comment.
- **Never included:** logcat, custom-station URLs, device or advertising IDs, account information.
- **Settings:** *Crash reports: Ask (default) / Never* (F-19).
- **Recipient:** a dedicated maintainer address (open question Q-02 in [SPEC-06](SPEC-06-roadmap-risks.md)).

## 8. Privacy

- **Privacy policy** in he/en, hosted on GitHub Pages and linked from About and the store listing:
  - No accounts. No personal data is collected by the developer.
  - The app talks **directly** to:
    - broadcasters' stream and now-playing servers (they see IP address and User-Agent);
    - GitHub Pages / jsDelivr / raw.githubusercontent.com (catalog, schedules, logos);
    - GitHub API (update check, GitHub builds only);
    - Google Play services (Cast discovery and sessions, when used).
  - Crash reports leave the device only when the user sends them.
- **User-Agent:** `Shidur/<version> (Android <release>; +https://github.com/<owner>/shidur)`. Honest, with no device identifiers.
- **Excluded:** no advertising ID, Firebase, Google Analytics, or other trackers; enforced by the CI deny-list (§2).

## 9. Licensing

| Item | License / treatment |
|---|---|
| App source code | **GPL-3.0-or-later**, with SPDX headers (REUSE-style). Choosing `-or-later` vs `-only` is open question Q-03 |
| Linking exception | A **GPL §7 additional permission** allowing the work to be combined with Google Play services (the Cast SDK). Without it, GPL compatibility of the proprietary Cast library is questionable. Wording to be reviewed before 1.0 |
| Contributions | **DCO** sign-off (`Signed-off-by`), no CLA |
| Catalog data (`catalog/`, excluding logos) | **CC0-1.0** ([SPEC-04 §10](SPEC-04-catalog.md#10-governance)) |
| Station logos | Trademarks of their owners, used for identification; listed in `catalog/LOGOS.md`; takedown policy |
| Fonts / icons | Heebo — SIL OFL 1.1; Material Symbols — Apache-2.0 |
| Third-party notices | In-app *Open-source licenses* screen generated by **AboutLibraries** (FOSS; no Play-services-based license plugin) |

## 10. Operations & community

- **Public status page:** `https://<owner>.github.io/shidur/status/`, generated from the `catalog-status` history. It shows per-station health and 30-day uptime, so users can check whether a station is down for everyone, and it doubles as the maintainer's dashboard.
- **Issue labels:** `bug`, `broken-stream`, `station-request`, `wrong-info`, `ux`, `a11y`, `l10n`, `catalog`, `good first issue`.
- **Templates:** issue forms for bug, broken stream, station suggestion, and wrong info; a PR template with a checklist.
- **Support:** GitHub Issues and Discussions, plus an email address for non-GitHub users.
- **Security:** `SECURITY.md` with GitHub private vulnerability reporting enabled.
- **Translations:** the maintainer covers he/en at first. Later, Hosted Weblate (free for libre projects) for Arabic and Russian (backlog).
- **Docs:** `README.md` (bilingual summary, screenshots, install via Obtainium/APK, fingerprint, disclaimer), `CONTRIBUTING.md` (including how to add a station), and `docs/testing/` manual plans.
