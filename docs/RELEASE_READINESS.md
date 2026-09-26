# Release readiness — 26 September 2026

Verdict: signed local release candidate with Android 8/15/16 smoke checks;
public Google Play launch is still blocked on account access and release setup.
This is a verification report, not an upload or publication approval.

## Verified locally

- Rechecked engine tests, debug/release app tests, both lint variants, release
  bundle and release APK through Gradle. Build successful. Unchanged checks
  reused Gradle's up-to-date results from the preceding full run.
- 377 distinct tests: 284 engine and 93 app; 470 executions including release
  app tests, zero failures/errors/skips. Lint: zero errors, 15 warnings and two
  hints per variant. Warnings are dependency update notices and two KTX suggestions.
- Application ID `com.simplegamegen.puzzles`, minimum API 26, target API 36,
  version 1.0.0, version code 1. Target meets the current Play submission rule.
- Release merged manifest has no internet or dangerous permissions and no
  debuggable flag. Its only requested permission is an app-owned signature permission.
- AAB signature integrity verified. APK signature verifies using v2 signing.
  Both match the upload certificate recorded in the repository workflow:
  `BE:42:70:FE:B7:74:35:DB:88:FB:32:52:49:2D:98:31:3A:79:08:B6:1B:99:85:3F:11:39:7D:A5:21:46:22:8B`.
  Matching Play Console's registered certificate was not independently checked.
- Release APK passes `zipalign -c -P 16 -v 4`. Inspected arm64-v8a and x86_64
  native LOAD segments use 16 KB alignment. This does not establish runtime
  compatibility on every 16 KB device. A subsequent x86_64 16 KB runtime smoke
  test loaded ONNX successfully; physical arm64 device coverage remains outstanding.
- Public privacy policy is reachable, contains developer contact and backup
  disclosures, and matches the URL used by Settings. All five store listings
  and five release-note files pass their applicable character limits.
- The preceding Android 16 debug-device checks cover Wordsworn combat, rewards,
  shops, old/new save restoration and larger text; see VERIFICATION.md.
- Signed release APK installed on separate Android 8 (API 26), Android 16
  (API 36), and Android 15 16 KB (API 35) emulators. Android 8 generated a
  playable Sudoku. Android 16 restored a manually entered 6 in row 1/column 1
  after backgrounding, force-stop and relaunch through Continue.
- At 1920x1200, density 240, font scale 1.3, Sudoku's controls remained
  reachable through scrolling. Its full-width board is oversized in landscape;
  this is a remaining layout-polish limitation, not a claim of ideal tablet UX.
- API 35 returned PAGE_SIZE=16384. Word Meaning opened, the native loader
  reported ONNX JNI loaded successfully, and a submitted definition received
  "Right!" and two points. No app crash appeared. The emulator's system_server
  crashed during initial boot before app installation and recovered; that event
  is retained in build/release16-crash.log, not attributed to the app.
- Four GitHub signing secrets are now configured and their names verified.
  The workflow now gates releases on engine tests, debug/release app tests and
  both Android lint variants. No service-account credential was supplied.

## Outstanding before release

1. Confirm the content-rating questionnaire reflects fantasy combat and the
   optional blood setting. Play requires updated answers when content changes
   affect the rating. Other Play declarations and review status were not inspected.
2. Test the signed, Play-delivered build and review the pre-launch report.
   Local signed smoke checks now cover API 26/35/36, one tablet-size layout and
   x86_64 16 KB native loading. Physical arm64 hardware, screen-reader use,
   system backup/restore, all-game regression and Play-installed delivery remain
   unverified. The smoke checks do not cover this entire release gate.
3. If version code 1 has already been uploaded, select a higher code before the
   next upload. Current Play version codes and production/testing eligibility
   were not available to this audit.
4. For automated publishing: signing secrets are configured, but
   `PLAY_SERVICE_ACCOUNT_JSON` is still missing. Its local file path has been
   requested; no unrelated credential locations were searched.
5. The prepared changes are being committed with this report. No version tag or
   release push was made. The last inspected successful remote CI run was for
   commit `8ab9e09`, not the current changes.
6. Confirm the final content/branding rights review. Gameplay test results do
   not establish legal clearance for the reference-inspired game.

Console access: the supplied developer-account URL redirects to Google sign-in
in the Codex browser. Playwright attachment was attempted for Chrome and the
open Edge browser; both lack the Playwright extension and no running debugging
connection was found. The user's browser was not restarted or its profile copied.
Console ratings, uploaded version codes and release eligibility remain unknown.

## Artifact

`app/build/outputs/bundle/release/app-release.aab`

SHA-256: `455B714342BD0F14B57B9734C74FA9347BF93C3995D54CF15ED31A37B0A203D1`

## References checked

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en)
- [Android 16 KB checks](https://developer.android.com/guide/practices/page-sizes)
- [Published privacy policy](https://github.com/unexpear/pencil-and-tile/blob/main/PRIVACY.md)
