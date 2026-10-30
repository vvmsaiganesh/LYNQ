# Team workflow — how four people edit one app

The goal: anyone can be mid-change and still pull. No one waits for anyone, no one overwrites anyone.

## The problem this solves

An Android app is not four separate files. The mesh engine touches the packet format, the UI renders the packet results, the service owns the engine. A naive "everyone pushes to `main`" setup produces conflicts on the same three files every single day.

So the code was deliberately layered so that **most changes stay inside one folder**, and the few files that cross layers are named and treated as contracts.

## Branch model

```
main ──────●────────●─────────●──────▶  always demo-ready
            \      /          /
             ●────●          /          feat-<name>-<area>
                  \         /
                   ●───────●             feat-<name>-<other>
```

- `main` is protected by convention: no direct pushes.
- Every change is a short-lived branch, ideally under a day. Long branches are where merge pain comes from.
- Rebase on `main` before pushing. Squash-merge the PR so `main` history reads as one commit per feature.
- No `develop` branch. With four people it is pure merge debt.

Why squash: reviewers see one coherent change, `git log --oneline main` stays readable for the judges, and a half-finished commit in the middle of your branch never lands.

## Why rebase and not merge

`git pull --rebase` rewrites *your* unpushed commits on top of `main`. Your work is never mixed into a merge commit, so a conflict shows up in your branch where you can fix it, not on `main` where everyone is blocked.

If a rebase goes wrong: `git rebase --abort`. Nothing is lost.

## Ownership map

| Area | Owner | Folder |
|---|---|---|
| Mesh routing, hop budget, dedup, presence gossip | Person A | `app/src/main/java/com/flux/relay/mesh/` |
| Bluetooth / Wi-Fi Direct transport | Person A | `.../relay/transport/` |
| Screens, navigation, graph rendering, theme | Person B | `.../relay/ui/` |
| Crypto, storage, foreground service | Person C | `.../relay/crypto/`, `.../relay/data/` |
| AI triage, docs, demo video | Person D | `.../relay/ai/`, `docs/`, `README.md` |

Person A's split is intentional — transport and mesh are one mental model. If A is overloaded, Person C takes transport.

### Shared files (contracts)

These are the only real collision points. Change them by agreement in the group chat, one PR at a time, never two in flight.

| File | Why it's shared |
|---|---|
| `mesh/Packet.kt` | Wire format. A change here breaks every peer on an older build. |
| `transport/Transport.kt` | Interface between mesh and Bluetooth. |
| `data/Message.kt` | Model read by mesh, UI, and DB. |
| `ServiceLocator.kt` | Wiring for all four areas. |
| `AndroidManifest.xml` | Permissions and components. |
| `app/build.gradle.kts` | Dependencies. |

Rule of thumb: **add, don't edit.** A new field with a default value doesn't break teammates. A renamed field does. Never delete or repurpose a field in a shared model during the hackathon.

## Conflict avoidance, concretely

1. **Pull before you start, not after you finish.** Starting from `main` that is a day old is how you get conflicts.
2. **Commit small.** A 40-file commit cannot be merged; a 4-file commit almost always can.
3. **Don't reformat.** Reformatting a file you barely changed makes every line a conflict. No import reordering, no line-ending churn — `.gitattributes` handles endings.
4. **Don't touch other people's files "while you're in there".** Open an issue instead.
5. **Never commit generated files.** `build/`, `.gradle/`, `.idea/`, `local.properties`, `*.apk` are ignored; if one appears in `git status`, the ignore rule is broken — fix the rule, don't commit the file.

## Build gate

Every PR must pass locally before it is opened:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Windows requires `JAVA_HOME` pointing at Android Studio's JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

A PR that does not build is closed, not reviewed. `main` must always be installable — that is what gets recorded for the demo.

## Authorship

The deliverable asks for clean commits from all members, and GitHub records whoever's `user.email` is configured on the machine that commits. There is no way to attribute a commit to a teammate from someone else's laptop — so each person clones, configures their own identity, and pushes their own branch:

```bash
git config user.name  "Teammate Name"
git config user.email "teammate@users.noreply.github.com"
```

The scaffold was committed once from the project owner's account. From then on, every feature commit belongs to whoever wrote it.

## Demo freeze

Two hours before recording:

1. Everyone pushes and merges. No new branches.
2. Tag the build: `git tag -a v1.0-demo -m "Hackathon demo build" && git push origin v1.0-demo`.
3. Install that tag on all three phones.
4. Rehearse the mesh graph shot — the video is judged on visible hops.

After the freeze, only `fix(demo):` commits, and only if the demo is broken.

## If something goes wrong

| Situation | Fix |
|---|---|
| Rebase conflict | Resolve, `git add`, `git rebase --continue`. Unsure? `git rebase --abort`. |
| Pushed a bad commit to your branch | `git revert <sha>` and push — don't force-push a branch someone is reviewing. |
| Accidentally committed `local.properties` | `git rm --cached local.properties`, commit, verify `.gitignore`. |
| Lost work | `git reflog` — it's almost never actually gone. |
| Two people edited a shared file | Second PR rebases and re-applies by hand. This is why the list is short. |
| `main` is broken | Whoever broke it reverts first, fixes second. Demo readiness beats correctness of the fix. |
