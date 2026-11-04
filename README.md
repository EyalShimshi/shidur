<div align="center">

# Shidur · שידור

**Israeli radio, simply.** A free, open-source and ad-free Android app for listening to live Israeli radio.

[![Build](https://github.com/EyalShimshi/shidur/actions/workflows/build.yml/badge.svg)](https://github.com/EyalShimshi/shidur/actions/workflows/build.yml)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)
[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](#install)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)

[**Download**](https://github.com/EyalShimshi/shidur/releases) · [**Get it with Obtainium**][obtainium] · [**עברית**](#בעברית)

</div>

> [!NOTE]
> Shidur is an independent, unofficial app. It is not affiliated with or endorsed by any broadcaster. It plays each station's own public stream, and station names and logos belong to their owners.

## Features

**Listen**
- 27 Israeli stations: Kan, Galei Tzahal and Galgalatz, regional, religious and Arabic-language stations. Each has backup stream addresses that the app switches to by itself.
- **Fast starts:** likely stations are pre-connected when the app opens, and a station starts connecting the moment your finger touches it. Optional **Instant start** keeps your last station ready to play at once.
- **No ads at the start:** some stations play an ad or jingle to everyone who tunes in. Shidur downloads it in a moment, skips it, and starts at the live broadcast.
- Song titles when the station sends them, including Hebrew titles in older encodings.
- A **sleep timer** that fades out, and a **wake-up alarm** that plays your station quietly at first, with your phone's alarm sound as a backup if the station can't be reached.

**Find**
- A Home screen with shelves for your favorites, recently played stations and every category.
- Search in Hebrew or English, by nickname ("גלצ") or by frequency ("88").
- Favorites you can reorder, and any other stream added by its address (including .pls and .m3u playlists).

**Everywhere**
- Media notification, lock screen, Bluetooth and steering-wheel controls.
- Android Auto, Google Cast, Wear OS controls and a home-screen widget.
- Launcher shortcuts: long-press the app icon for your top favorites, or add any station to your home screen.

**Your way**
- Material 3 design with your wallpaper's colors or an accent color you choose; light, dark and pure-black themes; a player colored after the station.
- A player you drag open and closed and swipe between stations, with predictive back.
- Hebrew and English, fully right-to-left.

## Install

Shidur isn't on Google Play yet. Every version is published on the [Releases](https://github.com/EyalShimshi/shidur/releases) page as an APK, signed with the same key so each one installs as an update. It needs Android 12 or newer.

**With Obtainium** (recommended: updates arrive automatically)
1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. On your phone, open [this link][obtainium], or add `https://github.com/EyalShimshi/shidur` in Obtainium with **Include prereleases** turned on.

**Directly**
1. On your phone, open the [latest release](https://github.com/EyalShimshi/shidur/releases) and download `shidur-N.apk`.
2. Open the downloaded file. If Android asks, allow your browser to install apps. If Google Play Protect warns about an unknown app, choose **More details → Install anyway**.

You can also install from a computer with `adb install -r shidur-N.apk`.

**Android Auto** lists only apps from Google Play until you allow others: in the Android Auto settings, tap **Version** ten times, then turn on **⋮ → Developer settings → Unknown sources**.

## Privacy

No accounts, ads, analytics or trackers. Your favorites, history and settings stay on your phone and aren't backed up to the cloud.

The app connects only to:
- the stations' own streaming servers, to play them;
- GitHub, to check for updates to the station list;
- the public addresses of station logos (mostly Wikimedia), once per logo. Turning logos off in Settings stops this;
- Google Play services, only if you use Google Cast.

If the app crashes, the details are saved on your phone. On the next launch you can copy them or open a pre-filled report on GitHub. Nothing is sent automatically.

<details>
<summary>Permissions, and what they're for</summary>

| Permission | Used for |
|---|---|
| Internet, network state | Playing streams; lower quality on mobile data with Data saver |
| Foreground service, wake lock | Playing in the background with the screen off |
| Notifications | The playback controls notification |
| Alarms & reminders, run at startup | Ringing the wake-up alarm on time, also after a restart |

</details>

## בעברית

<div dir="rtl">

**שידור** היא אפליקציה חינמית, בקוד פתוח וללא פרסומות להאזנה לרדיו הישראלי: כאן, גלי צה״ל וגלגלצ, תחנות אזוריות ועוד. יש בה מועדפים, חיפוש בעברית, טיימר שינה, שעון מעורר עם רדיו, Android Auto, Chromecast ווידג׳ט למסך הבית, בעיצוב Material 3.

להתקנה מורידים את קובץ ה-APK העדכני מדף ה-[Releases](https://github.com/EyalShimshi/shidur/releases) ופותחים אותו בטלפון (נדרש Android 12 ומעלה). כדי לקבל עדכונים אוטומטית אפשר להשתמש ב-[Obtainium][obtainium].

שידור היא אפליקציה עצמאית שאינה קשורה לגופי השידור. שמות התחנות והלוגואים שייכים לבעליהם.

</div>

## Building from source

You need JDK 17 or newer and [Gradle 9.5](https://gradle.org/install/), or a recent Android Studio.

```sh
gradle assembleDebug        # APK in app/build/outputs/apk/debug/
gradle testDebugUnitTest    # unit tests
```

Every push to `main` is built by [GitHub Actions](.github/workflows/build.yml), which runs the unit tests and publishes the APK as a pre-release.

**Stack:** Kotlin · Jetpack Compose with Material 3 · Media3 (ExoPlayer, MediaLibraryService, Cast) · OkHttp · Glance · kotlinx.serialization

**Layout:** a single `app` module with the packages `model` (stations), `data` (station list, library, settings, alarm), `playback` (service, streams, logos, Instant start), `ui` (screens, components, theme) and `widget`. The [specification](SPEC.md) covers the product, design and architecture in detail.

## Contributing

Bug reports, station fixes and pull requests are welcome.

- **A station stopped working or is missing?** In the app, use **Settings → Report a problem** or **Suggest a station**, or [open an issue](https://github.com/EyalShimshi/shidur/issues).
- **Fixing or adding a station** means editing [`catalog.json`](app/src/main/assets/catalog.json): Hebrew and English names, search aliases, FM frequencies, stream addresses in order of preference, and optionally a logo address (PNG, JPEG or WebP). If a stream opens every connection with the same ad or jingle, its `introSeconds` tells the app how much to skip. Raise `catalogVersion` so installed apps pick up the change. The unit tests check the file, and [`tools/check-streams.ps1`](tools/check-streams.ps1) (PowerShell 7) checks that every address answers.
- **Text** lives in [`values/strings.xml`](app/src/main/res/values/strings.xml) (English) and [`values-iw/strings.xml`](app/src/main/res/values-iw/strings.xml) (Hebrew); please update both.

## Acknowledgements

- The look and feel is inspired by [Echo](https://github.com/brahmkshatriya/echo).
- [Material Icons](https://github.com/google/material-design-icons) by Google (Apache License 2.0). The icons Shidur uses are included in [`AppIcons.kt`](app/src/main/java/io/github/eyalshimshi/shidur/ui/components/AppIcons.kt).
- Built on [AndroidX](https://github.com/androidx/androidx), [Media3](https://github.com/androidx/media) and [Reorderable](https://github.com/Calvin-LL/Reorderable) (all Apache License 2.0).
- Station logos are trademarks of their owners, shown only to identify the stations. They aren't included in the app; it downloads them from public sources, mostly [Wikimedia Commons](https://commons.wikimedia.org/) and Hebrew Wikipedia. If you own a logo and want it removed, please open an issue.

## License

Copyright (C) 2026 EyalShimshi and the Shidur contributors.

Shidur is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for details.

**Additional permission under GNU GPL version 3 section 7:** If you modify this Program, or any covered work, by linking or combining it with the Google Play services client libraries (or a modified version of those libraries), containing parts covered by the terms of the Android Software Development Kit License Agreement, the licensors of this Program grant you additional permission to convey the resulting work.

The station list ([`catalog.json`](app/src/main/assets/catalog.json)) is dedicated to the public domain under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).

[obtainium]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.eyalshimshi.shidur%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FEyalShimshi%2Fshidur%22%2C%22author%22%3A%22EyalShimshi%22%2C%22name%22%3A%22Shidur%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D




