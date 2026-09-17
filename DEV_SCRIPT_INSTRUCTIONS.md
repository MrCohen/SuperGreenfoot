# The `dev` script: testing SuperGreenfoot changes without an installer

`dev` lives in the repository root (`supergreenfoot/dev`). It sets `JAVA_HOME`
to Homebrew's JDK 21 for you, so no setup is needed beyond
`brew install openjdk@21`.

```sh
cd ~/Developer/SuperGreenfoot/supergreenfoot
./dev run                                         # IDE straight from source, about 30 s, tests skipped
./dev test                                        # engine test suite
./dev player super-scenarios/DisplayDemo --run    # a scenario in the standalone player
./dev app                                         # unsigned SuperGreenfoot.app, about 1 min, then opens it
./dev dmg                                         # signed and notarized installer onto the Desktop, about 8 min
./dev install                                     # dmg, then replace /Applications/SuperGreenfoot.app
./dev help                                        # this list
```

## Which one to use

| You changed... | Use | Why |
|---|---|---|
| IDE code (windows, menus, Share dialog, full-screen view) | `./dev run` | Fastest loop: compiles what changed and starts the IDE from the source tree. |
| Engine or player code (Actor, World, sound, `greenfoot.player`) | `./dev player <scenario folder>` | Compiles the scenario against the fresh runtime jar and runs it in the standalone player. Add `--run` to start running, `--fullscreen` to start full screen. |
| Anything, before committing | `./dev test` | Runs the headless engine tests. `./dev run` skips them to stay fast. |
| Something that only matters when packaged (export, bundled runtime, file locations) | `./dev app` | Builds the real app bundle, unsigned, for this Mac only. This is the check that would have caught the 2026-09-16 export bug. |
| You want to hand the build to someone, or update your own install | `./dev dmg` or `./dev install` | Signs with your Developer ID, notarizes, staples, and copies the DMG to the Desktop. `install` also replaces the app in /Applications. |

## Good to know

- `./dev install` refuses to replace the app while SuperGreenfoot is running. Quit it and run the command again.
- `./dev dmg` and `./dev install` include uncommitted changes. The build stamp then ends in `-dirty`. To see what an installed copy was built from:
  `cat /Applications/SuperGreenfoot.app/Contents/app/supergreenfoot-build.txt`
- The IDE started by `./dev run` or `./dev app` shares preferences and the debug log with the installed copy:
  `~/Library/Preferences/org.supergreenfoot/greenfoot-debuglog.txt`. When something fails silently, look there first.
- A window opened by `./dev run`, `./dev player` or `./dev app` takes keyboard focus, like any newly launched app.
- If a double-clicked exported `.jar` bounces in the Dock and then does nothing, quit `JavaLauncher` in Activity Monitor and try again (see `docs/api/phase4-native-packaging.md`).

## Overrides (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `SGF_JAVA_HOME` | `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` | A full JDK 21 or later (needs `jlink` and `jpackage` for `app`, `dmg`, `install`). |
| `SGF_SIGN_NAME` | `Jordan Cohen (QTS5DK2S8Q)` | The name part of the "Developer ID Application" certificate. |
| `SGF_NOTARY_PROFILE` | `SuperGreenfoot` | The keychain profile created with `xcrun notarytool store-credentials`. |
