# Contributing to LYNQ

LYNQ is the CBIT Hackathon build by **Team FLUX**. Four people work on this repo at the same time, so the rules below are about keeping merges boring.

Full rationale and the ownership table live in [`docs/COLLABORATION.md`](docs/COLLABORATION.md). This file is the checklist.

## The one rule that matters

**Nobody pushes to `main`.** Work on a branch, open a PR, get one review, squash-merge.

## Setup (once per machine)

```bash
git clone https://github.com/vvmsaiganesh/LYNQ.git
cd LYNQ

# Commit as yourself. Authorship is the deliverable.
git config user.name  "Your Name"
git config user.email "you@users.noreply.github.com"

# Build env
cp local.properties.example local.properties   # or write sdk.dir yourself
echo "FEATHERLESS_API_KEY=" >> local.properties
```

`local.properties` is git-ignored and must stay that way — it holds your SDK path and your AI key.

## Daily loop

```bash
git switch main
git pull --rebase
git switch -c feat/<yourname>-<area>      # e.g. feat-asr-mesh-retry

# ...edit...

./gradlew :app:assembleDebug :app:testDebugUnitTest
git add -p                                # stage only what you meant
git commit -m "feat(mesh): retry beacon after 3 lost ACKs"
git pull --rebase origin main
git push -u origin feat/<yourname>-<area>
```

Then open the PR. One teammate reviews, you squash-merge, delete the branch.

## Commit messages

`type(scope): what changed`

| type | use for |
|---|---|
| `feat` | new behaviour |
| `fix` | bug fix |
| `refactor` | no behaviour change |
| `test` | tests only |
| `docs` | README, docs, comments |
| `chore` | build files, deps, tooling |

Scope is the folder: `mesh`, `transport`, `crypto`, `data`, `service`, `ui`, `ai`, `docs`.

One logical change per commit. `fix(ui): typo` and `feat(mesh): new router` are two commits, not one.

## Folder ownership

Write freely inside your folders. Announce before touching a shared file.

| Owner | Writes |
|---|---|
| Mesh / transport | `mesh/`, `transport/` |
| UI | `ui/` |
| Crypto / data / service | `crypto/`, `data/`, `RelayService` |
| AI / docs / demo | `ai/`, `docs/`, `README.md` |

**Shared files — change only after the group agrees:** `mesh/Packet.kt`, `transport/Transport.kt`, `data/Message.kt`, `ServiceLocator.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`.

Prefer adding a new file over editing a shared one.

## Before you push

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes
- [ ] No `local.properties`, `build/`, `.idea/`, `*.apk`, `*.jks` in the diff
- [ ] No API keys or tokens in code
- [ ] Rebased on latest `main`
- [ ] Commit message follows the format above

## Build environment

| Tool | Version |
|---|---|
| JDK | 21+ (Android Studio JBR works) |
| Android SDK | compileSdk / targetSdk 37, minSdk 26 |
| Gradle | 9.5.0 (wrapper) |

Windows: `set JAVA_HOME=<Android Studio>\jbr` before `gradlew.bat`. A JDK newer than 25 may fail — use the JBR that ships with Android Studio.

## Reporting

Found a bug in the mesh path? Include: device models, Android versions, the hop count you saw, and the `adb logcat -s RelayMesh` lines. Routing bugs are almost never reproducible without that.
