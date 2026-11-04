# Design philosophy: calm, native, polished apps

*A portable design brief distilled from [Shidur](https://github.com/EyalShimshi/shidur), an open-source Material 3 Android app. Give it to any LLM or designer, for any project, to get the same look and the same level of polish.*

## How to use this document

- Paste the whole file into the project's instructions (or attach it) and say: **"Design and build the UI following DESIGN-PHILOSOPHY.md."**
- It fits any kind of app. Map the vocabulary to your domain:
  - **Item:** the app's main content unit (a station, song, recipe, product, note…).
  - **Artwork:** the item's image or logo.
  - **Hero screen:** the full-screen view of one item (a player, a detail page).
  - **Now surface:** something ongoing that stays reachable from every screen (playback, a timer, a cart, an upload).
- The reference platform is Android with Jetpack Compose and Material 3. For other platforms see [§12](#12-other-platforms); the principles, tokens and checklist still apply.
- For the development process (spec, builds, releases, issues), also give the agent [BUILD-PLAYBOOK.md](BUILD-PLAYBOOK.md).
- **Workflow for every screen:**
  1. Pick the pattern from §3.
  2. Apply the tokens in [Appendix A](#appendix-a-tokens).
  3. Write the copy with §7.
  4. Design every state in §6.
  5. Run the checklist in §13 before calling it done.

## 0. The short version

If you read nothing else, follow these 15 rules.

1. **Material 3 is the design system.** Use its color roles, type scale, shapes and stock components. Don't invent a widget the platform already has.
2. **One tap to the core action.** Tapping an item does the main thing (play, open). Secondary actions live on long-press, a trailing icon or an info sheet.
3. **Colors come from roles, never from hex codes in components.** Every scheme is generated from one seed color with tone-based roles, so contrast is always right. The seed can be the wallpaper (dynamic color), the user's accent or an item's brand color.
4. **Content tints the hero; chrome stays calm.** The hero screen takes its colors from the item. Lists, bars and settings keep the app's colors.
5. **Big, soft shapes:**
   - a pill-shaped search field;
   - cards and tiles with 20–28 dp corners;
   - squircle artwork (corner radius ≈ 22–28% of its size);
   - grouped lists with 24 dp outer and 4 dp inner corners.
6. **Depth comes from tonal surfaces and soft gradients,** not from heavy shadows or borders.
7. **Motion is physical and purposeful.** Springs, gestures that follow the finger, predictive back. Expressive motion only at key moments. Always honor the system's reduce-motion setting and an in-app *Animations* switch.
8. **Never show a blank.**
   - Every image has a generated fallback.
   - Every empty list invites the next step.
   - Every error says what happened and what to do.
9. **Make speed felt:**
   - act on press-down;
   - prefetch the likely next thing;
   - cache what you've shown;
   - bundle a data snapshot so the first launch is instant;
   - show measured timings.
10. **Plain, short words.** Sentence case and verbs on buttons. Every setting states its effect and its cost in one line.
11. **Few settings, good defaults.** Group them. Label experimental features and leave them off by default.
12. **Accessibility and RTL are designed in:**
    - 48 dp touch targets;
    - a label on every icon button;
    - text that still works at 200%;
    - start/end, never left/right;
    - mirror directional icons, but never media-transport icons.
13. **Meet users outside the app:** notification, lock screen, widget, shortcuts and share links, with the same names and artwork as inside.
14. **Respect the user:** no forced accounts, no tracking, local-first data, nothing sent without asking.
15. **Ship the edge cases:** dark, pure black, RTL, large text, offline, rotation, small and large screens.

## 1. Principles

1. **One tap to value.** The shortest path wins. Nothing stands between the user and the main action: no splash screens, sign-in walls or needless confirmations.
2. **Content is the hero; chrome is quiet.** Artwork and names carry recognition. Bars, buttons and backgrounds recede: tonal surfaces, no borders, no clutter.
3. **Expressive where it matters, calm elsewhere.** Springy motion, shape morphs and color shifts are kept for meaningful moments: starting and stopping, favoriting, opening the hero screen. Everything else uses quick, standard transitions.
4. **Feels native.** Standard components, gestures and system integrations beat custom inventions. Users already know the platform; the app should feel like the best version of it.
5. **Fast is a feature.** Perceived speed matters more than raw speed. Respond instantly, anticipate the next step, never block the screen.
6. **Honest and calm.** Plain words. No dark patterns, no nagging, no surprise data use. State a cost where the user makes the choice.
7. **Inclusive by default.** RTL languages, large text, screen readers, reduced motion and contrast are part of the first design, not a later pass.
8. **Fewer, better options.** Defaults fit most people. Settings exist for real preferences and trade-offs, never to patch a design problem.

## 2. Visual system

### 2.1 Color

Where the scheme comes from, in priority order:

| Context | Scheme |
|---|---|
| Hero screen | Generated from the item's brand color or artwork (§2.2), when that setting is on |
| Everywhere else, by default | **Dynamic color** from the wallpaper (Android 12+) |
| Dynamic color off | Generated from the user's accent pick. The default accent is Material's baseline purple `#6750A4` |

- **Theme mode:** System · Light · Dark, plus a **Pure black** switch for dark mode, for OLED screens. Pure black:
  - sets background and surface to `#000000`;
  - maps the surface containers to a near-black ramp, so cards stay distinguishable;
  - keeps accents colored;
  - turns decorative gradients off.
- **Accent picker:** a row of 8 color swatches ([Appendix A](#appendix-a-tokens)). Picking one turns dynamic color off; the setting's summary says so.
- **Which role for what:**

| Role | Use it for |
|---|---|
| `primary` | Active-state icons (a filled favorite heart, a now-playing indicator), primary buttons, badges |
| `primaryContainer` | The glow behind hero artwork; soft but prominent containers |
| `secondaryContainer` | The highlight on the current item's row; invitation cards in empty states; selected chips |
| `surfaceContainerHigh` | The search pill and input fields |
| `surfaceContainer` | Navigation bar; grouped settings cards |
| `onSurfaceVariant` | Secondary text: subtitles, hints, metadata |
| `error` | Errors, destructive actions, a "● LIVE" dot |

- **Never:**
  - hard-code a color in a component;
  - pick text colors by eye;
  - use color as the only signal (pair it with an icon or a word).

### 2.2 A full scheme from any single color

Item tints and accent picks use the same generator, so both inherit Material's contrast guarantees.

- Work in a perceptual color space where lightness equals Material's *tone* (0–100): CIELAB L\*, or HCT from Material Color Utilities.
- Build six palettes from the seed's hue *h* and chroma *c*:

| Palette | Hue | Chroma |
|---|---|---|
| primary | h | c, clamped to 32–56 |
| secondary | h | 18 |
| tertiary | h + 60° | 28 |
| neutral | h | 5 |
| neutral variant | h | 10 |
| error | 33° | 62 |

- **Grey seeds stay calm.** Multiply every chroma except error by `min(1, c / 16)`. Black, white and grey logos then give quiet schemes instead of a random hue.
- Read each role at its tone; [Appendix A](#appendix-a-tokens) has the full table.
- Compute schemes off the main thread and cache them per item and mode. When the item changes, crossfade the colors (about 400 ms, no overshoot).

### 2.3 Typography

- **One font family for everything:** the platform font (Roboto on Android), or a bundled variable font that covers all your scripts (for example Heebo for Hebrew and Latin). Bundle it; never download fonts at runtime.
- **Use the Material 3 type scale.** Emphasize with weight, not with bigger sizes:

| Element | Style |
|---|---|
| Hero title | `headlineMedium`, bold, centered |
| Section and shelf titles | `titleLarge`, semibold |
| Settings group labels | `titleSmall`, in `primary` |
| Row title / subtitle | `bodyLarge` / `bodyMedium` in `onSurfaceVariant` |
| Hero status line | `titleMedium`, at most 2 lines |
| Tile label | `labelMedium`, centered, at most 2 lines |
| Small metadata ("Started in 0.62 s") | `labelMedium` in `onSurfaceVariant` |

- **Tabular figures** for anything that counts: times, countdowns, frequencies, prices, stats.
- **Every text has a line limit and an ellipsis.** Nothing is clipped mid-glyph.

### 2.4 Shape

- Use the Material shape scale: 4 · 8 · 12 · 16 · 20 · 28 · 32 · 48 dp.
- **The same object has the same shape everywhere.** Artwork is always a squircle, whatever its size.

| Element | Shape |
|---|---|
| Search field | Full pill (28 dp) |
| Tiles and cards | 20 dp |
| Grouped list items | 24 dp outer corners, 4 dp inner corners, 2 dp gaps |
| Artwork thumbnails | Corner radius = 28% of the size |
| Hero artwork | Corner radius = 22% of the size |
| Floating mini surface | 16 dp |
| Bottom sheets | 28 dp top corners (the M3 default) |
| Primary hero action (88 dp) | Shape shows state: rounder when idle (44 dp corners), squarer when active (28 dp), morphing with a spring |

### 2.5 Spacing and sizing

- Everything sits on a **4 dp grid**: 4 · 8 · 12 · 16 · 24 · 32.
- **Screen edges:** 16 dp on lists and Home, 24 dp on the hero screen.
- **Touch targets:** at least 48 dp. The hero's primary action is 88 dp. Secondary transport buttons are 56 dp with 32 dp icons, spaced 20 dp apart.
- **Lists end with bottom padding** so the last item can scroll above any floating bars.

| Element | Size |
|---|---|
| Row artwork | 48 dp |
| Grid tile artwork | 72 dp; the label's width is the artwork + 12 dp |
| Shelf tile artwork | 112 dp; 6 dp padding inside the tile, 4 dp between tiles |
| Mini surface | 64 dp tall, 8 dp side margins, floating above an 80 dp navigation bar |
| Hero artwork | min(70% of screen width, 38% of screen height, 300 dp) |

### 2.6 Depth

- **Tonal elevation:** a higher layer uses a higher `surfaceContainer` role. Shadows are only for floating elements (the mini surface: 3 dp).
- **Soft washes** add warmth without noise:
  - **Top wash:** `primary` at 16% fading to transparent over the top ~320 dp of every main screen.
  - **Hero glow:** `primaryContainer` at 85% fading to transparent over the top 60% of the hero screen.
  - **Navigation bar fade:** the bar's color goes from transparent to 94% at 30% of its height, then solid, so content slides softly under it.
- All gradients sit behind one *Background gradient* switch, and are off in pure black.

### 2.7 Iconography

- **One icon family:** Material Symbols, Rounded or Outlined. Outline is the default; filled marks selected or active (navigation item, favorite).
- **Bundle only the icons you use**, as vectors, to keep the app small.
- **Mirror directional icons in RTL** (back, forward, chevrons). **Never mirror media-transport icons** (play, pause, previous, next).
- Every icon-only button has a content description. Navigation items show icons with labels.

### 2.8 Imagery

- **Every item has artwork.** When there's no image, generate a **monogram**:
  - the item's short name on its brand color;
  - black or white text, chosen by the background's luminance;
  - font size fitted to the text length.
- **Normalize remote images:**
  - make them square;
  - put transparent logos on a matching background with a margin;
  - reject tiny images (fall back to the monogram);
  - cache them on disk.
- **Allow turning remote images off**, for privacy and data. The monograms keep the app complete.

## 3. Layout and navigation patterns

### 3.1 App shell

- **Edge-to-edge** with transparent system bars; insets handled everywhere.
- **Three top-level destinations,** five at most, in a bottom navigation bar. No hamburger menu for primary navigation.
- **Top of Home:** a pill search field (tapping it opens full-screen search) with a settings icon at the end. Search matches names, nicknames and numbers.
- **The now surface** docks above the navigation bar (§3.5).
- **First run:** one welcome screen, then "Pick your favorites" (a multi-select grid with *Continue (n)* and *Skip*). No tutorial carousels.

### 3.2 Home as shelves

- **Personal first, then categories:** Favorites → Recent → one shelf per category. End with a tonal *All items* button.
- **A shelf** is a title (`titleLarge`, semibold) with an arrow that opens the full list, above a horizontally scrolling row of large tiles (112 dp artwork).
- **Acting on an item in a shelf uses the shelf as its context.** For example, playing an item queues the whole shelf, so "next" stays meaningful.
- **Hide empty shelves,** except Favorites, which becomes an invitation card: what goes here, plus a button that fills it.

### 3.3 Lists and rows

- **Rows use the stock list item:** 48 dp leading artwork, a one-line headline, a one-line supporting line (metadata) and trailing actions (for example a favorite toggle).
- **The current item is visible everywhere:**
  - its row gets a `secondaryContainer` background and an activity icon in `primary`;
  - its tile gets a 22 dp `primary` badge with that icon at the artwork's bottom-end corner.
- **Tap does the main action.** Long-press opens details or a menu.
- **Sort and filter with chips.** Use sticky group headers only when grouping helps.
- **Reordering:** long-press lifts the item (a slight scale up, with a haptic), its neighbors move aside, and the drop gives a tick.

### 3.4 Grouped settings

- **A group label** (`titleSmall` in `primary`; 24 dp above it, 8 dp below), then the group's rows as one visual card:
  - each row is its own `surfaceContainer` surface;
  - inner corners are 4 dp, the group's outer corners 24 dp;
  - rows are 2 dp apart, with 16 dp side margins.
- **Row types:**
  - **Switch row:** title, one-line summary and a switch. The whole row toggles.
  - **Click row:** title and summary; it opens a page or an action.
  - **Choice rows:** radio buttons inside one card.
  - **Swatch row:** 48 dp color circles. The selected one gets a 3 dp ring in `onSurface`.
  - **Segmented choice:** for 2–3 options, such as System · Light · Dark.

### 3.5 The now surface (mini ↔ full)

The ongoing thing (playback, timer, cart) is **one continuous surface**, not two screens.

- **Its position is a single progress value,** from 0 (mini) to 1 (full screen). Interpolate from it:
  - position;
  - side margins (8 dp → 0);
  - corner radius (16 dp → 0);
  - shadow (3 dp → 0);
  - background color (app color → hero color).
- **It follows the finger:**
  - drag to move it;
  - releasing faster than ~1200 px/s flings it in that direction;
  - otherwise it settles to the nearer state (50%) with a medium-low-stiffness spring.
- **Content crossfades with progress:** the mini content fades out over the first third; the full content fades in between 10% and 60%.
- **Nested scrolling:** content inside the full surface scrolls first, then the surface drags.
- **Predictive back** shrinks it toward the bottom (to 90%) and rounds its corners (to 28 dp) as the gesture moves, then collapses it.
- **The navigation bar slides away** as it opens.
- **Swipe sideways** on the mini surface or the hero artwork to move to the previous or next item.
- **The primary control** (play/pause) on the mini surface shows loading inside the button itself.

### 3.6 Hero screen

- **Top row:** collapse at the start; contextual actions (cast, info) at the end.
- **Artwork:**
  - large and centered, with 22% corners;
  - at 92% scale while idle, springing to 100% when active;
  - a swipe on it changes item.
- **Text stack:**
  - title (bold `headlineMedium`);
  - subtitle (`bodyMedium` in `onSurfaceVariant`), with an optional colored live dot;
  - status line (`titleMedium`; tap it for actions like *Copy · Search · Share*);
  - small metadata (`labelMedium`).
- **Controls:**
  - an 88 dp primary action with a state-morphing shape;
  - 56 dp previous and next;
  - then secondary toggles: favorite, a timer that shows its countdown, share.
- **Colors:** the item's own scheme, with the hero glow behind the artwork.

### 3.7 Sheets and dialogs

- **Bottom sheets** hold contextual tools: info, timers, options, song actions. A sheet starts with a small header (artwork + title), then full-width rows or tonal buttons.
- **Dialogs** are only for confirmations and very short forms. Complex forms are full screen on phones.
- **A snackbar with Undo** beats a confirmation dialog for reversible actions.

### 3.8 Adaptive layout

| Width | Navigation | Hero |
|---|---|---|
| Compact (< 600 dp) | Bottom bar | Full-screen sheet |
| Medium (600–839 dp) | Navigation rail | Full-screen sheet |
| Expanded (≥ 840 dp) | Rail | A persistent pane on the trailing side |

- Grids use an adaptive minimum cell width (about 104 dp) and drop columns as text grows.

## 4. Motion and feedback

- **Two kinds of spring:**
  - **spatial** (position, size, containers): a slight overshoot;
  - **effects** (color, opacity): no overshoot, about 200–400 ms.
- **Character only at key moments:**

| Moment | Treatment |
|---|---|
| Start / stop | The primary button morphs shape; the artwork springs between 92% and 100% |
| Favorite | Heart fills with a small spring "pop" and a light haptic |
| Open the hero | Container transform from the mini surface, following the finger |
| Back | Predictive back previews the result before it commits |
| Change item | Pager motion between artworks; the colors crossfade |
| List entry | Fade with a slight slide, staggered for the first 6 items at most |

- **Gestures keep their velocity.** A fling continues the motion; it never restarts it.
- **Feedback starts on press.** Ripples, and progress shown inside the control that started the work (a loading indicator inside the play button), never a full-screen spinner for a local action.
- **Haptics are subtle:** pick up, drop, toggle. Respect the system haptics setting.
- **Reduced motion:** when the system animation scale is 0 or the in-app *Animations* switch is off, loops, morphs and staggers become simple fades or instant changes.

## 5. Speed you can feel

- **Prepare on press-down.** Start connecting or prefetching when the finger touches an item, before the tap completes.
- **Anticipate:**
  - when the app opens, warm the likeliest next actions (top favorites, the last item);
  - after an action, warm its neighbors (next and previous).
- **Cache everything you've shown:** connections, DNS answers, images, data. Show cached content at once and refresh it quietly.
- **Bundle a snapshot of the essential data,** so the first launch is instant and works offline. Fetch updates in the background.
- **Start small, then fill.** Use small start-up buffers and page sizes.
- **Remember what worked** (the last working source or server) and try it first.
- **Show the speed.** A small "Started in 0.62 s" under the hero status, and a *Performance* section in Settings with recent timings. This makes speed visible to users and debuggable for you.
- **Aggressive modes are opt-in.** Preloading or keeping connections open is off by default, with the cost stated ("Uses about 1 MB a minute, also on mobile data").
- **Nothing blocks the main thread.** All I/O, including closing connections, runs in the background.

## 6. States

Design all of these for every screen:

| State | Treatment |
|---|---|
| Loading | Keep showing the old content. Use skeletons only when there is truly nothing yet. Show progress inside the control that triggered the work |
| Empty | A friendly card (`secondaryContainer`): what goes here, plus the button that fixes it ("No favorites yet" + *Browse stations*) |
| Error | What happened + what you can do: *Retry*, *Try another source*, *Report*. Never raw exception text |
| Offline | A dismissible banner ("You're offline — items will load when you're back online"); cached content keeps working |
| Unavailable item | Greyed out, labeled, with a remove action |
| After a crash | The next launch offers *Copy details* or *Report on GitHub* (a pre-filled issue). Nothing is sent automatically |

## 7. Words

- **Sentence case everywhere:** "Skip opening ads", not "Skip Opening Ads". No ALL CAPS.
- **Short, concrete, friendly and calm.** Everyday words; technical terms only where users expect them. No exclamation marks.
- **Buttons are verbs:** *Browse stations*, *Copy details*, *Add to home screen*.
- **Settings summaries state the effect and the cost:** "While the app is open, keeps your last station loading so it plays at once. Uses about 1 MB a minute, also on mobile data."
- **Errors: what happened + what to do:** "Can't reach Galgalatz right now. Retry, or try another stream."
- **One name per thing,** used identically in the app, notification, widget and shortcuts.
- **Translation-ready:**
  - gender-neutral phrasing (in Hebrew, plural or infinitive forms);
  - ICU plurals;
  - mixed-direction values (URLs, numbers, Latin names inside RTL text) wrapped in Unicode isolates.
- **Disclaimers, where needed, are one plain line:** "An independent app, not affiliated with any broadcaster."

## 8. Settings

- **Defaults most people keep.** Settings are for preferences and trade-offs.
- **Group by intent:**
  1. Colors
  2. Interface
  3. Language
  4. The core feature (for example Playback)
  5. Feature groups (for example Alarm)
  6. Content and data
  7. Performance (diagnostics)
  8. About
- **Every switch has a one-line summary:** what it does, plus its cost if it has one.
- **Follow the system first** (theme, language, contrast, animations), then allow an override.
- **Experimental features** say "(experimental)" in their title and are off by default.
- **About** shows the version, license, source link, a report link and the disclaimer.

## 9. Accessibility, RTL and languages

- **Targets** are at least 48 dp; the hero's primary action is at least 88 dp.
- **Screen readers:**
  - rows and tiles read as one sentence ("Galgalatz, 91.8 FM, playing");
  - custom actions cover the secondary actions (favorite, info, move up/down);
  - state changes are announced politely and throttled.
- **Text scale up to 200%:** grids drop columns, rows wrap to 2–3 lines, nothing critical is truncated.
- **Contrast** follows from tone-based roles (§2.2), including on item-tinted screens. Aim for WCAG AA or better.
- **RTL:**
  - start/end everywhere, never left/right;
  - the layout mirrors, but media-transport icons don't;
  - metadata lines use content-based text direction.
- **Languages:**
  - follow the system language, with a per-app language option;
  - format times in the user's locale;
  - test with pseudo-locales.

## 10. Beyond the app screen

Meet users where they already are, with the same names, artwork and actions as inside the app:

- **Media notification and lock screen.** The title is the most specific thing known (the song); otherwise the item's name. The subtitle is its context. Include a custom action for the key toggle (favorite).
- **Home-screen widget:** themed with dynamic colors and the system's widget corner radius.
- **Launcher shortcuts:** the top 4 favorites, plus *Add to home screen* for any item.
- **Share links** that open the item in the app, or a simple web page with a download link.
- **Car, watch and cast** through the platform's standard media APIs, with no custom UI.
- **Alarms and reminders** use the system's alarm APIs and keep working after a restart.

## 11. Trust and privacy

- **No forced accounts, ads, analytics or trackers.** Personal data (favorites, history, settings) stays on the device.
- **Every network call is explainable:** list them in the README, and let users turn off the optional ones (for example remote images).
- **Crash reports stay on the device** until the user chooses to send them.
- **Be clear about status:** an open-source license, and a one-line disclaimer when the app is unofficial.

## 12. Other platforms

The principles, tokens, copy rules and checklist apply everywhere. Map the mechanics:

| Concept | Android (Compose) | Web | Flutter | iOS (SwiftUI) |
|---|---|---|---|---|
| Design system | `MaterialTheme` with Material 3 | Material 3 tokens as CSS custom properties (`--md-sys-color-*`); `@material/web` components | `ThemeData(useMaterial3: true)` | Native components styled with these colors, shapes and spacing |
| Dynamic color | `dynamicLightColorScheme` / `dynamicDarkColorScheme` | `prefers-color-scheme` plus a user accent | `dynamic_color` package (`DynamicColorBuilder`) | System appearance plus a user accent |
| Scheme from a seed | Material Color Utilities, or the §2.2 generator | `@material/material-color-utilities` (`themeFromSourceColor`) | `ColorScheme.fromSeed` | The §2.2 generator, or precomputed tokens |
| Predictive back | `PredictiveBackHandler` | History API; swipe-back on mobile | `PopScope` with predictive back enabled | Interactive pop gesture |
| Mini ↔ full surface | A custom draggable layout (§3.5) | A pointer-driven sheet with CSS transforms | `DraggableScrollableSheet` | A sheet with detents, or a custom drag |
| Reduced motion | Animator duration scale, plus the in-app switch | `prefers-reduced-motion` | `MediaQuery.disableAnimationsOf` | `accessibilityReduceMotion` |
| RTL | start/end, `LayoutDirection` | `dir="rtl"` and logical CSS properties (`margin-inline-start`) | `Directionality` | leading/trailing |

For an identical look on iOS, build with Flutter or Compose Multiplatform. Otherwise keep native iOS controls and apply this document's colors, shapes, spacing, motion and words.

## 13. Definition of done

A screen is done when all of these hold:

- [ ] It looks right in light, dark and pure black, with dynamic color on and off, and with at least two accents.
- [ ] It works in LTR and RTL, and mixed-direction text is isolated.
- [ ] At 200% text, nothing critical is clipped and grids reflow.
- [ ] Every control is labeled for screen readers, and rows read as one sentence.
- [ ] With reduced motion on, nothing loops or morphs.
- [ ] Its empty, loading, error and offline states are designed.
- [ ] Every image has a fallback, and the same object has the same shape everywhere.
- [ ] Touch targets are at least 48 dp, and the back gesture works, with a predictive preview where available.
- [ ] State survives rotation and process death.
- [ ] It works at compact, medium and expanded widths.
- [ ] The copy is sentence case with verbs on buttons, costs are stated, and no raw errors show.
- [ ] No I/O runs on the main thread, and start-up time is measured.
- [ ] The notification, widget and shortcuts use the in-app names and artwork.

## 14. Anti-patterns

- A hamburger menu for primary navigation, or more than 5 tabs.
- Hard-coded colors, grey text on colored backgrounds, color as the only signal.
- Heavy drop shadows, borders around everything, dense tables on phones.
- A spinner on a blank screen, a modal dialog for non-critical information, a toast for an error.
- Empty image placeholders, or artwork shapes that differ between screens.
- Decorative motion that ignores reduced motion, or gestures that don't follow the finger.
- Settings that expose implementation details, options without explanations, experimental features on by default.
- Title Case Labels, ALL CAPS, jargon, raw exception messages.
- Left/right instead of start/end; mirrored play buttons.
- Tracking, forced sign-in, silent data use.

## Appendix A: Tokens

### Tone table

These are the tones each role reads from its palette (§2.2):

| Role | Light | Dark |
|---|---|---|
| primary / onPrimary | 40 / 100 | 80 / 20 |
| primaryContainer / onPrimaryContainer | 90 / 10 | 30 / 90 |
| secondary, tertiary, error: same tones as primary | | |
| inversePrimary | 80 | 40 |
| background, surface / onSurface | 98 / 10 | 6 / 90 |
| surfaceVariant / onSurfaceVariant (neutral variant) | 90 / 30 | 30 / 80 |
| surfaceContainerLowest · Low · (base) · High · Highest | 100 · 96 · 94 · 92 · 90 | 4 · 10 · 12 · 17 · 22 |
| surfaceDim / surfaceBright | 87 / 98 | 6 / 24 |
| inverseSurface / inverseOnSurface | 20 / 95 | 90 / 20 |
| outline / outlineVariant (neutral variant) | 50 / 80 | 60 / 30 |
| scrim | black | black |

### Pure black (dark mode)

| Role | Color |
|---|---|
| background, surface, surfaceDim, surfaceContainerLowest | `#000000` |
| surfaceContainerLow | `#0A0A0C` |
| surfaceContainer | `#111114` |
| surfaceContainerHigh | `#18181B` |
| surfaceContainerHighest | `#1F1F23` |

### Accent presets

`#6750A4` (default, Material baseline) · `#0061A4` · `#006A6A` · `#386A20` · `#8B5000` · `#9C4146` · `#B4236C` · `#0038B8`

### Shapes and sizes

| Token | Value |
|---|---|
| Search pill corner | 28 dp |
| Tile / card corner | 20 dp |
| Group outer / inner corner, gap | 24 / 4 / 2 dp |
| Artwork corner | 28% of size (thumbnails), 22% (hero) |
| Mini surface | 64 dp tall, 8 dp margins, 16 dp corners, 3 dp shadow |
| Navigation bar | 80 dp |
| Row / grid tile / shelf tile artwork | 48 / 72 / 112 dp |
| Primary hero action | 88 dp; corners 44 dp idle, 28 dp active |
| Secondary hero actions | 56 dp buttons, 32 dp icons, 20 dp apart |
| Now-playing badge on tiles | 22 dp circle in `primary`, 14 dp icon |
| Accent swatch | 48 dp circle; 3 dp `onSurface` ring when selected |
| Screen margins | 16 dp (lists), 24 dp (hero) |

### Motion and gradients

| Token | Value |
|---|---|
| Sheet settle | Spring, medium-low stiffness; fling threshold ~1200 px/s; otherwise snap at 50% |
| Mini content fade-out | Progress 0 → 0.33 |
| Full content fade-in | Progress 0.1 → 0.6 |
| Predictive back on the sheet | Scale to 90% toward the bottom, corners to 28 dp |
| Idle artwork scale | 0.92 → 1.0 when active (spring) |
| Color crossfade | ~400 ms, no overshoot |
| List stagger | First 6 items at most |
| Top wash | `primary` 16% → transparent over ~320 dp |
| Hero glow | `primaryContainer` 85% → transparent over the top 60% |
| Navigation bar fade | Transparent → 94% at 30% of its height → solid |

## Appendix B: Prompt snippet

When the full document doesn't fit, paste this instead:

> Design and build the UI as a calm, native, polished Material 3 app.
> - **Color:** dynamic wallpaper color, falling back to a user accent (default `#6750A4`). Generate every scheme from one seed with tone-based roles (light: primary T40, container T90, surface T98; dark: primary T80, container T30, surface T6). Tint the item's hero screen with a scheme from its brand color. Offer Light / Dark / System plus Pure black.
> - **Shape:** pill search field; 20 dp tiles; squircle artwork (28% corners, 22% on the hero); grouped settings cards with 24 dp outer and 4 dp inner corners and 2 dp gaps. Depth from tonal surfaces and soft gradients, not shadows.
> - **Layout:** 3 bottom tabs; a search pill and settings icon on top of Home; Home as horizontal shelves (Favorites, Recent, categories); list rows with 48 dp artwork; the current item highlighted in `secondaryContainer`. One draggable mini ↔ full surface for the ongoing activity, with predictive back.
> - **Motion:** springs, follows the finger, expressive only on key moments (state morphs, favorite pop); honor reduce motion and an in-app switch.
> - **Never a blank:** monogram fallbacks for images, invitation empty states, errors as "what happened + what to do".
> - **Speed:** act on press-down, prefetch likely next items, cache, bundle a data snapshot, show measured timings.
> - **Words:** sentence case, short, verbs on buttons; each setting states its effect and cost in one line.
> - **Inclusive:** 48 dp targets, labels on every icon button, 200% text, RTL with start/end (never mirror media icons).
> - **Trust:** no accounts or tracking; local-first data.
> - **Before finishing,** check light/dark/pure black, RTL, large text, and empty/error/offline states.
