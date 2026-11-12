# Maintaining Shidur

Notes for the maintainer. Users and contributors: see [README.md](README.md).

## Release flow

1. Commit and push to `main`: in VS Code, **Source Control → Commit → Sync Changes**, or `git push` in a terminal.
2. GitHub Actions builds the app, opens it and plays a station on an emulator, and then publishes `shidur-N.apk` as the pre-release **Shidur build N** (version `1.0-dev.N`), in about 10 minutes.
3. If the run fails (red ✗), open it: its **Summary** page shows the compiler or test errors, or what the smoke test saw, in a box, ready to copy.
4. **Official releases** come from a tag such as `v1.0.0` (GitHub → Releases → Draft a new release → new tag). The same workflow then publishes a full release with the APK and the Google Play app bundle (`.aab`). It refuses to run without the release key. Google Play: see [PUBLISHING.md](PUBLISHING.md).

## Install a build on your own phone with ADB

1. Get **SDK Platform-Tools for Windows** from <https://developer.android.com/tools/releases/platform-tools> and extract it to `C:\platform-tools`.
2. Download `shidur-N.apk` from the latest release into that folder.
3. On the phone (once): **Settings → About phone**, tap **Build number** 7 times, then turn on **Developer options → USB debugging**.
4. Connect the phone, accept the "Allow USB debugging?" prompt, open `cmd` in `C:\platform-tools` and run:
   ```
   adb devices
   adb install -r shidur-N.apk
   ```

| Problem | Fix |
|---|---|
| `unauthorized` | Unlock the phone and accept the USB debugging prompt |
| No device listed | Another cable or port, USB mode "File transfer", or the phone maker's USB driver |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A build signed with another key is installed: `adb uninstall io.github.eyalshimshi.shidur`, then install again |

Wireless (Android 11+): **Developer options → Wireless debugging → Pair device with pairing code**, then `adb pair <ip:port>`, `adb connect <ip:port>` and `adb install -r shidur-N.apk`.

## Release signing key

- **Until the release key exists,** CI signs builds with a debug key kept in the GitHub Actions cache (`shidur-debug-keystore-v1`; a weekly scheduled run keeps it from expiring).
- **The release key replaces it:**
  1. Run **Actions → Create release signing key** once.
  2. Save its two files as the repository secrets `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD`. [PUBLISHING.md §1](PUBLISHING.md#1-get-the-repository-ready-once-15-minutes) has the steps.
  3. Every build after that is signed with it, and Google Play knows it as the *upload key*.
- **Keep a copy of the key.** If it's lost, GitHub builds can't update installed apps (people would uninstall first); Google can reset a lost Play upload key.
- **Before making the repository public,** the release key must be in place. Pull requests from forks can read the Actions cache, but not secrets. Also choose **Settings → Actions → General → Require approval for all external contributors**, and delete the old debug key cache.

Once the repository is public, installed apps also start downloading station list updates from `main`.

## Crash reports and code shrinking (R8)

Release builds are shrunk and obfuscated by R8, which makes them smaller and faster; Google Play flags apps whose code isn't. In crash reports, class and method names become short codes like `a.b.c`.

- **Crash issues are decoded automatically.** When someone opens an issue titled "Crash: …" from the app, the *Decode crash report* workflow takes `mapping-N.zip` for the build named in the report from its GitHub release, and comments with the original names. To decode an issue again: **Actions → Decode crash report → Run workflow**, with the issue number. Don't delete releases: a build's crash reports need its mapping file.
- **Play Console's crash reports** are decoded with the mapping file inside the app bundle.
- **The smoke test** runs every build on an emulator before anything is published: it opens the app, taps *Get started* and *Skip* on the first-launch screens, plays a station from a `shidur://` link and goes to the home screen. When it fails, the run's Summary shows the crash with the original names, or the text on the screen and the app's log; a screenshot is kept with the run. If you rename those two English buttons, update [`.github/scripts/smoke-test.sh`](.github/scripts/smoke-test.sh).
- **Code that is reached only by reflection needs a keep rule** in [`app/proguard-rules.pro`](app/proguard-rules.pro). One example is the widget's button handler, which Glance creates from its class name. List the constructor when code creates the class: `-keep class A { <init>(); }`.
- **AGP 9's strict keep rules are off** (`android.r8.strictFullModeForKeepRules=false` in `gradle.properties`). Under them, `-keep class A` no longer keeps `A()`, which broke Room's rule (Glance → WorkManager → Room), and the first R8 build crashed at start. Remove the line only when the smoke test passes without it, for example after updating Glance.
- **If a library update fails the build with "Missing classes detected while running R8",** the run's Summary lists the rules R8 suggests. Add them to `app/proguard-rules.pro` once you've checked that the missing classes are really optional.

## Fix a station (from the browser, in two minutes)

1. Open [`app/src/main/assets/catalog.json`](app/src/main/assets/catalog.json) on GitHub and click the pencil (Edit).
2. Change what's broken: a stream `url`, a `logo` address, a name. To remove a dead backup address, delete its line and the comma before it.
3. Raise `catalogVersion` to today's date followed by a two-digit counter, for example `2026112001`.
4. **Commit changes** to `main`.
   - CI checks the file: the unit tests catch typos, and the build fails if `catalogVersion` wasn't raised.
   - Installed apps pick up the new list the next time they start (public repository only).

**Where problems come from:**
- **The weekly Station check** (Actions → *Station check*, every Monday, or run it by hand) tests every stream and logo address. It opens one issue labeled `station-check` that lists what fails, and closes it when everything works again. It runs on GitHub's servers outside Israel, so confirm a failure on a phone before removing an address.
- **Users** report problems from the app (Report a problem) or with the issue forms *A station doesn't play* and *Suggest a station*.

**Finding a new stream address:** the station's website usually plays it. Search the page source for `.mp3`, `.aac`, `icecast`, `m3u8` or `<audio src=`. Then check it with `./tools/check-streams.ps1`.

## Station list

- Stations live in `app/src/main/assets/catalog.json`. Raise `catalogVersion` with every change; installed apps download the newer list from `main` (public repository only).
- `logo` must point to a PNG, JPEG or WebP image (Wikimedia's PNG thumbnails of SVG files work). The app skips images smaller than 96 px. Radio Jerusalem has no usable logo yet.
  - The app makes each logo square: a solid tile or badge (such as Galgalatz's purple tile) fills the square with its own color, and other logos lose their empty margins and sit on their own background color, or on white.
  - The rules are in `LogoNormalizer` (`StationLogos.kt`); raise its `VERSION` when changing them, so saved logos are made again.
  - A photo instead of a logo looks bad. The station's website usually has the real logo (look for `og:image` or `apple-touch-icon`).
- Kan's stations play first from Kan's own HLS streams on `kancdn.medonecdn.net`: the addresses Kan's website player uses (search its pages for `livehls`). Each station's master playlist lists one 192 kbps AAC rendition; the catalog points straight at that media playlist (`…/playlist.m3u8?bitrate=192000&audioId=1&lang=pol&renditions`), because ExoPlayer would otherwise load the same audio twice. These streams have no per-listener ads: every listener gets the same clock-named segments.
- Triton (StreamTheWorld) is Kan's fallback. Its ads (11–14 s, rotating) are inserted into each new connection; after one, Triton's frequency cap often skips the next. The Triton addresses ask for a 60-second opening burst (`?burst-time=60`; Triton caps it, ~26–29 s for Kan) and are marked `introSeconds: 60`, so the ad arrives at once and the app starts 5 seconds before the newest audio. Triton's default burst (~11 s) is too short for that. To find or check a mount, open `https://playerservices.streamtheworld.com/api/livestream?station=KAN_BET&transports=http&version=1.9`; Makan's mount is `RADIO_MAKAN`. Documentation: [playback buffer](https://help.tritondigital.com/docs/triton-digital-streaming-specification-playback-buffer), [stream connection](https://help.tritondigital.com/docs/player-streaming-connection).
- `introSeconds` on a stream: the length of the clip (an ad or jingle) its server sends every new listener before the live audio, plus about half a second; for ads of varying length, the most to skip. The app downloads the server's opening burst and starts right after the clip. If the burst ends before the clip and 5 more seconds have arrived, it starts 5 seconds before the newest audio instead (Settings → Skip opening ads).
  - Measured in November 2026 with the app's User-Agent: 102FM 2.1 s and Radio Haifa 2.0 s (jingles), and 103FM 3.0 s (a clip that changes each time).
  - CyberCDN sends longer clips (103FM's 14-second ad) only to browser-like User-Agents; keep `USER_AGENT` (in `Net.kt`) an app-style name.
  - When your own network blocks a station, run **Actions → Measure opening clips** with its id, or leave the box empty for every station. It measures from GitHub's servers, and the result is on the run's Summary page.
- **Check the list with [`tools/check-streams.ps1`](tools/check-streams.ps1)** (PowerShell 7, nothing to install):
  - without arguments, it checks every stream and logo address's status and type;
  - with `-Report report.md`, it lists only the problems, as the weekly Station check does;
  - with `-Intro 102fm-tlv,kan-bet`, it measures those stations' opening clips with the app's User-Agent, by connecting twice 20 seconds apart and matching the audio frames.

  Behind a proxy, plain-http addresses can time out and filtered hosts return the proxy's 403 page, so confirm failures from another network before removing an address.

## Google Play

[PUBLISHING.md](PUBLISHING.md) walks through the developer account, the app's declarations, the store listing (texts and images in [`fastlane/metadata/android`](fastlane/metadata/android)), the closed test and production. Before uploading, check:
- **Exact alarms:** the app asks for `SCHEDULE_EXACT_ALARM` only. Play keeps `USE_EXACT_ALARM` for apps whose main feature is an alarm clock.
- **Advertising ID:** the manifest removes `AD_ID`, so the answer "no advertising ID" stays true even if a library asks for it.
- **Privacy policy:** [PRIVACY.md](PRIVACY.md), linked from Settings → About. Update both when the app starts using a new service.
