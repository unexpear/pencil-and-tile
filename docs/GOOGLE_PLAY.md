# Google Play preparation

Checked against official documentation on September 23, 2026. This is a release
preparation guide, not confirmation that Google has approved the app.

## Build configuration

- Compile and target SDK: 36. Google Play currently requires API 36 for new
  phone/tablet apps and updates from August 31, 2026.
- Android Gradle Plugin 8.10.1 and Gradle 8.11.1, with JDK 17. This is a supported
  API 36 toolchain; unrelated library versions remain pinned rather than being
  upgraded solely to silence newer-version notices.
- App name **Pencil & Tile**; application ID **`com.simplegamegen.puzzles`** (chosen by the owner on
  2026-09-25; it can never change after the first upload). The code namespace stays
  `com.simplegamegen.sudoku`, which Play never sees.
- Version code 1, version name 1.0.0. Increase the version code for every upload.
- No permissions that reach outside the device: the merged release manifest requests no INTERNET or
  other dangerous permission.
- Native libraries (ONNX Runtime for Word Meaning): the 64-bit libraries are 16 KB page aligned, as Play
  requires for apps targeting Android 15+. Check again whenever ONNX Runtime is upgraded.
- Expected download on an arm64 phone: roughly 35–40 MB (the 23 MB sentence model plus about 7 MB of
  compressed runtime); the Play bundle delivers only the phone's own processor libraries.
- Adaptive launcher icon, themed monochrome icon, explicit backup rules,
  Android 16 insets, scrollable screens and accessible cell labels are included.

## Build and sign

Run the Gradle wrapper from the repository root:

```powershell
./gradlew :sudoku-engine:test :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:bundleRelease
```

The release bundle is `app/build/outputs/bundle/release/app-release.aab`.
Without upload credentials it is unsigned and must not be submitted to Play.

Create or select your own upload keystore using Android Studio's **Generate
Signed Bundle / APK** workflow. Keep the keystore and its backups outside the
repository. The build optionally reads these four environment variables:

| Variable | Value |
| --- | --- |
| `PENCILTILE_KEYSTORE_PATH` | Absolute path to the upload keystore |
| `PENCILTILE_KEYSTORE_PASSWORD` | Keystore password |
| `PENCILTILE_KEY_ALIAS` | Upload key alias |
| `PENCILTILE_KEY_PASSWORD` | Upload key password |

Set all four in your local environment or CI secret store, then run
`:app:bundleRelease`. Partial configuration fails rather than silently producing
an unsigned artifact. No upload key has been generated or selected for you.
Enroll in Play App Signing and retain the upload key securely. Never use a debug
key for a Play release. Release shrinking is currently disabled; enabling it
should be accompanied by installed-release regression testing.

## Data and privacy

The current app stores the puzzle, moves, notes, elapsed time, hint counts, and
aggregate play/win/best-time statistics on the device. There is no account,
advertising SDK, analytics SDK, billing integration or app server. Android may
back up game data and statistics to the user's backup provider and transfer them
to a new device, according to device settings. Clearing the app's storage removes
its current local data; system backups are controlled through Android settings.

Before launch, publish a privacy policy at a working public URL and make it
accessible in the app and Play listing. Include your actual developer identity,
contact details, retention/deletion information, and the Android backup behavior.
Do not reuse a claim that all data stays exclusively on-device while cloud backup
is enabled. Complete Play Console's Data safety form based on the final shipped
app and Google's definitions. Reassess disclosures if ads, analytics, purchases,
cloud saves or other SDKs are added.

Crosswords and word searches also store their full boards, chosen difficulty,
entered letters, found words and hint counts in the app's backed-up DataStore directory. Their
English word lists and crossword clues are bundled with the app; these modes
make no network requests and introduce no external puzzle-content licenses.

Hangman and Mahjong Solitaire save their exact games, guesses/moves and hint
counts separately. A shared DataStore also retains the generation sequence and
bounded recent-puzzle fingerprints (recent answers for Hangman). These files are
included in Android backup. The additional modes introduce no SDKs or permissions.

Solitaire, Minesweeper, Checkers, Reversi and Dominoes additionally save their
settings, initial layouts and move histories in the same backed-up directory.
Computer opponents run locally. These additions introduce no network requests,
accounts, advertising or external content services.

## Store listing, privacy and forms

- Listing text for en-US, zh-CN, ja-JP, es-ES and de-DE: `docs/store/listing.md`
  (`python docs/store/check_listing.py` checks Play's length limits).
- Privacy policy: `PRIVACY.md`, published at https://github.com/unexpear/pencil-and-tile/blob/main/PRIVACY.md
  (linked from the listing and from the app's Settings → About).
- **Data safety form:** "Does your app collect or share any of the required user data types?" → **No**.
  The app has no internet permission, SDKs, accounts, ads or analytics; typed Word Meaning answers are judged
  on the device. Android backup is handled by the platform, not collected by the developer.
- **Content rating (IARC questionnaire):** category *Game* (puzzle); no violence, fear, sexuality, profanity,
  gambling or simulated gambling (solitaire has no betting), no user-to-user interaction, no sharing of
  location or personal information, no purchases. Expected result: Everyone / PEGI 3 / USK 0.
- **Target audience:** simplest is 13 and over. Including under-13 ages is possible (the app has no ads or
  data collection) but brings the Families policy and extra review; choose it only if you want to market
  to children.
- **Ads declaration:** No ads. **App access:** all features available without login.
- Trademark care: store text uses generic names only (Calcudoku, not KenKen; in Japanese ナンプレ, not the
  trademarked 数独; クロスサム, not カックロ). Inspired games have their own names.

## Closed test with the same testers as the other app

Personal developer accounts created after 13 November 2023 must run a closed test for **each new app**:
at least **12 testers opted in continuously for 14 days**, then apply for production access from the
Dashboard. The other app's test doesn't count for this one, but its tester list can be reused.

1. Create the upload key (Android Studio → Build → Generate Signed Bundle / APK → create new keystore),
   keep it and its passwords outside the repository, set the four `PENCILTILE_*` variables and run
   `./gradlew :app:bundleRelease`.
2. Play Console → **Create app** → name "Pencil & Tile", game, free. Keep it separate from the existing app.
3. **Test and release → Testing → Closed testing** → create a track → **Testers**: tick the **same email
   list** used by the other app (email lists belong to the developer account and appear for every app), or
   add the same **Google Group** address if the other app used one.
4. Create a release on that track, upload `app-release.aab`, add release notes and roll it out.
5. Copy the track's **opt-in link** (Testers tab). Testers must open this new link and install this app,
   even if they tested the other app: the 14 days count from each tester's opt-in to *this* app.
6. Ask testers to stay opted in and keep the app installed for the full 14 days; keep at least 12 opted in
   (inviting a few extra helps if anyone drops out).
7. After 14 days: **Dashboard → Apply for production** and answer the questions about the test.

Invite message to send the testers (fill in the link):

> Hi! I've made a new puzzle app, Pencil & Tile: 32 offline puzzle, word, card and board games. Google
> needs 12 people to test it for 14 days before it can go public. If you're up for it:
> 1. Open this link on your Android phone and tap "Become a tester": [OPT-IN LINK]
> 2. Install Pencil & Tile from the Play Store link on that page.
> 3. Keep it installed for at least 14 days and play whenever you like. Feedback is welcome!
> It's a separate app from the last one, so please join this test too. Thank you!

### Public tester link

- Opt-in link (works once the closed test is rolled out): https://play.google.com/apps/testing/com.simplegamegen.puzzles
- Store page for opted-in testers: https://play.google.com/store/apps/details?id=com.simplegamegen.puzzles
- A closed test only admits listed testers. To post the link publicly, create a Google Group (for example
  `pencil-and-tile-testers@googlegroups.com`) with **Who can join: Anyone on the web**, add the group's address
  to the closed test's testers (next to the other app's email list), and post both links.

Post for social media or forums (fill in the group link):

> 🧩 Help test Pencil & Tile, a free Android puzzle app: 32 offline puzzle, word, card and board games
> (Sudoku, crosswords, solitaire, a word-grouping game, a word-meaning game and more). No ads, no account.
> Google asks new apps to be tested by 12+ people for 14 days before launch. To join:
> 1. Join the tester group: [GROUP LINK]
> 2. Open https://play.google.com/apps/testing/com.simplegamegen.puzzles on your Android phone and tap "Become a tester"
> 3. Install it from Google Play and keep it for at least 14 days. Feedback welcome!

## Remaining release work

- Fill in the developer contact in the privacy policy and publish it.
- Configure upload signing, then test a Play internal-track installation of the
  signed bundle. A successful unsigned local build is not a publishable release.
- Test phones and tablets on Android 8, 15 and 16, including narrow/landscape
  layouts, font scaling, screen readers and navigation with system bars.
- Test cold-start Continue, rotation, background/foreground, abrupt process
  termination, Android backup/restore, and offline play. Play's pre-launch report
  supplements these checks; it does not replace them.
- Exercise all variants and difficulties, especially slow 9×9 Killer generation.
- Verify packaged native dependencies for 16 KB page-size support when changing
  libraries. Pure Kotlin/Java code needs no native alignment changes, but
  dependencies can introduce native binaries.
- Store graphics are ready in `docs/store/`: `graphics/icon-512.png` (hi-res icon),
  `graphics/feature-1024x500.png` (feature graphic, regenerate with `python tools/store_graphics.py`) and
  9:16 screenshots in `screenshots/` (Play only accepts 16:9 or 9:16): `phone-*` 1080×1920, `tablet7-*`
  1080×1920 at tablet density and `tablet10-*` 1440×2560 (home, Sudoku, Common Threads, Five Letters, Word
  Meaning, Mahjong). Play shows these for every language unless localized ones are added.
- Still needed from you: support email and privacy-policy URL.
- Complete content rating, target audience, ads/app-access declarations, Data
  safety and any account-specific testing/verification requirements shown by
  Play Console. No Play Console declarations or uploads were made by this task.

## Official references

- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [AGP 8.10 compatibility](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Signing and Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Backup behavior and rules](https://developer.android.com/identity/data/autobackup)
- [16 KB page-size compatibility](https://developer.android.com/guide/practices/page-sizes)
