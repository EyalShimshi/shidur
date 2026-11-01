# Shidur · שידור

A free, open-source, ad-free Android app for listening to **Israeli radio**: Kan, Galei Tzahal / Galgalatz, regional stations and more. Material 3 design, Hebrew and English.

> Unofficial app. It is not affiliated with or endorsed by any broadcaster. Station names belong to their owners. The app only plays each station's own public stream.

- **Specification:** [SPEC.md](SPEC.md) (product, design, architecture, station catalog, release plan, roadmap)
- **Package name:** `io.github.eyalshimshi.shidur`
- **Requires:** Android 12 or newer

---

## Put the code on GitHub (one time)

GitHub builds the app, so this folder must first be uploaded to a repository named **shidur** under the **EyalShimshi** account. Pick one way:

**A. From a PC with VS Code (about 2 minutes)**
1. In VS Code, delete `.github/workflows/build-new.yml`, an unused leftover.
2. Open **Source Control** (`Ctrl+Shift+G`) and click **Initialize Repository** (if shown).
3. Type the message `First build`, click **Commit** (answer **Yes** to stage all changes), then click **Publish Branch**.
4. Choose **Publish to GitHub private repository**, named **shidur**, signed in as **EyalShimshi**.

**B. From any computer with only a browser**
1. On github.com, signed in as **EyalShimshi**, click **+ → New repository**. Name it **shidur**, choose **Private**, leave every "Add …" option unticked, and click **Create repository**.
2. On the new empty repository page, click the **uploading an existing file** link.
3. Open this project folder in File Explorer and select everything inside it (`Ctrl+A`), including the `.github` folder and the `.gitignore` file. You can leave out `make-folders.cmd` and `.github/workflows/build-new.yml`. Drag the selection onto the browser page and wait until every file has finished uploading.
4. Click **Commit changes**.

Either way, open the repository's **Actions** tab: the **Build APK** workflow starts on its own and takes about 10 minutes. A green ✓ means the APK is on the **Releases** page (see below). A red ✗ means the build failed: click the run, then **build**, open the failed step, and copy the lines that start with `e:` or follow `What went wrong` so they can be fixed.

## Install the app on your phone

Every time code is pushed to GitHub, a GitHub Actions workflow builds the app and publishes a ready-to-install **APK** on the repository's **Releases** page. You don't need to install anything on a computer.

### Option A: install straight on the phone (easiest, no computer)

1. On your phone, open **https://github.com/EyalShimshi/shidur/releases** and sign in to GitHub. Because the repository is private, you need to be signed in to see it.
2. Tap the newest **"Shidur build N"**, then under **Assets** tap **`shidur-N.apk`** to download it.
3. Open the downloaded file from the download notification, or from the **Files** app → **Downloads**.
4. If Android says your browser isn't allowed to install apps, tap **Settings**, turn on **Allow from this source**, go back, and tap **Install**.
5. If Google Play Protect warns about an unrecognized app, tap **More details → Install anyway**. This is expected for apps that aren't from the Play Store.

### Option B: install from a Windows computer with ADB

ADB is Google's small command-line tool for talking to a phone. It's a zip file with no installer.

1. **Get ADB.** Download **SDK Platform-Tools for Windows** from https://developer.android.com/tools/releases/platform-tools and extract the zip to `C:\platform-tools`.
2. **Get the app.** On the computer, open https://github.com/EyalShimshi/shidur/releases, download the newest `shidur-N.apk`, and save it in `C:\platform-tools` as `shidur.apk`.
3. **Enable USB debugging on the phone** (one time only):
   - Open **Settings → About phone** and tap **Build number** 7 times until it says you're a developer. On Samsung it's under **Settings → About phone → Software information**.
   - Go to **Settings → System → Developer options** (on Samsung: **Settings → Developer options**) and turn on **USB debugging**.
4. **Connect the phone** with a USB cable. On the phone, tap **Allow** on the "Allow USB debugging?" prompt (tick "Always allow from this computer").
5. **Open a command window in the folder.** In File Explorer, open `C:\platform-tools`, click the address bar, type `cmd`, and press Enter.
6. **Check the connection:**
   ```
   adb devices
   ```
   Your phone should be listed with the word `device` next to it.
7. **Install:**
   ```
   adb install -r shidur.apk
   ```
   You should see `Success`. Shidur now appears in your app drawer.

**If something goes wrong:**

| What you see | What to do |
|---|---|
| `unauthorized` next to the device | Unlock the phone and accept the USB debugging prompt |
| No device listed | Try another cable or USB port, and make sure the phone is in "File transfer" USB mode. Otherwise install the phone maker's USB driver: *Samsung Android USB Driver* or *Google USB Driver* (Pixel) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A build signed with a different key is installed. Run `adb uninstall io.github.eyalshimshi.shidur`, then install again |

**No cable? Use wireless debugging (Android 11+, no drivers needed).** Put the phone and computer on the same Wi-Fi.
1. On the phone, go to **Developer options → Wireless debugging**, turn it on, and tap **Pair device with pairing code**.
2. On the computer, run `adb pair <IP:port shown on the phone>` and type the code.
3. Run `adb connect <IP:port shown under "IP address & Port">`.
4. Run `adb install -r shidur.apk`.

### Android Auto

Android Auto hides apps that weren't installed from the Play Store until you allow them:
1. Open the **Android Auto** settings on the phone, scroll down, and tap **Version** 10 times.
2. In the **⋮** menu, choose **Developer settings** and turn on **Unknown sources**.

Shidur then appears in the car's app list.

---

## What works (v0.2)

- About 27 Israeli stations, with automatic fallback between each station's stream addresses.
- **Home** (favorites, recently played, categories), **Browse** (categories, sorting), **Favorites** (drag to reorder, edit), and **Search** (Hebrew/English, nicknames, frequencies such as "88").
- **Player:** one sheet that drags (or taps) open from the mini player into the full player and back; swipe sideways on the mini player or the artwork to change station; the back gesture closes it. Background playback with the media notification, lock screen, Bluetooth and steering-wheel controls, and song titles when the station sends them (Hebrew encodings handled).
- **Fast starts:** stations are pre-connected when the app opens and the moment you touch one, so most start in well under a second. **Settings → Performance** shows how long each start took.
- **Instant start (experimental, off by default):** keeps your last station loading silently for up to 2 minutes while the app is open and nothing plays, so it starts at once. It works on Wi-Fi and mobile data and uses about 1 MB per minute while it waits, so leave it off if your data plan is limited.
- **Sleep timer** with fade-out; **custom stations** by URL (including `.pls`/`.m3u` playlists); **share**.
- **Android Auto** (Favorites / Recent / Categories / All stations, plus voice search), **home-screen widget**, **Google Cast**, and **Wear OS** controls (automatic, through the media session).
- **Settings:** light/dark/system theme, wallpaper colors or an accent color of your choice, pure black (the player keeps a black background and colors only its accents), station colors in the player, background gradient, animations, language (Hebrew/English), data saver, headphones auto-play.

**Not in this build yet** (see [SPEC-06](SPEC-06-roadmap-risks.md)):
- automated station health checks and program schedules;
- Bluetooth auto-start through the companion device picker;
- share landing pages;
- opt-in crash reports;
- the newer Material 3 *Expressive* components (this build uses stable Material 3);
- the two-pane player for tablets.

## Building

- **Cloud:** [`.github/workflows/build.yml`](.github/workflows/build.yml) builds a release APK (signed with the workflow's own key, so each build installs over the previous one) on every push to `main`/`master` and publishes it as a pre-release.
- **Locally (optional):** install Android Studio, open this folder, and press **Run**.
- **Toolchain:** AGP 9.2, Gradle 9.5, Kotlin 2.3, Jetpack Compose (BOM 2026.09), Media3 1.11.

## Updating the station list

Stations live in [`app/src/main/assets/catalog.json`](app/src/main/assets/catalog.json). Each station has Hebrew/English names, aliases for search, FM frequencies, and a prioritized list of stream URLs. Once the repository is public, installed apps also download this file from GitHub, so station fixes reach users without a new APK.

## License

GPL-3.0 (to add the full license text: on GitHub choose **Add file → Create new file**, name it `LICENSE`, click **Choose a license template**, and pick **GNU General Public License v3.0**).
