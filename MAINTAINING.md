# Maintaining Shidur

Notes for the maintainer. Users and contributors: see [README.md](README.md).

## Release flow

1. Commit and push to `main`: in VS Code, **Source Control → Commit → Sync Changes**, or `git push` in a terminal.
2. GitHub Actions builds the app in about 10 minutes and publishes `shidur-N.apk` as the pre-release **Shidur build N** (version `0.3.N`).
3. If the run fails (red ✗), open it: its **Summary** page shows the compiler or test errors in a box, ready to copy.

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

## Signing key

CI signs every build with one keystore kept in the GitHub Actions cache (key `shidur-debug-keystore-v1`). A weekly scheduled run keeps the cache from expiring. If the key is lost, new builds can't install over old ones (uninstall first).

## Before making the repository public

> [!WARNING]
> Pull requests from forks can read the Actions cache, so in a public repository anyone could copy the signing key and sign a fake update. Before switching to public:
> 1. While the repository is still private, move the keystore into a repository **secret**: a one-time workflow step uploads the cached keystore as an artifact; download and unzip it, save it base64-encoded as a secret (in PowerShell, in the unzipped folder: `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path debug.keystore))) | Set-Clipboard` copies it, ready to paste), change the workflow to read the secret, and delete the artifact. Fork pull requests can't read secrets.
> 2. Under **Settings → Actions → General**, choose **Require approval for all external contributors**.

Once the repository is public, installed apps also start downloading station list updates from `main`.

## Station list

- Stations live in `app/src/main/assets/catalog.json`. Raise `catalogVersion` with every change; installed apps download the newer list from `main` (public repository only).
- `logo` must point to a PNG, JPEG or WebP image (Wikimedia's PNG thumbnails of SVG files work). The app skips images smaller than 96 px. Radio Jerusalem has no usable logo yet.
- Kan's stations play first from Kan's own HLS streams on `kancdn.medonecdn.net`: the addresses Kan's website player uses (search its pages for `livehls`). Each station's master playlist lists one 192 kbps AAC rendition; the catalog points straight at that media playlist (`…/playlist.m3u8?bitrate=192000&audioId=1&lang=pol&renditions`), because ExoPlayer would otherwise load the same audio twice. These streams have no per-listener ads: every listener gets the same clock-named segments.
- Triton (StreamTheWorld) is Kan's fallback. Its ads (11–14 s, rotating) are inserted into each new connection; after one, Triton's frequency cap often skips the next. The Triton addresses ask for a 60-second opening burst (`?burst-time=60`; Triton caps it, ~26–29 s for Kan) and are marked `introSeconds: 60`, so the ad arrives at once and the app starts 5 seconds before the newest audio. Triton's default burst (~11 s) is too short for that. To find or check a mount, open `https://playerservices.streamtheworld.com/api/livestream?station=KAN_BET&transports=http&version=1.9`; Makan's mount is `RADIO_MAKAN`. Documentation: [playback buffer](https://help.tritondigital.com/docs/triton-digital-streaming-specification-playback-buffer), [stream connection](https://help.tritondigital.com/docs/player-streaming-connection).
- `introSeconds` on a stream: the length of the clip (an ad or jingle) its server sends every new listener before the live audio, plus about half a second; for ads of varying length, the most to skip. The app downloads the server's opening burst and starts right after the clip. If the burst ends before the clip and 5 more seconds have arrived, it starts 5 seconds before the newest audio instead (Settings → Skip opening ads). Measured in November 2026 with the app's User-Agent: 102FM 2.1 s (a jingle). CyberCDN stations (103FM, Galei Yisrael, Radio Darom) send clips only to browser-like User-Agents, so the app needs none for them; keep `USER_AGENT` (in `Net.kt`) an app-style name.
- **Check the list with [`tools/check-streams.ps1`](tools/check-streams.ps1)** (PowerShell 7, nothing to install):
  - without arguments, it checks every address's status and type;
  - with `-Intro 102fm-tlv,kan-bet`, it measures those stations' opening clips with the app's User-Agent, by connecting twice 20 seconds apart and matching the audio frames.

  Behind a proxy, plain-http addresses can time out and filtered hosts return the proxy's 403 page, so confirm failures from another network before removing an address.

## Before a Google Play release

- Play allows `USE_EXACT_ALARM` only for apps whose main feature is an alarm clock; use `SCHEDULE_EXACT_ALARM` for the Play build.
- Create a real release signing key (Play App Signing) instead of the CI key.
- See [SPEC-05](SPEC-05-quality-release.md) for the full release plan.
