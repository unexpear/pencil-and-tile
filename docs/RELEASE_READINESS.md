# Release readiness — 26 September 2026

Verdict: signed local release candidate with Android 8/15/16 smoke checks;
version 1.1.0 (code 2) is built for closed testing. Public Google Play launch
is blocked by the account's production-access testing requirement. The signed
code-2 bundle was uploaded through the native Windows file picker, release notes
were entered in all five languages, and the Alpha update was submitted. Console
now lists it under "Changes in review"; quick checks are still running and must
pass before Google review proceeds. The update is not yet approved or published.

## Verified locally

- Rechecked engine tests, debug/release app tests, both lint variants, release
  bundle and release APK through Gradle. Build successful. Unchanged checks
  reused Gradle's up-to-date results from the preceding full run.
- 377 distinct tests: 284 engine and 93 app; 470 executions including release
  app tests, zero failures/errors/skips. Lint: zero errors, 15 warnings and two
  hints per variant. Warnings are dependency update notices and two KTX suggestions.
- Application ID `com.simplegamegen.puzzles`, minimum API 26, target API 36,
  version 1.1.0, version code 2. Target meets the current Play submission rule.
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
- The preceding code-1 signed release APK installed on separate Android 8 (API 26), Android 16
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

1. Production access is locked. The authenticated Console dashboard reports one
   opted-in tester; it requires at least 12 testers opted in continuously for
   14 days before applying for production access.
2. Await Google quick checks and review for the submitted code-2 Alpha update.
   Console accepted the bundle with no blocking validation errors and two
   warnings: no deobfuscation mapping and no native debug symbols. Release
   shrinking/obfuscation is disabled; native crash symbolication remains limited.
   The submitted change targets 100% of the existing closed Alpha track only.
   Managed publishing is off, so approval can release it to those testers. The
   prior code-1 / 1.0.0 Alpha release remains the last confirmed available build.
3. Test the signed, Play-delivered build and review its pre-launch report.
   Physical arm64 hardware, screen-reader use, system backup/restore and a
   complete all-game regression remain unverified. The emulator smoke checks
   above preceded the version-only bump; code 2 passed the build/test/lint gates.
4. Console's App content page reports no outstanding tasks. IARC is completed,
   with ESRB Teen and PEGI 12 among its regional ratings. Exact questionnaire
   answers and rating descriptors were not verified; confirm that they reflect
   fantasy combat and optional blood/ink effects before submission.
5. Automated publishing still needs PLAY_SERVICE_ACCOUNT_JSON. Four signing
   secrets are configured. The browser upload does not require that credential.
6. Gameplay test results do not establish legal clearance for the
   reference-inspired game. Final content/branding rights review is outstanding.

Console was inspected through the owner's authenticated Edge session on
26 September 2026. No browser profile was copied. The extension connection
credential is not stored in repository files. Code changes are on `more-games`;
no release tag or push was made. The prior implementation is commit `7024813`.

## Artifact

`app/build/outputs/bundle/release/app-release.aab`

SHA-256: `50E2755959ED7426E17BD987A7F3613844C470312E7727161AFF58F1EBB764B9`

## References checked

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Content ratings](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en)
- [Android 16 KB checks](https://developer.android.com/guide/practices/page-sizes)
- [Published privacy policy](https://github.com/unexpear/pencil-and-tile/blob/main/PRIVACY.md)
