# Build playbook: from idea to a polished, shipped app with an AI agent

*How [Shidur](https://github.com/EyalShimshi/shidur) was built: an AI agent researched and wrote the spec, the code, the tests and the docs, while the owner made the decisions and tested every build on a phone. No development tools were installed on the owner's computer: GitHub Actions compiled, tested and published every build. This file describes the process. Pair it with [DESIGN-PHILOSOPHY.md](DESIGN-PHILOSOPHY.md), which describes the look.*

## How to use this document

- Give a new agent this file and DESIGN-PHILOSOPHY.md. Optionally attach a finished project's spec files and CI workflow as examples.
- Start with the kickoff prompt in [Appendix C](#appendix-c-kickoff-prompt).
- The examples come from an Android app (Kotlin, Jetpack Compose, GitHub Actions). The process works for any platform.

**Roles:**
- **Owner** (the human): has the idea, makes the decisions, tests each build on a real device and reports problems.
- **Agent** (the AI): researches; writes the spec, code, tests, CI and docs; works in the computer's shell (git, quick checks, commits and pushes); asks for the decisions that matter; and explains the trade-offs in plain words.

## 0. The short version

1. **Spec before code.** Write a short document set with a decision log, then ship. Don't polish the spec forever.
2. **The owner decides; the agent proposes.** Ask decisions as multiple-choice questions with a recommended answer. Settle trivia yourself and say what you chose.
3. **Something installable, early.** Build a thin first version over the full pipeline: push → build → release → install on the phone.
4. **The computer stays clean; CI is the compiler.** Nothing gets installed on the owner's computer. The agent uses the shell for git and quick checks. Every push builds in CI, runs the tests and publishes an installable build, with any errors summarized in a box on the run's page.
5. **Write code that compiles the first time:**
   - stable APIs;
   - dependency versions copied from a known-good sample;
   - API signatures verified before use;
   - few dependencies and a simple architecture;
   - the quick local checks ([Appendix D](#appendix-d-quick-local-checks)) before every push.
6. **Record every shortcut.** A "Spec says / we did / why" table keeps the spec honest.
7. **Iterate in rounds:** a menu of options → the owner picks → build → install → feedback.
8. **Verify, don't guess.** Look up APIs, URLs and limits in official sources, measure real behavior, and cite what you found.
9. **Make quality visible:**
   - in-app timings for the core experience;
   - a crash catcher that turns a crash into a pre-filled GitHub issue;
   - unit tests in CI.
10. **Data that changes lives in data files:** versioned, validated by tests, and updatable without a new release.
11. **Every change updates the docs in the same round:** README, maintainer notes, spec notes.
12. **Be honest about costs.** State data, battery and privacy costs where the user decides. Experimental features are off by default.
13. **Fix classes of bugs, not instances.** On a crash, find the root cause, then search for the same pattern elsewhere.
14. **Issues are the backlog.** Investigate before building, prefer the cheaper solution, and close issues from the commit message.
15. **Secure before going public.** No secrets in the repo or in CI caches; pull requests from strangers need approval.
16. **Respect the owner's time.** Give short progress notes. When the owner says "wrap up", finish cleanly with a summary and next steps.

## 1. Working agreement

### Asking for decisions

- **Ask only what changes the outcome:** scope, platform, distribution, licensing, design direction, data costs, privacy.
- **Use a form, not prose:**
  - a multiple-choice question;
  - your recommendation as the default;
  - one line on each option's consequence;
  - always room for the owner's own answer.
- **Batch related questions into one form.** Never ask one question at a time when five are pending.
- **Record each answer** in the spec's decision log, with the reason.
- **Example:**

  > **License?** *GPL-3.0-or-later (recommended: keeps forks open)* · *GPL-3.0-only* · *MIT (anyone may close the source)* · *Other…*

### Talking to the owner

- **Plain words, numbers for costs** ("about 1 MB a minute"), no unexplained jargon.
- **Before slow work:** a one-line note on what you're doing and why.
- **Surprises and pivots:** say what you found and what changes.
- **When the owner is in a hurry:** stop adding scope, finish what's started, update the docs, summarize.
- **End every round with the summary** in [Appendix A](#appendix-a-round-summary).

### Respecting the environment

- **Never handle the owner's passwords.** Open the sign-in page and let them sign in.
- **Don't work around network or security policies.** If a network blocks something, test it another way or ask the owner to test on their device.
- **Don't send private code or data to third-party services.**
- **Read before you write:** existing code, docs and conventions first.

### Using the owner's computer

- **Use the shell freely** (PowerShell on Windows, a terminal elsewhere) for:
  - git: status, diff, log and commits, and pushing when the owner wants it;
  - reading, searching and validating files with built-in tools (for example PowerShell's JSON and XML parsers);
  - network checks: DNS lookups, HTTP status codes, response headers;
  - the pre-push checks in [Appendix D](#appendix-d-quick-local-checks).
- **Install nothing:** no SDKs, compilers, runtimes, package managers or global packages, unless the owner asks. If a check needs a tool, add it to the CI workflow instead.
- **Leave no trace:** delete temporary files, and don't change global settings (git config, PATH, environment variables).

## 2. Phase 1: Spec

**Research first:**
- platform constraints and policies;
- data sources and their licenses;
- reference apps (what users already love);
- legal points: trademarks, licenses, unofficial-app disclaimers;
- distribution rules.

**Then write the document set.** Scale it to the project; a small app can merge files, but keep the sections:

| File | Contents |
|---|---|
| `SPEC.md` | Index, at a glance, **decision log** (D-01…), next steps, **implementation notes per build**, glossary |
| `SPEC-01-product.md` | Summary, problem, goals and non-goals, personas, release scope, **functional requirements** (F-01…, each with priority and acceptance criteria), non-functional requirements, backlog, assumptions |
| `SPEC-02-design.md` | Design principles, design system, information architecture (a Mermaid flowchart), navigation and adaptive layout, every screen (ASCII wireframes), system surfaces, localization, accessibility, content and tone |
| `SPEC-03-architecture.md` | Overview diagram, tech stack with exact versions, modules, UI layer, core subsystems, data layer, platform integrations, permissions, networking and security, distribution constraints, **technical spikes** |
| `SPEC-04-data.md` | Source evaluation, data pipeline, data model and schema, automation, governance, legal |
| `SPEC-05-quality-release.md` | Test strategy, static analysis, CI/CD, versioning, signing, distribution, crash reporting, privacy, licensing, operations |
| `SPEC-06-roadmap-risks.md` | Milestones, spikes, risks (likelihood, impact, mitigation), **open questions** for the owner (Q-01…) |

**Writing rules:**
- Tables and bullets over prose.
- Every requirement has an ID, a priority (P0/P1/P2) and acceptance criteria.
- Open questions become decision forms; their answers move into the decision log.

## 3. Phase 2: Foundation (the pipeline before features)

Build the path from code to a phone first. Everything after it depends on it.

1. **Repository:** private at first, under the owner's account.
2. **CI workflow** ([Appendix B](#appendix-b-ci-workflow-skeleton)), run on every push:
   1. Set up the toolchain.
   2. Build the release artifact.
   3. On failure, write the error lines to the run's summary page, in a box that's easy to copy.
   4. Publish a pre-release with the artifact, named `<app>-<run number>.apk`.
   5. Run the unit tests, and write any failures to the summary as well.
3. **Versions:**
   - the build number (`versionCode`) is the CI run number, so each build installs over the previous one;
   - the version name is `<major>.<minor>.<run number>`;
   - raise the minor version every round.
4. **One signing key for every build,** from day 1. Otherwise updates won't install over each other.
   - A key kept in the CI cache is acceptable while the repo is private.
   - Move it into an encrypted secret before going public (§7).
5. **Installation guide for the owner:**
   - **On the phone (no computer needed):** open the release page and tap the APK.
   - **From a computer (optional; needs Android's platform tools):** `adb install -r <file>`.
   - **Updates:** Obtainium (enable pre-releases).
   - **Expect these prompts:** "unknown sources", Play Protect's *Install anyway*, and Android Auto's developer setting for sideloaded apps.
6. **The build loop, with no development tools on the computer:**
   1. The agent runs the quick local checks ([Appendix D](#appendix-d-quick-local-checks)), commits, and pushes (or the owner pushes from their editor).
   2. CI compiles, tests and publishes the build.
   3. The agent reads the result itself where it can (a signed-in browser, the CLI or the API); otherwise the owner pastes the summary box.
   4. A red build is fixed and pushed again before any new work.

## 4. Phase 3: First version (v0.1)

**Build the core loop end to end** (for example: browse → play → background controls). Leave everything else for later rounds.

**Compile-first-time rules** (nothing compiles locally, so every compile error costs a CI round trip of several minutes):

- Prefer stable APIs. Keep experimental ones behind one file or module.
- Copy dependency and toolchain versions from an official, CI-tested sample project instead of guessing a combination.
- Verify exact signatures in the library's source or reference docs before calling an API. List anything you couldn't verify as a compile risk.
- **No build-time code generation in the first build:** no annotation processors, no dependency-injection frameworks. A manual container class is fine.
- **One module.** Packages mirror the modules you might split out later.
- **The simplest storage that works** (key-value storage exposed as flows) before databases.
- **Re-read every edit once** for imports, types, nullability and brackets.
- **Run the quick local checks** ([Appendix D](#appendix-d-quick-local-checks)): they catch broken data and resource files in seconds.

**Also:**
- **Bundle a data snapshot,** so the first launch is instant and works offline.
- **Record every shortcut** in SPEC.md under "Implementation notes: first build":

| Spec says | v0.1 does | Why / next step |
|---|---|---|
| Room + DataStore | Key-value storage exposed as flows | DataStore helpers didn't resolve with the build toolchain; revisit when schedules arrive |

## 5. Phase 4: Iteration rounds

### Each round

1. **The owner says what they want,** or asks "what else?".
2. **The agent researches**, then offers a menu of concrete options:
   - one line each, with its value and cost;
   - grouped as features, quality and polish;
   - including the owner's own ideas.
3. **The owner picks** in a multi-select form.
4. **The agent builds:**
   - the picked items;
   - unit tests for the new pure logic;
   - updates to the docs and to the spec's implementation notes;
   - a version bump.
5. **Push** (the agent, or the owner from their editor) → CI builds → the owner installs and gives feedback.
6. **The agent writes the round summary.**

### Patterns that worked

- **Polish from a reference app.** Study a well-loved open-source app (screenshots and code), list its patterns, let the owner choose, then adapt them; don't copy.
- **Performance with numbers.**
  1. Measure first: time from tap to result, shown in Settings → Performance and as a small "Started in 0.62 s" line.
  2. Optimize: start work on press-down, prefetch likely next items, cache, use smaller start-up buffers, remember what worked.
  3. Measure again.
- **Honest trade-offs.** When the owner's idea has a real cost (data, battery, fake traffic), say so with numbers. Propose a cheaper alternative, and ship the expensive one as opt-in with the cost in its description.
- **Small owner tweaks are done right away,** with strings and docs updated in the same change.

## 6. Phase 5: Quality built in

- **Unit tests** for every pure function and every data file: schema and content validation, time math, parsing, buffers. CI runs them on every push.
- **Crash catcher:**
  - an uncaught-exception handler saves the report on the device (version, device, stack trace);
  - the next launch offers *Copy details* / *Report on GitHub* (a pre-filled issue, size-limited) / *Close*;
  - nothing is sent automatically.
- **Diagnostics** for the core experience: timings, and which path was used (cached, pre-connected, fallback).
- **Threading discipline:** no I/O on the main thread, including closing network connections.
- **Design passes:** dark, pure black, RTL, 200% text, screen reader, reduced motion (DESIGN-PHILOSOPHY.md §13).
- **External data rots.** Hosts move, URLs die, APIs change:
  - validate data files in tests;
  - keep fallbacks;
  - make data updatable without a release;
  - re-check sources periodically.

## 7. Phase 6: Going public

- **README for users:**
  - name, one-line pitch, badges;
  - features;
  - install (releases, Obtainium, adb);
  - privacy and permissions;
  - building from source, contributing, acknowledgements, license;
  - a section in a second language if the audience needs it.
- **LICENSE:** decided with the owner (copyleft or permissive), with any needed exceptions (e.g. linking Google Play services under the GPL).
- **MAINTAINING.md** for the owner: the release flow, how to install builds, the signing key, data maintenance rules, store-policy notes.
- **Security checklist:**
  - [ ] No secrets anywhere in the repo or its history.
  - [ ] The signing key is in an encrypted CI secret. Caches are readable by workflows from pull requests, including forks.
  - [ ] Workflows from outside contributors need approval (repository Settings → Actions).
  - [ ] Permissions are reviewed against store policies (e.g. exact-alarm rules).
- **Third parties:**
  - trademarks and logos are fetched at runtime rather than committed, with a takedown path;
  - a one-line unofficial-app disclaimer.

## 8. Phase 7: Maintenance, with issues as the backlog

For each open issue:

1. **Read it** (with comments) and classify it: crash, bug, data problem or feature request.
2. **Crash:**
   - read the stack trace and find the root cause;
   - search the codebase for the same pattern and fix every instance;
   - add a test if the logic is testable.
3. **Bug or data problem:** reproduce or measure (call the endpoint, compare two runs, check DNS). Fix the code or the data, and add a validation that would have caught it.
4. **Feature request:**
   - measure and research before building;
   - look for a solution better than the one requested (cheaper, automatic, no extra data);
   - ship it behind a setting whose summary states the cost;
   - document how it works in the maintainer notes.
5. **Commit with `Fixes #N`,** so the issue closes when the commit lands on the main branch.
6. **Tell the owner how to verify it** on the device.

**Incidental findings** (another broken item, a dead link): report them separately. Fix them only when they're closely related or the owner agrees.

## 9. Research and verification

- **Never invent URLs, endpoints, API names or versions.** Look them up in official docs, library source, or the provider's own discovery APIs, and cite them.
- **Prefer measuring to assuming:**
  - to learn whether a stream starts with an ad, connect twice, 20 seconds apart, and compare the audio;
  - to learn whether a host is dead, check its DNS and HTTP status from the shell ([Appendix D](#appendix-d-quick-local-checks)).
- **Run independent research in parallel** with helper agents, and keep coding meanwhile.
- **When a test environment distorts results** (a proxy, a firewall), say so, stop, and verify on the owner's device instead.

## 10. Docs as memory

A fresh agent session must be able to continue from the repository alone:

| Document | Audience | Update it when |
|---|---|---|
| `README.md` | Users | Features, installation or privacy change |
| `MAINTAINING.md` | Owner, maintainers | Release flow, data rules or security change |
| `SPEC.md` + implementation notes | Agents, contributors | Every round |
| `DESIGN-PHILOSOPHY.md` | Agents, designers | The look changes |
| `BUILD-PLAYBOOK.md` | Agents | The process changes |

Commit messages say why, not just what. One round is one commit, or a few.

## 11. Lessons learned

| Lesson | Example from Shidur |
|---|---|
| Start with the simplest dependency that works | DataStore's helpers failed with the build toolchain; key-value storage with flows compiled first time |
| Stable APIs before shiny ones | Material 3 Expressive was still experimental; stable M3 with a few expressive touches shipped |
| Bundle only what you use | The full icon library was huge; ~30 icons as vectors in one file instead |
| Validate data formats in tests | Android can't decode SVG; a test rejects `.svg` logo URLs |
| Closing a connection is network I/O | Closing TLS connections on the main thread crashed the app when the network changed |
| Third-party endpoints die silently | A broadcaster's hosts started resolving to 127.0.0.1; two stations broke unnoticed |
| Look for the cheaper solution | Pre-roll ads: instead of preloading streams in the background (lots of data), download the server's fast opening burst and start after the ad |
| CI caches aren't secrets | A signing key in a cache is readable by fork pull requests once the repo is public |
| Store policies constrain permissions | Exact-alarm permissions are restricted for store builds |
| Test environments can lie | An office proxy cut radio streams after 32 KB; the real check happened on the phone |

## Appendix A: Round summary

The agent ends every round with this, in the owner's language:

```
**What's new** – 3 to 6 bullets, in user terms.
**Fixed** – issue numbers, one line each.
**To ship** – the commit message (with "Fixes #N"), and whether the agent already pushed it.
**To test** – numbered steps on the device, with what to expect.
**Heads-up** – untested parts, risks, costs, anything the owner must do.
**Next options** – 2 to 4 suggestions for the next round.
```

## Appendix B: CI workflow skeleton

For an Android app on GitHub Actions. Adapt the toolchain versions and the paths.

```yaml
name: Build

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "<gradle version>"

      # The same key for every build, so each APK installs over the previous one.
      # The release build type signs with this keystore. Create the secret once: the keystore
      # file in base64, under Settings → Secrets and variables → Actions.
      - name: Restore the signing key
        run: |
          mkdir -p ~/.android
          echo "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" | base64 -d > ~/.android/debug.keystore

      - name: Build
        id: build
        run: |
          set -o pipefail
          gradle assembleRelease 2>&1 | tee build.log

      # The error lines, in a box on the run's Summary page: the agent reads it, or the owner pastes it.
      - name: Show build errors in the summary
        if: failure() && steps.build.outcome == 'failure'
        run: |
          {
            echo "## Build errors (copy everything in the box below)"
            echo '```'
            grep -E '^e: ' build.log | head -n 120 || true
            grep -E -A 12 '^\* What went wrong' build.log | head -n 80 || true
            echo '```'
          } >> "$GITHUB_STEP_SUMMARY"

      - name: Name the APK
        run: cp app/build/outputs/apk/release/app-release.apk "app-${{ github.run_number }}.apk"

      - name: Publish a pre-release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: build-${{ github.run_number }}
          name: Build ${{ github.run_number }}
          prerelease: true
          files: app-${{ github.run_number }}.apk

      - name: Run unit tests
        id: tests
        run: |
          set -o pipefail
          gradle testDebugUnitTest 2>&1 | tee test.log

      - name: Show test failures in the summary
        if: failure() && steps.tests.outcome == 'failure'
        run: |
          {
            echo "## Test failures (copy everything in the box below)"
            echo '```'
            grep -E -B 2 -A 8 'FAILED$' test.log | head -n 120 || true
            echo '```'
          } >> "$GITHUB_STEP_SUMMARY"
```

In `app/build.gradle.kts`, take the build number from CI:

```kotlin
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// defaultConfig:
versionCode = buildNumber
versionName = "0.1.$buildNumber"
```

## Appendix C: Kickoff prompt

Paste this into a new agent session, with BUILD-PLAYBOOK.md and DESIGN-PHILOSOPHY.md attached:

```
I want to build <the app idea> for <platform>.
Use BUILD-PLAYBOOK.md for how we work and DESIGN-PHILOSOPHY.md for how it looks.

About me: <how technical you are, which devices you test on>.
My computer: keep it clean. Use the terminal for git and quick checks, but install
nothing: no SDKs, compilers or packages. Builds and tests run in GitHub Actions.
<Commit and push for me | I'll push from my editor>.
Constraints: <budget, accounts, distribution (store or sideloading), languages,
privacy expectations, deadline>.

Start with research and the spec (playbook §2). Ask me the key decisions as
multiple-choice questions with your recommendation. Then set up the build pipeline
(§3) and ship a first version I can install on my phone (§4).
```

## Appendix D: Quick local checks

Run these in the shell before every push. They use only what ships with Windows and PowerShell 7; adapt the paths. On other systems, use the equivalents that come with the system.

```powershell
# What changed
git --no-pager status --short
git --no-pager diff --stat

# Data files parse (stops with an error if not)
Get-Content app/src/main/assets/catalog.json -Raw -Encoding UTF8 | ConvertFrom-Json | Out-Null

# Every resource file is well-formed XML (prints the broken ones)
Get-ChildItem app/src/main/res -Recurse -Filter *.xml | ForEach-Object {
  $file = $_.FullName
  try { [xml](Get-Content $file -Raw -Encoding UTF8) | Out-Null } catch { "Broken XML: $file" }
}

# Android strings: an apostrophe must be escaped as \' (prints the offending lines)
Select-String -Path app/src/main/res/values*/strings.xml -Encoding UTF8 -Pattern '<string[^>]*>(?!")[^<]*(?<!\\)'''

# A host or stream answers
Resolve-DnsName example.com
(Invoke-WebRequest https://example.com/ -Method Head).StatusCode
```

- The file checks print nothing when everything is fine.
- Behind a corporate proxy, `curl.exe` can fail its certificate revocation check (error 35). `Invoke-WebRequest` works there, or add `--ssl-no-revoke` to curl.
- Compiling, unit tests and lint run in CI only.
