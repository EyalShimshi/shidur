# Maintaining Shidur

Notes for the maintainer. Users and contributors: see [README.md](README.md).

## Release flow

1. Commit and push to `main` (VS Code: **Source Control → Commit → Sync Changes**).
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
> 1. While the repository is still private, move the keystore into a repository **secret**: a one-time workflow step uploads the cached keystore as an artifact; download it, save it base64-encoded as a secret, change the workflow to read the secret, and delete the artifact. Fork pull requests can't read secrets.
> 2. Under **Settings → Actions → General**, choose **Require approval for all external contributors**.

Once the repository is public, installed apps also start downloading station list updates from `main`.

## Station list

- Stations live in `app/src/main/assets/catalog.json`. Raise `catalogVersion` with every change; installed apps download the newer list from `main` (public repository only).
- `logo` must point to a PNG, JPEG or WebP image (Wikimedia's PNG thumbnails of SVG files work). The app skips images smaller than 96 px. Radio Jerusalem has no usable logo yet.
- Kan streams only from Triton (StreamTheWorld) since its own hosts were shut down in 2026. To find or check a mount, open `https://playerservices.streamtheworld.com/api/livestream?station=KAN_BET&transports=http&version=1.9`: status 200 lists the mount's servers and bitrate. Makan's mount is `RADIO_MAKAN`.
- `introSeconds` on a stream: the length of the clip (an ad or jingle) its server sends every new listener before the live audio, plus about half a second; for ads of varying length, the most to skip. The app downloads the server's opening burst and starts right after the clip. If the burst ends before the clip and 5 more seconds have arrived, it starts 5 seconds before the newest audio instead (Settings → Skip opening ads). To measure a fixed clip, connect twice about 20 seconds apart: the clip is the stretch of audio both connections start with. Measured in November 2026: 103FM 14 s, Galei Yisrael 3.8 s, Radio Darom 2.2 s, 102FM 2.1 s.
- Kan's streams carry ads that Triton inserts into each new connection, with varying length and no signal in the stream. They're marked `introSeconds: 60` and ask Triton for a 60-second opening burst (`?burst-time=60`, capped per station at 25–60 s), so the ad arrives at once and the app starts 5 seconds before the newest audio. Triton's documentation: [playback buffer](https://help.tritondigital.com/docs/triton-digital-streaming-specification-playback-buffer), [stream connection](https://help.tritondigital.com/docs/player-streaming-connection).

## Before a Google Play release

- Play allows `USE_EXACT_ALARM` only for apps whose main feature is an alarm clock; use `SCHEDULE_EXACT_ALARM` for the Play build.
- Create a real release signing key (Play App Signing) instead of the CI key.
- See [SPEC-05](SPEC-05-quality-release.md) for the full release plan.
