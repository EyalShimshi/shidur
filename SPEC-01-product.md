# SPEC-01 · Product Requirements — Shidur · שידור

| | |
|---|---|
| **Status** | Draft v0.1 — for review |
| **Date** | 2026-10-26 |
| **Related** | [Overview](SPEC.md) · [02 Design](SPEC-02-design.md) · [03 Architecture](SPEC-03-architecture.md) · [04 Catalog](SPEC-04-catalog.md) · [05 Quality & Release](SPEC-05-quality-release.md) · [06 Roadmap & Risks](SPEC-06-roadmap-risks.md) |

Conventions: **MUST / SHOULD / MAY** as in RFC 2119.
Priorities: **P0** = required for 1.0 · **P1** = ships in 1.0 *best-effort per station* (depends on what each broadcaster exposes) · **P2** = backlog.

---

## 1. Summary

**Shidur** (Hebrew *שידור*, "broadcast") is a free, ad-free, open-source (GPL-3.0) Android app for listening live to **Israeli radio stations**. It looks and behaves like a first-party Android app: Material 3 Expressive design, dynamic color, adaptive layouts, and full **Hebrew (RTL) and English** UI. It integrates with Android Auto, a home-screen widget, Google Cast, Wear OS, and Bluetooth devices.

There are no accounts, ads, or analytics. Stations come from a **curated catalog in the project's GitHub repository**. Automated jobs keep that catalog healthy, so stream fixes reach users without an app update.

## 2. Problem

- Israeli stations are fragmented across separate official apps and sites (Kan, Galei Tzahal, 103FM, Eco99, 100FM…), each with its own ads, UI, and quality bar.
- Global aggregators have stale, duplicate, or broken Israeli streams and weak RTL support. In Radio Browser, the largest open directory, **31 of the 186 IL-tagged entries (~17%) were flagged broken** on 2026-10-26, and major broadcasters have up to 9 duplicate entries each (see [SPEC-04 §2](SPEC-04-catalog.md#2-source-evaluation)).
- No single app is well-maintained, privacy-respecting, Hebrew-first, and covers all Israeli radio.

## 3. Goals & non-goals

### 3.1 Goals

| ID | Goal | Target (how measured) |
|---|---|---|
| G1 | **Complete coverage** | All Israeli licensed FM stations (Kan, Galei Tzahal/Galgalatz, Second Authority regional licensees) plus notable Israeli digital stations, ~45–60 entries at 1.0 (catalog count) |
| G2 | **Streams that work** | ≥ 97% of catalog stations pass the automated health check on any given day (catalog health report) |
| G3 | **Fast to audio** | Tap → audible: p50 ≤ 2 s, p90 ≤ 4 s on Wi-Fi/4G for healthy streams (instrumented tests, debug overlay) |
| G4 | **Feels native** | M3 Expressive, dynamic color, adaptive layouts, correct RTL; 0 untranslated strings in he/en (lint) |
| G5 | **$0 to run, low maintenance** | $0 running cost; the only costs are one-time registration fees (US $25 Android developer verification, needed for public sideloading as Google enforces it globally from 2027; US $25 Google Play, later). ≤ ~1 h/month of catalog upkeep (automation opens issues/PRs; the maintainer reviews and merges) |
| G6 | **Private by design** | No accounts, analytics, ads, or trackers; crash reports only with explicit per-report consent (ACRA) |

### 3.2 Non-goals (v1)

- Non-Israeli stations in the catalog. Users can still add any stream as a custom station ([F-11](#f-11-custom-station-by-url)).
- Podcasts, on-demand/catch-up programs, video/TV, lyrics.
- FM hardware tuner support.
- Accounts, cloud sync, social features, monetization.
- Any server that proxies, re-streams, or records audio. Playback always goes **directly from the broadcaster's server to the device**.

## 4. Personas

| Persona | Context | Key needs |
|---|---|---|
| **Noa, 34 — commuter** (Ramat Gan) | Drives daily with Android Auto; Galgalatz, 103FM | Instant start in the car; steering-wheel next/previous switches favorites; auto-play when the car connects |
| **Shlomo, 72 — loyal listener** (Be'er Sheva) | Kan Moreshet, Radio Darom; large font size | Simple UI, big targets, home-screen widget, sleep timer at night, streams that "just work" |
| **Maya, 26 — music lover** (Haifa) | Eco99, Kan 88, 100FM digital channels; Pixel + Wear OS + Nest speaker | Song info, Cast to speakers, watch controls, dark/AMOLED theme |
| **Daniel, 45 — expat** (Berlin) | Kan Bet news, Galei Tzahal; English or Hebrew UI | Works abroad, schedules in local time, data saver while roaming |
| **Rami, 38 — Arabic speaker** (Nazareth) | A-Shams, Makan | Arabic stations easy to find; Arabic names render correctly (Arabic UI is on the backlog) |

## 5. Release scope (v1.0)

| ID | Feature | Priority |
|---|---|---|
| [F-01](#f-01-browse-stations-by-category) | Browse stations by category | P0 |
| [F-02](#f-02-search) | Search (Hebrew / English / frequency) | P0 |
| [F-03](#f-03-favorites) | Favorites with drag-to-reorder | P0 |
| [F-04](#f-04-recently-played) | Recently played | P0 |
| [F-05](#f-05-background-playback--system-media-controls) | Background playback & system media controls | P0 |
| [F-06](#f-06-now-playing-info) | Now-playing info (song / program) | **P1** |
| [F-07](#f-07-sleep-timer) | Sleep timer | P0 |
| [F-08](#f-08-android-auto) | Android Auto | P0 |
| [F-09](#f-09-home-screen-widget) | Home-screen widget | P0 |
| [F-10](#f-10-google-cast) | Google Cast | P0 |
| [F-11](#f-11-custom-station-by-url) | Custom station by URL | P0 |
| [F-12](#f-12-program-schedule) | Program schedule | **P1** |
| [F-13](#f-13-data-saver) | Data saver | P0 |
| [F-14](#f-14-auto-resume-on-headphones--car-bluetooth) | Auto-resume on headphones / car Bluetooth | P0 |
| [F-15](#f-15-share-a-station-link) | Share a station link | P0 |
| [F-16](#f-16-wear-os-controls) | Wear OS controls | P0 (system media controls) |
| [F-17](#f-17-settings--personalization) | Settings & personalization | P0 |
| [F-18](#f-18-catalog-updates--offline-snapshot) | Catalog updates & offline snapshot | P0 |
| [F-19](#f-19-feedback-problem-reports--crash-reports) | Feedback, problem reports & opt-in crash reports | P0 |
| [F-20](#f-20-first-run-experience) | First-run experience | P0 |

## 6. Functional requirements

### F-01 Browse stations by category

*As a listener, I want to browse all Israeli stations by category so I can discover what's available.*

- **R1** The Browse tab MUST show a scrollable row of filter chips built from catalog **collections**. Collections are data-driven, ordered, and have he/en labels, e.g. *All · Kan · Galei Tzahal · Regional · News & Talk · Music · Mizrahi · Religious · Arabic · Other languages · Digital*. Changing the taxonomy needs no app release.
- **R2** Each station row MUST show: logo (or a generated monogram), localized name, primary frequency label (e.g. "88.0 FM"), a "now" line (current song or program, when known), a favorite toggle, and a playing indicator.
- **R3** Sort options: *Recommended* (catalog editorial order, default) · *A–Z* · *Frequency* (dial order; digital-only stations last).
- **R4** Tap = play (the mini-player appears). Long-press or overflow = station info sheet.
- **R5** MUST work offline from the cached/bundled catalog: lists render, and Play shows an offline error.
- **AC1** Selecting *Kan* lists exactly the stations whose network is Kan.
- **AC2** On first launch in airplane mode, Browse lists all bundled stations.
- **AC3** In the Hebrew UI, rows are mirrored and "88.0 FM" renders without bidi artifacts.

### F-02 Search

- **R1** A search bar sits at the top of Home and Browse and expands into a full-screen search view.
- **R2** MUST match on: he/en names, short names, catalog **aliases** (transliterations and nicknames, e.g. *גלצ*, *galatz*, *reshet bet*), frequency (*88*, *88.0*, *88fm*), network, and city/region.
- **R3** Hebrew normalization: ignore niqqud, geresh/gershayim and quote characters (*צה"ל* = *צהל*), final-letter forms (ך→כ, ם→מ, ן→נ, ף→פ, ץ→צ), punctuation, and whitespace. Latin matching ignores case and diacritics.
- **R4** Ranking: exact frequency > name prefix > alias prefix > substring. Results update on every keystroke (< 50 ms for ≤ 1,000 stations).
- **R5** Keep the last 10 searches; the list is clearable.
- **R6** If the query looks like an `http(s)` URL, offer *Add as custom station* ([F-11](#f-11-custom-station-by-url)).
- **AC1** "88" → Kan 88 ranks first. **AC2** "glglz", "galgalatz", "גלגלצ", and "גלגל"צ" all find Galgalatz. **AC3** "צהל" finds Galei Tzahal.

### F-03 Favorites

- **R1** Favorite toggles are available from station rows, the player, the station info sheet, and the notification / Android Auto custom action.
- **R2** The Favorites tab shows an adaptive grid of large logo tiles. Long-press and drag reorders. An edit mode adds drag handles and a remove action.
- **R3** Accessible reordering MUST be possible via TalkBack custom actions (*Move up / Move down / Move to top*).
- **R4** Favorites order drives the widget favorites, the Android Auto *Favorites* tab, and next/previous station when playback started from Favorites.
- **R5** Favorites survive catalog updates because station IDs are stable. If a station leaves the catalog, its tile shows *No longer available* with a remove action. If the catalog declares `replacedBy`, the favorite migrates automatically.
- **AC1** Order persists across process death and app updates. **AC2** Reordering works using TalkBack only.

### F-04 Recently played

- **R1** Keep the last 20 distinct stations, most recent first. A station is recorded after ≥ 10 s of audible playback, so accidental taps don't count.
- **R2** Shown on Home and as the Android Auto *Recent* tab.
- **R3** Single entries can be removed; *Clear history* lives in Settings.

### F-05 Background playback & system media controls

- **R1** Playback continues in the background and with the screen off, via a media foreground service. The system media notification MUST show logo, station, now-playing text, play/pause, previous/next station, and a custom *Favorite* action.
- **R2** Lock-screen and Quick Settings media controls, Bluetooth/wired headset buttons, and steering-wheel controls MUST all work.
- **R3** Previous/next MUST cycle through the list the station was started from (Favorites, a category, Recents, or search results) and wrap around.
- **R4** Audio focus: pause on calls and other media; duck for navigation prompts; resume after a transient loss.
- **R5** *Becoming noisy*: pause when headphones disconnect.
- **R6** Live semantics: resuming after a pause MUST rejoin the live edge with no stale audio. Time-shift is on the backlog.
- **R7** Resilience: on network drop or handover, auto-reconnect with exponential backoff for up to ~2 min while showing *Reconnecting…*. After repeated failures on one URL, fall back to the station's next stream URL. If everything fails, show an actionable error (*Retry · Report*).
- **R8** After process death or reboot, the system *resume media* card and the headset/car Play button MUST restart the last station (playback resumption).
- **AC1** 60 min of screen-off playback on a stable network without interruption. **AC2** Recovery from a Wi-Fi → cellular handover within 5 s. **AC3** Unplugging headphones pauses playback.

### F-06 Now-playing info

**P1 (best effort).** Availability varies by station. As of 2026-10-26, research confirmed song-level data only from 100FM's digital channels; other stations offer program-level data or nothing (see [SPEC-04 §7](SPEC-04-catalog.md#7-now-playing--schedule-data)).

- **R1** Metadata sources, tried in the order declared per station in the catalog:
  1. in-stream ICY `StreamTitle`;
  2. HLS ID3 tags;
  3. a station-specific HTTP now-playing endpoint;
  4. the current program from the schedule ([F-12](#f-12-program-schedule));
  5. fallback: frequency or slogan.
- **R2** Shown in the player, mini-player, notification, lock screen, Android Auto, widget, and (through the media session) on Wear OS.
- **R3** Catalog-driven clean-up per station: split "Artist – Title", strip ads/slogans by regex, decode Hebrew correctly (UTF-8 / Windows-1255), and isolate bidi text.
- **R4** (P1) Song actions: *Copy*, and *Search this song* (music-app search intent with a web-search fallback).
- **AC1** For stations whose catalog entry declares a metadata source, changes appear within 15 s. **AC2** No mojibake in Hebrew metadata for stations with a declared charset.

### F-07 Sleep timer

- **R1** Presets 15 / 30 / 45 / 60 / 90 / 120 min, a custom duration, and *End of current program* when schedule data exists.
- **R2** Remaining time shows as a chip in the full player and as an indicator in the mini-player, with *+10 min* and *Cancel*.
- **R3** Optional 30-second volume fade-out (default on); then pause and release the foreground service.
- **R4** The timer lives in the playback service, so it keeps running when the UI is closed.
- **AC1** Fires within ±5 s with the app UI closed.

### F-08 Android Auto

- **R1** Browse tabs: *Favorites* (grid), *Recent* (list), *Categories* (browsable → station lists).
- **R2** Search by keyboard (when parked) and by voice ("Play Galgalatz on Shidur", or the Hebrew equivalent), resolved through names and aliases.
- **R3** Now Playing shows station, now-playing text, and artwork, with previous/next station and a *Favorite* custom action.
- **R4** MUST meet the Android for Cars media-app quality guidelines; verified with the Desktop Head Unit (DHU).
- *Note:* Sideloaded builds appear in Android Auto only after the user enables *Unknown sources* in Android Auto's developer settings. Regular users need the Google Play build (see [SPEC-05](SPEC-05-quality-release.md)).

### F-09 Home-screen widget

- **R1** A resizable widget with three layouts:
  - **small:** logo + play/pause;
  - **medium:** logo, station, now-playing text, previous / play / next;
  - **large:** medium plus up to 8 favorites.
- **R2** Uses dynamic color when enabled, supports light/dark, and mirrors in RTL.
- **R3** Tapping a favorite starts it without opening the app; tapping the body opens the full player.
- **R4** After a reboot, shows the last station in a paused state.
- **AC1** The widget reflects playback state changes within 2 s.

### F-10 Google Cast

- **R1** A Cast button appears when Cast devices are discovered: in the player's top bar and the mini-player overflow.
- **R2** While connected, playback runs on the receiver, and the app, notification, lock screen, widget, and Auto controls drive the Cast session. Switching stations while casting works.
- **R3** Uses the station's cast-compatible stream (HTTPS plus a supported codec). If a station has none, show *This station can't be cast* with a short explanation.
- **R4** On disconnect, local playback ends up paused (it does not auto-resume).
- **R5** On devices without Google Play services, the Cast UI is hidden and everything else works.

### F-11 Custom station by URL

- **R1** Entry points: Favorites FAB menu (*Add by URL*), the search URL suggestion ([F-02 R6](#f-02-search)), and Settings.
- **R2** Fields:
  - stream URL (required);
  - name (required; auto-filled from `icy-name` when present);
  - logo URL (optional; otherwise a monogram);
  - category (optional).
- **R3** *Test* resolves PLS / M3U / M3U8 / direct URLs, verifies that the response is audio, and plays a short preview. *Save* is enabled after a successful test, or via an explicit *Save anyway*.
- **R4** Custom stations are stored only on the device. They are auto-added to Favorites, editable and deletable, labeled *Custom*, searchable, and reach Android Auto and the widget through Favorites.
- **AC1** An Icecast MP3 URL, a `.pls` file, and an HLS `.m3u8` can each be added and played.

### F-12 Program schedule

**P1 (best effort).** Schedules come from the catalog pipeline's per-broadcaster adapters (Kan, Eco99, 103FM, … — see [SPEC-04 §7](SPEC-04-catalog.md#7-now-playing--schedule-data)).

- **R1** For stations with schedule data, the player shows *Now: ‹program› · until 16:00* and *Next: …*. A schedule sheet lists today and tomorrow.
- **R2** Times display in the device time zone. When that differs from `Asia/Jerusalem`, Israel time is shown too.
- **R3** Station rows MAY show the current program.
- **R4** Missing or stale data hides the schedule UI silently; it never shows an error.
- **AC1** The current program is highlighted correctly across midnight and across DST transitions.

### F-13 Data saver

- **R1** Setting: *Off · On mobile data* (default) *· Always*. The system Data Saver is respected as well.
- **R2** When active:
  - pick the lowest-bitrate stream variant, where the catalog lists more than one (most Icecast stations offer a single bitrate);
  - cap HLS audio at ≈64 kbps when variants exist;
  - load smaller logos;
  - defer catalog and schedule refreshes to unmetered networks (except on first run).
- **R3** The player's stream-info line shows the current bitrate and a *Data saver* badge.
- **AC1** On a metered network with the default setting, a station that has 64k and 128k variants plays the 64k variant.

### F-14 Auto-resume on headphones / car Bluetooth

- **R1** Settings → *Start playing automatically when…*. Both options are off by default:
  - (a) **a chosen Bluetooth device connects** (car kit, headphones, speaker). Devices are linked through Android's **companion-device picker**, a system dialog, and the user can link several;
  - (b) wired headphones are plugged in — works only while Shidur's process is alive (platform limitation; see [SPEC-03 §9](SPEC-03-architecture.md#9-auto-resume-bluetooth--headphones)).
- **R2** Plays the last station. A per-device station is on the backlog.
- **R3** No runtime permission prompt. Linking a device happens through the system companion-device dialog, which is the user's consent. This is the platform-sanctioned way for an app to start playback in the background when a specific device connects; Android 12+ doesn't allow it on a plain Bluetooth broadcast.
- **R4** MUST NOT auto-start during a phone call, and MUST NOT auto-start more than once per connection.
- **R5** Independently of this setting, the playback-resumption path ([F-05 R8](#f-05-background-playback--system-media-controls)) lets cars and headsets that send PLAY on connect start the last station, with no permission needed.
- **AC1** With (a) enabled and the car's head unit linked, connecting it starts playback within 5 s on the reference devices (Pixel and Samsung, Android 12–16). OEM battery-optimization caveats are documented in the in-app help.

### F-15 Share a station link

- **R1** Share from the player or station info → system share sheet with localized text and a link of the form `https://<owner>.github.io/shidur/s/<stationId>`.
- **R2** If Shidur is installed, the link opens that station in the player, paused; the user taps Play. Otherwise, a lightweight GitHub Pages landing page shows the station and a *Get Shidur* link (GitHub Releases now, Play later).
- **R3** Custom stations are shared as plain text (name + stream URL), with no landing page.
- **R4** (P1) *Share song* when now-playing info is known.

### F-16 Wear OS controls

- **R1** v1: a paired Wear OS watch controls playback (play/pause, previous/next station, volume) through the **system media controls** that mirror Shidur's media session. No Wear app ships in v1.
- **R2** Backlog: a standalone Wear OS app with a favorites list, tile, and complication.
- **AC1** Verified on a Wear OS 5+ watch paired with a reference phone.

### F-17 Settings & personalization

- **Appearance:** theme (System / Light / Dark), *Pure black* dark theme (AMOLED), dynamic color (default on), and language (System / עברית / English) via per-app language preferences.
- **Playback:** data saver ([F-13](#f-13-data-saver)), auto-resume ([F-14](#f-14-auto-resume-on-headphones--car-bluetooth)), sleep-timer fade-out, and *Stop playback when the app is swiped away* (default off).
- **Stations:** last catalog update with *Check now*, *Suggest a station*, manage custom stations, clear history.
- **Privacy:** crash reports — *Ask* (default) / *Never* ([F-19](#f-19-feedback-problem-reports--crash-reports)).
- **About:** version and changelog, GitHub link, licenses (GPL-3.0 plus third-party), the *unofficial app* disclaimer, and the takedown policy.

### F-18 Catalog updates & offline snapshot

- **R1** Each APK bundles the latest catalog snapshot from build time, so everything except streaming works without a network.
- **R2** The app refreshes the catalog from remote mirrors at most every 24 h (background job, conditional GET) and on demand. It only accepts catalogs whose major `schemaVersion` it supports.
- **R3** Catalog changes apply **without an app update**: new or removed stations, stream URLs, collections, aliases, and metadata/schedule adapters.
- **R4** The catalog can remotely mark a station `disabled`, e.g. after a takedown request.

### F-19 Feedback, problem reports & crash reports

- **R1** *Report a problem* (global and per-station) opens a pre-filled GitHub issue containing app version, Android version, device model, station ID, the stream URL tried, and the last error code. Email is offered as an alternative for users without a GitHub account.
- **R2** *Suggest a station* opens a GitHub issue form.
- **R3** Crash reports use ACRA. After a crash, a dialog offers to send the report through email or the share sheet. The user sees the content before sending, and nothing uploads automatically. The setting is *Ask* (default) / *Never*.
- **R4** Reports MUST NOT contain personal data or custom-station URL query strings (they may hold tokens).

### F-20 First-run experience

- **R1** Welcome screen (name, one-line value proposition, *unofficial app* disclaimer) → optional *Pick your stations* grid (multi-select → Favorites) → Home. Skippable; at most 2 screens.
- **R2** No permission prompts on first run. The media notification needs no runtime permission (verified at M0, see [SPEC-03 §12](SPEC-03-architecture.md#12-permissions--platform-requirements)), and auto-resume links devices through a system dialog only when the user enables it.

## 7. Non-functional requirements

| ID | Area | Requirement |
|---|---|---|
| NFR-01 | Performance | Cold start to interactive Home ≤ 1.0 s (p50) on a Pixel 6a-class device with baseline profiles (Macrobenchmark) |
| NFR-02 | Performance | Time to first audio p50 ≤ 2 s / p90 ≤ 4 s for healthy streams |
| NFR-03 | Performance | Browse scrolls with < 1% janky frames (`FrameTimingMetric`) |
| NFR-04 | Reliability | ≥ 99.5% crash-free users (ACRA reports before Play; Play vitals after) |
| NFR-05 | Battery | No wake locks, Wi-Fi locks, or polling while paused; now-playing polling only while playing, at ≥ 20 s intervals |
| NFR-06 | Data | Compressed catalog fetch ≤ 100 KB with conditional requests; logo disk cache ≤ 50 MB (LRU) |
| NFR-07 | Size | Universal APK ≤ 15 MB |
| NFR-08 | Compatibility | minSdk 31 (Android 12); targetSdk = latest stable; phones, tablets, foldables, ChromeOS (keyboard/mouse usable) |
| NFR-09 | Accessibility | WCAG 2.2 AA contrast; complete TalkBack support; 48 dp touch targets; font scale up to 200%; respects *Remove animations* |
| NFR-10 | Localization | Complete Hebrew and English; RTL-correct layouts; no string concatenation; pseudo-locale (`ar-XB`) checks |
| NFR-11 | Privacy | No accounts, analytics, ads, or trackers; network access limited to stream hosts, logo hosts, catalog mirrors, now-playing endpoints, Google Cast, and (GitHub builds only) the GitHub Releases API for the update check |
| NFR-12 | Security | HTTPS for all non-stream traffic (catalog, schedules). Cleartext is allowed only because many radio streams are HTTP-only. R8 enabled; Gradle dependency verification |
| NFR-13 | Legal | Never proxy or re-stream audio; *unofficial app* disclaimer; documented takedown process |
| NFR-14 | Maintainability | Unit, UI, and screenshot tests run in CI; static analysis clean; catalog changes need no app release |

## 8. Backlog (post-1.0, P2)

Roughly ordered by value:

1. Alarm clock: wake up to a station.
2. Quick Settings tile: play/pause the last station.
3. Favorites import/export (file / M3U).
4. Standalone Wear OS app: favorites, tile, complication.
5. Pause & rewind live radio (time-shift buffer). 100FM's digital channels already expose server-side time-shift streams.
6. Per-device auto-resume station (e.g. car → Galgalatz, headphones → Kan 88).
7. Arabic and Russian UI translations (community-driven via Weblate).
8. Android Automotive OS (built-in car) build.
9. *Discover more*: community stations queried live from Radio Browser, clearly labeled.
10. Equalizer / audio effects; recording to a file (needs legal review first).

## 9. Assumptions & dependencies

- Broadcasters keep publishing free public streams. Shidur only **links** to them.
- GitHub (repo, Actions, Pages, Releases) stays free for public repositories; jsDelivr serves as a secondary mirror.
- Google Play services is present for Cast. Cast is optional; the app works without it.
- Which streams carry in-stream metadata is **unverified** until the M0 metadata spike ([SPEC-06](SPEC-06-roadmap-risks.md)).
- Android's **developer verification** requirement will cover sideloaded apps globally from 2027. Shidur registers its package and signing key before 1.0 ([SPEC-03 §14.1](SPEC-03-architecture.md#141-android-developer-verification-applies-to-github-distributed-apks)).
