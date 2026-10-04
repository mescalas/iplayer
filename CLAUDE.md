# iPlayer — notes for Claude

Android TV IPTV player (Kotlin, Jetpack Compose, Media3). Single module `:app`, no unit tests.

## Verifying a change

Never report a change as working without one of the two checks below.

### 1. Local build (cloud sessions)

`.claude/hooks/session-start.sh` installs the Android SDK in `~/android-sdk` and warms the Gradle cache.
It needs `dl.google.com` to be allowed by the environment's network policy: the SDK, the Android Gradle
Plugin and every AndroidX library are served from there (`maven.google.com` only redirects to it).
If the hook printed "dl.google.com is unreachable", no Gradle command can work in the session: go to 2.

- Fast compile check: `./gradlew --no-daemon :app:compileDebugKotlin`
- Full APK (same as CI): `./gradlew --no-daemon assembleRelease`

### 2. GitHub Actions (always available)

`Build APK` (`.github/workflows/build.yml`) runs on every push, on every branch. After pushing:

1. `mcp__github__actions_list` with `method: list_workflow_runs`, `workflow_runs_filter.branch: <branch>`:
   find the run whose `head_sha` is the pushed commit.
2. Wait for it (a build takes about 3 minutes) and check `conclusion`.
3. On failure: `mcp__github__get_job_logs` with `run_id`, `failed_only: true`, `return_content: true`,
   fix, push again. Repeat until green.

`UI test (Android TV emulator)` (`.github/workflows/ui-test.yml`) is manual only: start it with
`mcp__github__actions_run_trigger` (`method: run_workflow`, `workflow_id: ui-test.yml`, `ref: <branch>`).
It drives the app on an emulator against a mock Xtream server (`.github/ci/`) and pushes its screenshots to
`refs/ci-shots/latest`: `git fetch origin refs/ci-shots/latest && git archive FETCH_HEAD | tar -x -C <scratch dir>`,
then look at the PNGs.

## Releases

A push to `main` publishes a GitHub release that the in-app updater installs; feature branches only upload
an APK artifact.
