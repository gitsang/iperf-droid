# iperf-droid

A graphical iperf3 tool for Android TV: pick a quick command from the home screen
with the remote, or open the configuration screen (top right) to manage several
saved iperf3 argument sets (e.g. `-c 192.168.5.200`). Tap one to start a test and
watch the output live.

| Item       | Details                                         |
| ---------- | ----------------------------------------------- |
| App name   | iperf-droid                                     |
| Package    | `com.example.iperfdroid`                        |
| Minimum    | Android 5.0 (API 21)                            |
| Target     | Android 14 (API 34)                             |
| Stack      | Kotlin + AndroidX ConstraintLayout              |
| License    | MIT (Also see [THIRD_PARTY.md](THIRD_PARTY.md)) |
| Repository | <https://github.com/gitsang/iperf-droid>        |

## Getting and installing

```bash
git clone git@github.com:gitsang/iperf-droid.git
cd iperf-droid
```

Build it yourself:

```bash
./build.sh                 # or ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also download a prebuilt APK from [Releases](https://github.com/gitsang/iperf-droid/releases) and install it on the TV.

> Note: on first launch the app unpacks the iperf3 binary for the device's ABI into
> its private directory, after which it works fully offline.

## Known issues and fixes

### Log output only appears when the test ends (fixed)

**Cause**: iperf3 writes results to stdout. When stdout is a **pipe**, libc uses full
buffering (typically 4 KB), while iperf3 emits only a few dozen bytes per interval and
does not flush by default — so the output piles up in the buffer and is only released
when the test finishes.

In the source: iperf3 3.9 `src/iperf_api.c` → end of `iperf_print_intermediate()`:

```c
if (test->logfile || test->forceflush)
    iflush(test);
```

In other words, iperf3 itself provides a `--forceflush` switch to flush on every interval.

**Fix**: the app appends `--forceflush` before executing (and does not duplicate it if the
user already supplied it). See `CommandBuilder.withRealtimeOutput()`, guarded by
`CommandBuilderTest`.

Related improvements:

- The run screen appends incrementally with `TextView.append()` instead of rebuilding the
  whole text on every line — a test running for minutes can emit thousands of lines, and a
  full rebuild makes the UI stutter, which is worse than no real-time output at all.
- The run screen has a **live rate bar** at the top, refreshed on every interval result
  (current rate / elapsed time / UDP jitter and loss); parsing lives in `IntervalParser`.
- **JSON mode (`-J`) is the exception**: iperf3's JSON mode accumulates everything into a
  JSON tree and prints it in one go at the end, with no per-interval rate lines. The run
  screen shows a "test running for N seconds" timer instead, so users do not think it hung.

### `iperf3: error - unable to create a new stream: No such file or directory`

**Cause**: when iperf3 sets up a data stream it calls `mkstemp()` to create a buffer file,
whose path comes from the `TMPDIR` / `TEMP` / `TMP` environment variables, falling back to a
hardcoded `/tmp`. **Android has no `/tmp`**, so `mkstemp()` returns `ENOENT` and iperf3 exits
with `IECREATESTREAM`, printing that error.

In the source: iperf3 3.9 `src/iperf_api.c` → `iperf_new_stream()`; the message mapping is in
`src/iperf_error.c` → `iperf_strerror()` (`IECREATESTREAM` carries `perr=1`, so `strerror(errno)`
is appended, which is why it reads `...: No such file or directory`).

**Fix**: when launching the iperf3 child process the app explicitly injects
`TMPDIR` / `TMP` / `TEMP` → `context.cacheDir/tmp` (a writable app-private directory).
See `IperfRunner.tempEnv()`, guarded by `TempEnvTest`.

### Diagnostics

Configuration screen → **Environment self-check** prints the ABI, binary path and size,
executable permission, the actual TMPDIR value, and the result of `iperf3 --version`.
Run it once after installing; it surfaces SELinux denials, ABI mismatches and unwritable
temp directories up front.

## Features

- **Quick-command grid on the home screen**: one card per quick command, showing its name and the full command (`iperf3 -c 192.168.5.200`). Move focus with the d-pad, run with OK.
- **"Configure" entry in the top right**: opens the configuration screen to add, edit and delete quick commands.
- **Common argument templates**: the edit dialog offers built-in templates (TCP default, 10 seconds, JSON output, reverse, UDP 100M, this device as server) so you can fill them in with one tap instead of typing with the remote.
- **Live output**: the run screen scrolls iperf3's line-by-line output with timestamps, and "Stop" in the top right interrupts it. The live rate bar at the top refreshes on every interval.
- **Smart hints**: for common failures (cannot reach the server, port in use, permission denied, ...) the app shows a concrete troubleshooting suggestion.
- **Environment self-check**: one tap on the configuration screen verifies that iperf3 can execute; run it once after installing on the line.
- **Bundled iperf3 binaries**: `assets` ships statically compiled iperf3 (v3.9) for arm64-v8a / armeabi-v7a / x86_64 / x86. On first run it is unpacked into the app's private directory and executed, with **no root required**.
- **TV artwork**: `tv_banner` ships a landscape banner at five densities from 160×90 to 640×360; the launcher icon comes in square and round variants, and Android 8.0+ automatically uses the adaptive icon (separate foreground/background, theme-icon tinting supported).

## Project layout

```
iperf-droid/
├── settings.gradle / build.gradle / gradle.properties
├── build.sh                 # one-shot build script
├── gradlew / gradlew.bat    # Gradle wrapper (8.7)
├── gradle/wrapper/
├── LICENSE                  # MIT
├── THIRD_PARTY_NOTICES.md   # third-party license notices
└── app/
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/iperf3/<abi>/iperf3     # prebuilt binaries
        │   ├── java/com/example/iperfdroid/
        │   │   ├── MainActivity.kt     # home grid
        │   │   ├── ConfigActivity.kt   # configuration screen
        │   │   ├── RunActivity.kt      # run and output
        │   │   ├── Shortcut.kt         # data model + local storage
        │   │   ├── IperfRunner.kt      # unpack binaries + run the child process
        │   │   ├── CommandBuilder.kt   # command assembly (incl. the realtime-output switch)
        │   │   ├── ArgsParser.kt       # command-line argument splitting
        │   │   ├── IntervalParser.kt   # live rate parsing
        │   │   ├── ResultParser.kt     # JSON summary + failure diagnosis
        │   │   └── SelfCheck.kt        # environment self-check
        │   └── res/...
        └── test/java/com/example/iperfdroid/   # unit tests
```

## Building

Requires JDK 17 and the Android SDK (compileSdk 34, build-tools 34.0.0). The repo ships a
Gradle wrapper, so a separate Gradle install is not needed.

```bash
# Option 1: the bundled script (auto-detects JAVA_HOME / ANDROID_HOME)
./build.sh

# Option 2: by hand
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug      # artifact: app/build/outputs/apk/debug/app-debug.apk
```

Artifact: `app/build/outputs/apk/debug/app-debug.apk`; install it on the TV with `adb install -r`.

### Running unit tests

```bash
./gradlew testDebugUnitTest
```

The report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

## Usage

1. Open "iperf-droid" on the TV.
2. Top right "Configure" → "Add quick command" → enter a name (e.g. "Default server") and the arguments (e.g. `-c 192.168.5.200`) → OK.
3. Go back to the home screen and tap the card to start a test; the run screen shows output live.

### Arguments

The configuration holds iperf3 arguments (without the `iperf3` binary itself). Common ones:

| Argument           | Meaning                                                               |
| ------------------ | --------------------------------------------------------------------- |
| `-c 192.168.5.200` | Connect to the given server as a client (most common)                 |
| `-t 10`            | Run for 10 seconds                                                    |
| `-J`               | Emit JSON for programmatic parsing                                    |
| `-R`               | Reverse mode (server sends to the TV)                                 |
| `-u -b 100M`       | UDP mode capped at 100M                                               |
| `-s`               | Act as a server on this device (a separate machine drives the client) |
| `-p 5201`          | Use the given port                                                    |

### Notes

- The iperf3 binaries come from `ninjab3s/ogt_androidue_utilities` (iperf3 v3.9, statically linked, MIT). To update, rebuild with the NDK from `ciniaogui/android-iperf3` and replace `assets/iperf3/<abi>/iperf3`.
- Android 10+ allows an app to execute a binary in its own private directory, but `execve` needs the file to be executable (the app handles this with `setExecutable`).
- If the TV is below Android 9, or its CPU ABI is not in the supported list, the bundled arm64 build may still work as a fallback.

### Regenerating the icons

The icons are drawn programmatically with no external assets. To adjust colours or layout:

```bash
pip install pillow
python tools/gen_icons.py
```

The script rewrites `app/src/main/res/mipmap-*/` and `app/src/main/res/drawable-*/tv_banner.png`,
and writes previews to `app/preview/`. Theme colours must stay in sync with
`app/src/main/res/values/colors.xml`.

## Language

All UI strings live in Android string resources under `app/src/main/res/values/strings.xml`
and are English only.

## License

MIT — see [LICENSE](LICENSE).

The repository bundles prebuilt iperf3 binaries (upstream [ESnet/iperf](https://github.com/esnet/iperf),
also MIT). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the full notices.

## Acknowledgements

- [ESnet/iperf](https://github.com/esnet/iperf) — the upstream iperf3 project
- [ninjab3s/ogt_androidue_utilities](https://github.com/ninjab3s/ogt_androidue_utilities) — statically compiled iperf3 binaries for Android
- [ciniaogui/android-iperf3](https://github.com/ciniaogui/android-iperf3) — a reference for cross-compiling iperf3 with the Android NDK
