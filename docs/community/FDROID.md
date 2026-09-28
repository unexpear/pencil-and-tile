# Getting into F-Droid and IzzyOnDroid

Pencil & Tile is GPL-3.0-or-later, has no ads, tracking or Google Play Services, and its release build works
without the private signing key, so it fits both open-source app stores. Store text, icon and screenshots are
already in `fastlane/metadata/android/` (rebuilt by `tools/store_metadata.py` on every release).

Both need a GitHub release to point at, so submit after 1.1.0 is tagged. Each release now attaches a signed
APK (`pencil-and-tile-vX.Y.Z.apk`) to https://github.com/unexpear/pencil-and-tile/releases.

## 1. IzzyOnDroid (fastest, usually days)

IzzyOnDroid lists the developer's own signed APK from GitHub releases, so installs there can update to
later GitHub releases. Its inclusion policy and request form are linked from https://apt.izzysoft.de/fdroid/.
The request needs a free account on their issue tracker. Give it:

- App: Pencil & Tile, `com.simplegamegen.puzzles`
- Source: https://github.com/unexpear/pencil-and-tile (GPL-3.0-or-later)
- APK: the latest GitHub release
- Description and screenshots: fastlane metadata in the repo

## 2. F-Droid (the main repository, usually weeks)

F-Droid builds the app from source itself and signs it with its own key. Open a "Request For Packaging"
issue at https://gitlab.com/fdroid/rfp/-/issues (free GitLab account), or send a merge request to
https://gitlab.com/fdroid/fdroiddata adding `metadata/com.simplegamegen.puzzles.yml`:

```yaml
Categories:
  - Games
License: GPL-3.0-or-later
AuthorName: unexpear
SourceCode: https://github.com/unexpear/pencil-and-tile
IssueTracker: https://github.com/unexpear/pencil-and-tile/issues
Changelog: https://github.com/unexpear/pencil-and-tile/releases

AutoName: Pencil & Tile

RepoType: git
Repo: https://github.com/unexpear/pencil-and-tile.git

Builds:
  - versionName: 1.1.0
    versionCode: 2
    commit: v1.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 2
```

Things the F-Droid reviewers may ask about, and the answers:

- **ONNX Runtime** (`com.microsoft.onnxruntime:onnxruntime-android`, MIT) comes from Maven Central with
  prebuilt native libraries. If they want it built from source or removed, Word Meaning can fall back to its
  word checks alone (they already decide most answers; the model is only a tie-breaker).
- **The sentence model** (`all-MiniLM-L6-v2`, Apache 2.0) ships as a data file in the app's assets. Its
  source is the sentence-transformers project on Hugging Face.
- **Word lists** (`*.txt.gz`, categories) are generated data; the scripts that build them are in
  `tools/words/` and `tools/meaning/`.
- **English lexicon** (`assets/lexicon/lexicon.db`) is built during Gradle by
  `:sudoku-engine:buildLexiconDb` from Open English WordNet 2025, the public-domain ENABLE list, and
  Tatoeba CC0 English sentences. The task needs network unless those files are already cached or passed
  in. It is separate from the reviewed Word Meaning bank. See `tools/lexicon/DESIGN.md`.
- **Anti-features:** none (no ads, tracking, non-free network services or non-free assets).

## After they're listed

- Add the badges to the README and the store listing's full description ("Also on F-Droid").
- The F-Droid build is signed with F-Droid's key, so a player can't switch between the Play, GitHub and
  F-Droid versions without uninstalling (their saves stay on the phone they were made on).
