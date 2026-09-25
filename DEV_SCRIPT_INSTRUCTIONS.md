# The `dev` script: testing SuperGreenfoot changes without an installer

`dev` lives in the repository root (`supergreenfoot/dev`). It sets `JAVA_HOME`
to Homebrew's JDK 21 for you, so no setup is needed beyond
`brew install openjdk@21`.

```sh
cd ~/Developer/SuperGreenfoot/supergreenfoot
./dev run                                         # IDE straight from source, about 30 s, tests skipped
./dev test                                        # engine and IDE test suites (about 1 min)
./dev player super-scenarios/DisplayDemo --run    # a scenario in the standalone player
./dev app                                         # unsigned SuperGreenfoot.app, about 1 min, then opens it
./dev dmg                                         # signed and notarized installer onto the Desktop, about 8 min
./dev install                                     # dmg, then replace /Applications/SuperGreenfoot.app
./dev winzip                                      # Windows standalone zip onto the Desktop, about 1 min
./dev usb /Volumes/USB\ DISK                      # the same, copied onto a USB key beside what is there
./dev help                                        # this list
```

## Which one to use

| You changed... | Use | Why |
|---|---|---|
| IDE code (windows, menus, Share dialog, full-screen view) | `./dev run` | Fastest loop: compiles what changed and starts the IDE from the source tree. |
| Engine or player code (Actor, World, sound, `greenfoot.player`) | `./dev player <scenario folder>` | Compiles the scenario against the fresh runtime jar and runs it in the standalone player. Add `--run` to start running, `--fullscreen` to start full screen. |
| Anything, before committing | `./dev test` | Runs the headless engine and IDE tests. `./dev run` skips them to stay fast. |
| Something that only matters when packaged (export, bundled runtime, file locations) | `./dev app` | Builds the real app bundle, unsigned, for this Mac only. This is the check that would have caught the 2026-09-16 export bug. |
| You want to hand the build to someone, or update your own install | `./dev dmg` or `./dev install` | Signs with your Developer ID, notarizes, staples, and copies the DMG to the Desktop. `install` also replaces the app in /Applications. |
| Windows PCs where you cannot install anything (school labs) | `./dev winzip` or `./dev usb <folder>` | Builds the Windows standalone on the Mac: `SuperGreenfoot.exe` plus a `SuperGreenfoot\` folder with its own Java. Unzip it anywhere writable, or run it from a USB key. See below. |

## Good to know

- `./dev install` refuses to replace the app while SuperGreenfoot is running. Quit it and run the command again.
- `./dev dmg` and `./dev install` include uncommitted changes. The build stamp then ends in `-dirty`. To see what an installed copy was built from:
  `cat /Applications/SuperGreenfoot.app/Contents/app/supergreenfoot-build.txt`
- The IDE started by `./dev run` or `./dev app` shares preferences and the debug log with the installed copy:
  `~/Library/Preferences/org.supergreenfoot/greenfoot-debuglog.txt`. When something fails silently, look there first.
- A window opened by `./dev run`, `./dev player` or `./dev app` takes keyboard focus, like any newly launched app.
- If a double-clicked exported `.jar` bounces in the Dock and then does nothing, quit `JavaLauncher` in Activity Monitor and try again (see `docs/api/phase4-native-packaging.md`).

## Windows standalone

`installer/windows/build-standalone.sh` is the Mac-side equivalent of upstream's `windows-standalone`
Ant target, which needs Windows. It needs `brew install mingw-w64` once (to compile upstream's
launchers with SuperGreenfoot's name) and downloads, the first time only, into
`~/Library/Caches/SuperGreenfoot/windows`: Temurin JDK 21 for Windows, the same Temurin release for
the Mac (only for its `jlink`, which must match the target's version exactly), and the Windows JavaFX
jars. `SGF_WIN_JDK_REFRESH=1` picks up a newer Temurin release.

The result, on a key or unzipped:

```
SuperGreenfoot.exe              start here: runs SuperGreenfoot\SuperGreenfoot.exe
SuperGreenfoot\
  SuperGreenfoot.exe            the normal launcher (also works on its own)
  Start SuperGreenfoot.cmd      fallback for PCs that block unknown .exe files
  jdk\  lib\
userhome\supergreenfoot\        preferences, created on first run
```

It sits beside a stock Greenfoot standalone (`greenfoot.exe`, `greenfoot\`, `userhome\greenfoot\`)
without sharing anything. `./dev usb` replaces only `SuperGreenfoot.exe` and `SuperGreenfoot\`.
The launchers are unsigned, so Windows SmartScreen may ask once ("More info", "Run anyway").

## Overrides (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `SGF_JAVA_HOME` | `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` | A full JDK 21 or later (needs `jlink` and `jpackage` for `app`, `dmg`, `install`). |
| `SGF_SIGN_NAME` | `Jordan Cohen (QTS5DK2S8Q)` | The name part of the "Developer ID Application" certificate. |
| `SGF_NOTARY_PROFILE` | `SuperGreenfoot` | The keychain profile created with `xcrun notarytool store-credentials`. |
| `SGF_WIN_CACHE` | `~/Library/Caches/SuperGreenfoot/windows` | Where `winzip` and `usb` keep the downloaded Windows JDK and JavaFX. |
