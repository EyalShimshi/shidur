# Publishing Shidur on Google Play

A step-by-step guide for the maintainer. Everything happens in a browser and on your phone; nothing needs to be installed on a computer. Facts were checked against Google's help pages in November 2026; Google changes details often, so trust Play Console's own wording when it differs.

**How long it takes:** about 3–4 weeks, mostly waiting.

| Step | Time |
|---|---|
| Developer account and identity checks | 1–3 days |
| Closed test with 12 testers (required for new personal accounts) | 14 days |
| Review of the production application | up to 7 days |
| Review of the first release | up to 7 days |

## 1. Get the repository ready (once, 15 minutes)

1. **Create the release key.** GitHub → **Actions** → **Create release signing key** → **Run workflow**. When it finishes, open the run and download **release-signing-key** (a zip).
2. **Save it as secrets.** Repository **Settings → Secrets and variables → Actions → New repository secret**, twice:
   - `RELEASE_KEYSTORE_BASE64`: the content of `RELEASE_KEYSTORE_BASE64.txt`;
   - `RELEASE_KEYSTORE_PASSWORD`: the content of `RELEASE_KEYSTORE_PASSWORD.txt`.
3. **Keep the zip safe** (a password manager or private cloud storage), then delete the download from the run page. Google can replace a lost Play upload key, but without it, GitHub builds can't update the installed app.
4. **From now on, builds are signed with the new key.** Phones with an older build need to uninstall it once (favorites are lost) and install the new one.
5. **Protect the repository before making it public.** Settings → Actions → General → **Fork pull request workflows** → *Require approval for all external contributors*. Then delete the old debug key: Actions → Caches → delete `shidur-debug-keystore-v1`.
6. **Make the repository public:** Settings → General → Danger zone → Change visibility. The privacy policy link, station list updates in installed apps and Obtainium all need this.

## 2. Make the release

1. GitHub → **Releases → Draft a new release** → *Choose a tag*: type `v1.0.0` and pick **Create new tag on publish** (target `main`) → **Publish release**.
2. The **Build APK** workflow builds the release and attaches two files to it:
   - `shidur-v1.0.0.apk`, for people who install from GitHub or Obtainium;
   - `shidur-v1.0.0.aab`, the app bundle for Google Play.
3. Later versions work the same way: `v1.0.1`, `v1.1.0` and so on. Every build's version code is higher than the last one, as Play requires.

## 3. Open a developer account

1. Go to <https://play.google.com/console> and sign up with your Google account. Choose a **personal** account, which needs no D-U-N-S number.
2. Pay the one-time **US$25** fee.
3. **Verify your identity** with a government ID. Your legal name and address come from your Google payments profile.
4. **Verify an Android phone:** install the **Google Play Console** app on your phone (Android 10 or newer, not rooted) and scan the QR code shown on the website.
5. **Verify your contact email and phone** with one-time codes.

The developer name you choose is shown on the store. The contact email is shown publicly.

## 4. Create the app and fill in "App content"

**Create app:**
- App name: `שידור – רדיו ישראלי` with Hebrew as the default language, or `Shidur: Israeli Radio` with English.
- App, Free.
- Accept the declarations.

On the Dashboard, under **Set up your app**, answer each item. Expected answers:

| Item | Answer |
|---|---|
| Privacy policy | `https://github.com/EyalShimshi/shidur/blob/main/PRIVACY.md` |
| App access | All functionality is available without special access |
| Ads | No, the app doesn't contain ads |
| Content rating | Fill in the IARC questionnaire as an "all other app types" app. Answer no to violence, purchases, user-to-user communication and sharing location. |
| Target audience | 18 and over is the simplest choice (younger ages bring the Families policy). It doesn't stop anyone from installing. |
| News app | No: Shidur plays radio stations; it doesn't publish news. |
| Data safety | **No data collected, no data shared.** See the note below. |
| Advertising ID | No (the app removes it on purpose). |
| Government app, financial features, health | No / none |

**Data safety note:** the app sends nothing to the developer. Crash and problem reports are only sent if the user opens them in the browser and submits them to GitHub themselves; Google treats a transfer the user starts, and expects, as not "sharing". If you prefer the most cautious answer, declare *App info and performance → Crash logs* as shared, optional, for app functionality.

**Permissions and foreground services** (Policy → App content, or when you upload the first bundle):
- **Foreground service: media playback.** Describe it as: "Plays the radio station the user picks in the background, with a media notification to control it." Google asks for a video:
  1. Record your phone's screen: open Shidur, tap a station, press Home, and show the playback notification.
  2. Upload it to YouTube as **Unlisted** and paste the link.
- **Exact alarms**, if asked: "The user sets a wake-up alarm in Settings; at that time Shidur plays the station they chose."

## 5. Store listing

Grow → Store presence → **Main store listing**. Everything is ready in [`fastlane/metadata/android`](fastlane/metadata/android):

| Field | File |
|---|---|
| App name (30 characters) | `title.txt` |
| Short description (80) | `short_description.txt` |
| Full description (4000) | `full_description.txt` |
| App icon, 512 × 512 | `en-US/images/icon.png` |
| Feature graphic, 1024 × 500 | `en-US/images/featureGraphic.jpg` |
| Phone screenshots, at least 2 | see below |

- **Category:** Music & Audio.
- **Contact details:** an email address, and the website `https://github.com/EyalShimshi/shidur`.
- **Screenshots:**
  1. Take 4–6 on your phone (Power + Volume down): Home, the player, Browse, Settings, and one in dark mode.
  2. Play rejects screenshots taller than twice their width, which most phones exceed. Copy them to the computer and run `./tools/prepare-screenshots.ps1 -Path <folder>\*.png` (PowerShell 7). It crops them to fit and saves them in the listing folder; upload those.
- **Second language:** Store listings → *Manage translations* → add Hebrew or English and paste the other folder's texts.

## 6. Closed test: 12 testers for 14 days

New personal accounts must run a closed test with at least **12 testers** who stay opted in for **14 days in a row** before they can publish to everyone.

1. Test and release → Testing → **Closed testing** → Create track (or use *Alpha*) → **Countries:** Israel (add more if you like).
2. **Testers** tab: create an email list with the Gmail addresses of at least 12 people (ask 15, in case some drop out), tick it, set the feedback link to `https://github.com/EyalShimshi/shidur/issues`, and **Save**. The opt-in links are on the same tab; the web one is `https://play.google.com/apps/testing/io.github.eyalshimshi.shidur`.
3. **Create release:**
   - Upload the `.aab` from the latest GitHub release, or use *Add from library* if you already uploaded it to Internal testing. On the first upload, keep **Google-managed app signing**: Google keeps the key that signs the copies people download, and your release key becomes the *upload key*.
   - Release notes: paste both `changelogs/default.txt` files, the Hebrew one between `<iw-IL>` tags and the English one between `<en-US>` tags.
   - Next → **Save**. Warnings about missing testers or native debug symbols don't block anything. Play reads the deobfuscation file from the bundle.
4. **Send it to Google:** **Publishing overview** lists everything waiting: the release, store listing and declarations. First finish anything App content marks as an error (such as the foreground service video), then press **Send changes for review**. The first review can take a few days. With managed publishing off, the test goes live as soon as it's approved.
5. **Send testers the opt-in link.** It works only after approval, and only for addresses on the list. Each tester opens it, taps *Become a tester*, and installs Shidur from Play. Ask them to keep it for at least 14 days. Feedback is welcome but not required.
6. **To fix something during the test,** publish a new tag (for example `v1.0.2`) and upload its `.aab` to the same track. The 14 days continue.

## 7. Apply for production and publish

1. After 14 days, the Dashboard shows **Apply for production**. It asks about your closed test (how you found testers and what you learned), the app, and whether it's ready. Review takes up to 7 days.
2. Once approved: Production → Countries → **Create release** → *Add from library* (the same bundle) or upload a newer one → release notes → **Save** → Publishing overview → **Send changes for review**. Starting with a staged rollout (for example 20%) is safer.
3. **Android Auto (optional):** Play distributes the app to cars only after you opt in: Test and release → Advanced settings → **Form factors** → add Android Auto, accept its terms, and pass its separate review. Without it, Android Auto users need the "unknown sources" developer setting.

## After launch

- **Updates:** publish a tag, upload its `.aab` to Production, and add release notes. Notes for a specific version can go in `changelogs/<versionCode>.txt`.
- **Google's automatic checks:** Test and release → **For the next release** lists findings about the uploaded bundle, each with a deadline. For example, 1.0.1 was flagged for unoptimized code, which led to turning on R8 (MAINTAINING.md).
- **Station fixes don't need a new version.** Editing `catalog.json` on GitHub updates installed apps (see MAINTAINING.md).
- **GitHub and Play builds are signed differently** (Google re-signs Play builds). Someone switching between them must uninstall first. Point most people to Google Play, and keep GitHub and Obtainium for everyone else.
- **Developer verification (2027):** Android will require apps on certified phones to come from verified developers, also outside Play. Your Play Console account counts as verified for Play. For the GitHub APKs, register the package and the release key's certificate when Play Console offers it. Google's page: <https://developer.android.com/developer-verification>.
- **Logos and station names:** Play's Impersonation and Intellectual Property policies forbid implying that an app is official.
  - Shidur uses its own name and icon, and says in the app and the listing that it is independent.
  - If a station or Google objects to a logo, remove that station's `logo` from `catalog.json` and installed apps update by themselves.
  - Avoid station logos in the icon and the feature graphic.

## Sources

- [Account types](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Identity verification](https://support.google.com/googleplay/android-developer/answer/10841920)
- [Device verification](https://support.google.com/googleplay/android-developer/answer/14316361)
- [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API levels](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Foreground services](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Exact alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Store listing assets](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374)
- [Intellectual property](https://support.google.com/googleplay/android-developer/answer/9888072)
