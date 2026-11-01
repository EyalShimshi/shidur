# Shidur · שידור — Specification

> **A free, open-source, ad-free Android app for listening live to Israeli radio, with a Material 3 Expressive design in Hebrew and English.**

| | |
|---|---|
| **Status** | Draft v0.1 — ready for review |
| **Date** | 2026-10-26 |
| **License (planned)** | App: GPL-3.0 · Catalog data: CC0-1.0 · Logos: trademarks of their owners |
| **Distribution (planned)** | GitHub Releases (APK, Obtainium) → Google Play later |

---

## Documents

| # | Document | Contents |
|---|---|---|
| 01 | [Product requirements](SPEC-01-product.md) | Vision, goals, personas, **20 features with requirements and acceptance criteria**, NFRs, backlog |
| 02 | [UX & design](SPEC-02-design.md) | M3 Expressive design system (color, type, shape, motion), IA, **wireframes for every screen**, system surfaces (notification, Auto, widget, Wear, Cast), RTL, accessibility |
| 03 | [Technical architecture](SPEC-03-architecture.md) | Stack and versions, modules, playback service, data layer, Android Auto, Cast, Bluetooth auto-resume, widget, Wear OS, deep links, platform requirements |
| 04 | [Station catalog & data pipeline](SPEC-04-catalog.md) | Source evaluation, schema, **$0 GitHub Actions pipeline** (health checks, drift detection, schedules), now-playing data, **seed station list**, legal |
| 05 | [Quality, release & operations](SPEC-05-quality-release.md) | Testing, static analysis, CI/CD, signing, GitHub/Play distribution, ACRA, privacy, licensing |
| 06 | [Roadmap, risks & open questions](SPEC-06-roadmap-risks.md) | Milestones M0–M5 (~18 weeks part-time), technical spikes, risk register, **open questions for you** |

## At a glance

- **What:** one app for every Israeli radio station: Kan, Galei Tzahal/Galgalatz, all regional licensees, and notable digital channels. About 45–50 stations at 1.0.
- **Experience:**
  - one tap to audio;
  - Home · Browse · Favorites, with search on top;
  - mini-player and full player tinted from the station logo;
  - dynamic color, AMOLED black, adaptive phone/tablet/foldable layouts;
  - full Hebrew RTL.
- **Integrations:** media notification, lock screen, Bluetooth/steering-wheel controls, Android Auto, home-screen widget, Google Cast, Wear OS (system media controls), auto-resume on car/headphones, share links.
- **Data that stays fresh for free:**
  - a curated station catalog in the GitHub repo, health-checked every 6 h by GitHub Actions;
  - cross-checked against Radio Browser;
  - published to GitHub Pages and the jsDelivr CDN, with a snapshot bundled in the APK;
  - broken streams open GitHub issues automatically, and fixes reach users without an app update.
- **Privacy:** no accounts, ads, analytics, or trackers. Crash reports are sent only when the user chooses to (ACRA via email).

## Decision log

| # | Topic | Decision | Source / rationale |
|---|---|---|---|
| D-01 | Content | **Israeli stations only.** Any other stream can be added as a custom station | Maintainer |
| D-02 | Station data | **Curated catalog in the repo** + automated health checks + Radio Browser cross-check + bundled snapshot. mivzaklive is used only for leads | Research: Radio Browser has ~17% broken IL entries and many duplicates; mivzaklive has no license and includes filler ([04 §2](SPEC-04-catalog.md#2-source-evaluation)) |
| D-03 | Languages | **Hebrew (RTL) + English** UI | Maintainer |
| D-04 | Accounts | **None.** Everything stays on the device | Maintainer |
| D-05 | Distribution | **GitHub Releases** (signed APK, Obtainium) first; **Google Play** later, sharing the same signing key | Maintainer; [05 §5](SPEC-05-quality-release.md#5-signing--key-management) |
| D-06 | Licensing | **GPL-3.0** + §7 permission for Google Play services (Cast); catalog **CC0**; logos used for identification under a takedown policy | Maintainer; [05 §9](SPEC-05-quality-release.md#9-licensing) |
| D-07 | Name | **Shidur · שידור** ("broadcast") | Maintainer |
| D-08 | Visual design | **M3 Expressive**; **dynamic color** with seed `#6750A4` fallback and a toggle; **pure-black** option; **station-tinted player** | Maintainer; [02 §2](SPEC-02-design.md#2-design-system) |
| D-09 | Navigation | **Home · Browse · Favorites** + search bar; adaptive bar / rail / supporting-pane player | Maintainer; [02 §4](SPEC-02-design.md#4-navigation--adaptive-layout) |
| D-10 | Platform | **minSdk 31** (Android 12); **compile/targetSdk 36** (Android 16; meets Play's 2026-11-01 rule) | Maintainer; [03 §2](SPEC-03-architecture.md#2-technology-stack) |
| D-11 | Stack | Kotlin 2.4, AGP 9.3, Compose 1.12 + **Material 3 1.5 (Expressive, experimental APIs wrapped)**, **Navigation Compose 2.10** (Nav3 after 1.0), Media3 1.11, Hilt, Room 2.8, DataStore, OkHttp + kotlinx.serialization, Coil 3, Glance 1.2, WorkManager | Versions checked 2026-10-26; [03 §2](SPEC-03-architecture.md#2-technology-stack) |
| D-12 | Structure | **Modest modularization:** `app` + `core:{model,data,playback,designsystem,testing}` + `widget` + `baselineprofile` + `catalog-tools` | Maintainer; [03 §3](SPEC-03-architecture.md#3-modules) |
| D-13 | Telemetry | **No analytics.** Opt-in **ACRA** crash reports via email or share sheet | Maintainer |
| D-14 | Quality | Unit, Compose UI, **Roborazzi** screenshot tests, CI, Detekt + ktlint/Spotless, baseline profiles | Maintainer; [05 §1](SPEC-05-quality-release.md#1-test-strategy) |
| D-15 | v1 scope | 16 user-facing features + 4 supporting ones. **Now playing (F-06) and schedules (F-12) are best effort per station** | Maintainer; [01 §5](SPEC-01-product.md#5-release-scope-v10) |
| D-16 | Wear OS (v1) | Use the **system media controls** driven by the phone's media session; a standalone Wear app is backlog | [01 F-16](SPEC-01-product.md#f-16-wear-os-controls) |
| D-17 | Hosting | **GitHub Pages + jsDelivr**: catalog, schedules, logos, share landing pages, status page, privacy policy. **$0** | [04 §3](SPEC-04-catalog.md#3-pipeline-overview) |
| D-18 | Now-playing & schedules | Real-time now playing in the app (ICY/ID3 + declarative HTTP adapters); schedules scraped once a day in the pipeline | [04 §7](SPEC-04-catalog.md#7-now-playing--schedule-data) |
| D-19 | Playback ethics | **Link only:** never proxy, re-stream, or record audio on a server | Kan Terms of Use; Play IP policy ([04 §12](SPEC-04-catalog.md#12-legal--policy-summary)) |
| D-20 | Bluetooth auto-start | Uses the **Companion Device Manager**: the user links devices in a system dialog, and presence events start playback. Plain Bluetooth broadcasts can't start a foreground service from the background on Android 12+ | Verified against Android's FGS restriction list; [03 §9](SPEC-03-architecture.md#9-auto-resume-bluetooth--headphones) |
| D-21 | Developer verification | Register the package and signing key in Google's Android Developer Console (Full distribution, one-time US $25 + ID) **before 1.0**; Google enforces this globally for sideloaded apps from 2027 | [03 §14.1](SPEC-03-architecture.md#141-android-developer-verification-applies-to-github-distributed-apks) |
| D-22 | Hebrew song titles | Decode ICY metadata from raw bytes with a per-station charset (Media3's decoder can't handle Windows-1255) | [03 §5.3](SPEC-03-architecture.md#53-now-playing-pipeline-f-06) |

## Next steps

1. **Review** these documents and answer the [open questions](SPEC-06-roadmap-risks.md#4-open-questions-for-the-maintainer), especially Q-01 (GitHub owner → package name), Q-08 (cut line), and Q-11 (developer verification fee).
2. **M0:** scaffold the project, set up CI, the theme, and catalog tooling, and run the six technical spikes ([06 §2](SPEC-06-roadmap-risks.md#2-technical-spikes-m0-time-boxed)).
3. Verify the seed station URLs from [04 §11](SPEC-04-catalog.md#11-seed-list-for-10) against each broadcaster's own player while writing the first `catalog/stations/*.yaml` files.

**Budget:** $0 running costs. One-time fees: US $25 for Android developer verification (before 1.0) and US $25 for Google Play (later).

## Implementation notes — first build (v0.1, 2026-10-26)

The first installable build intentionally simplifies parts of the plan. It was written without being able to compile locally and is verified by the CI build instead.

| Spec says | v0.1 does | Why / next step |
|---|---|---|
| Modest modularization ([03 §3](SPEC-03-architecture.md#3-modules)) | Single `:app` module whose packages mirror the planned modules (`model`, `data`, `playback`, `ui`, `widget`) | Fewer build files to get right blind; split into modules once the code stabilizes |
| Hilt + Room + DataStore | Manual `AppContainer`; `SharedPreferences` (favorites, recents, custom stations, settings) exposed as Flows | No annotation processing (KSP) in the first build. DataStore 1.1.5's preferences helpers didn't resolve with AGP 9's built-in Kotlin in the first CI build; revisit DataStore/Room when schedules arrive |
| M3 Expressive components | Stable Material 3 components (with a few expressive touches: animated play-button shape, station-tinted player) | Expressive APIs are still experimental; adopt after spike S-4 |
| Toolchain from [03 §2](SPEC-03-architecture.md#2-technology-stack) | AGP 9.2.1, Gradle 9.5.0, Kotlin 2.3.21, compileSdk 37, BOM 2026.09.00, Media3 1.11.1, Glance 1.1.1 | Versions copied from Google's CI-tested compose-samples, so the first build has the best chance of compiling |
| Station logos ([04 §9](SPEC-04-catalog.md#9-logos)) | Generated monogram artwork (short name on station color), served via `ArtworkProvider` | No trademark assets in the repo; real logos come with the catalog pipeline |
| Catalog pipeline on GitHub Pages ([04 §3](SPEC-04-catalog.md#3-pipeline-overview)) | Catalog bundled in the APK; the app also fetches `app/src/main/assets/catalog.json` from the repo (raw URL) when the repo is public | Pipeline (health checks, schedules) is milestone M2 |
| Bluetooth auto-start via CDM ([03 §9](SPEC-03-architecture.md#9-auto-resume-bluetooth--headphones)) | Not yet. Playback resumption (car/headset PLAY) and wired-headphone auto-play while the app is running are implemented | Needs spike S-3 on real devices |
| Distribution | Debug APK built by GitHub Actions and published as a GitHub pre-release per push | Signing key ceremony and release builds come at M5 |

## Glossary

| Term | Meaning |
|---|---|
| **Kan (כאן)** | Israeli Public Broadcasting Corporation; runs Kan Bet, Gimel, 88, Tarbut, Kol HaMusica, Moreshet, Reka, and Makan |
| **Galei Tzahal / Galgalatz** | IDF radio stations (news/talk and music/traffic) |
| **Second Authority** | Regulator of the licensed commercial and regional radio stations |
| **ICY metadata** | In-stream "now playing" titles (`StreamTitle`) sent by Icecast/Shoutcast servers |
| **HLS / ID3** | HTTP Live Streaming; ID3 tags can carry timed metadata inside HLS |
| **Media3** | AndroidX media library (ExoPlayer, MediaSession, MediaLibraryService) |
| **MediaLibraryService** | Media3 service exposing playback and a browse tree to system UI, Android Auto, Wear OS, and Assistant |
| **DHU** | Desktop Head Unit, the Android Auto emulator for testing |
| **Glance** | Jetpack library for building app widgets with Compose-style code |
| **M3 Expressive** | The 2025+ evolution of Material 3: richer shapes, springy motion, new components |
| **MCU** | Material Color Utilities, the library that generates M3 color schemes from a seed color |
| **Radio Browser** | Open community directory of internet radio stations (api.radio-browser.info) |
| **Obtainium** | Android app that installs and updates apps straight from GitHub Releases |
| **Play App Signing / PEPK** | Google Play's key management; PEPK exports an existing key so Play can keep using it |
| **App Links** | Verified https links that open directly in the app |
