# Phase 0 baseline (2026-09-16)

Machine: Apple Silicon Mac, macOS (Darwin 27), Homebrew openjdk@21 (21.0.11),
Gradle 8.5 wrapper, JavaFX 21.0.3 from Maven Central.

## Build

| Step | Result |
|---|---|
| `./gradlew :greenfoot:compileJava` | OK. Warnings only: `finalize()` deprecation in `bluej/debugger/jdi/JdiObject.java:151`, unchecked ops in `greenfoot/World.java`, and a harmless `--add-exports javafx.graphics` module warning. |
| `./gradlew :greenfoot:test` | OK. 41 tests, 0 failures: `ActorTest` (4), `MousePollTest` (15), `collision/*` (22). |
| `./gradlew runGreenfoot` | OK. IDE window appears in about 20 s after the build. Console shows "Unsupported JavaFX configuration: classes were loaded from 'unnamed module'" (upstream-known, harmless). |

## Launch and scenario open

- Launching with a scenario path as a trailing argument works:
  `java ... bluej.Boot -bluej.debug=true -greenfoot=true /path/to/scenario`
  (BlueJ `Main.processArgs` opens non-dash arguments).
- `super-scenarios/MrCohenLibrary150` opened and compiled from source with
  no errors: 27 `.java` files produced 34 `.class` files (nested/inner
  classes: `Platform`, `VerticalPlatform`, `ActorContent`, `Direction`,
  `GifImage$GifDecoder` and friends).
- Screenshot: `baseline/ide-mrcohenlibrary-2026-09-16.png`.

## Findings to carry forward

1. **Shared preferences with the installed Greenfoot.** The dev build reads
   and writes the same `~/.greenfoot/greenfoot.properties` (and last-opened
   project list) as `/Applications/Greenfoot.app` 3.9.0. Running both at
   once works, but the fork should get its own config directory name early
   in Phase 1 (`Config.java` line ~590 sets the program name) so it cannot
   disturb a teacher's or student's installed Greenfoot.
2. **Regression scenario is source-only.** `.class` files produced by the
   IDE are ignored by upstream's `.gitignore` (`*.class`), so the scenario
   stays clean in git. Greenfoot rewrites `project.greenfoot` on open
   (window positions, API version); commit those changes as they come.
3. **Upstream CI** (`build-and-run-tests.yml`) runs on push with Xvfb on
   Ubuntu; kept as-is. `build-installers.yml` now only runs on manual
   dispatch (logged in `provenance.md`).
4. **JDK on PATH is 25.** Always export `JAVA_HOME` to openjdk@21 before
   Gradle; JDK 25 fails the `--release 21` + JavaFX combination.

## Not yet done in Phase 0

- Behavioural screenshots of the `ants` bundled scenario (open it from the
  IDE: `greenfoot/scenarios/java/ants`).
- Windows build verification (no Windows machine on hand; CI covers compile).
