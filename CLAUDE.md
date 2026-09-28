# SuperGreenfoot (fork repository)

This is a git clone of upstream BlueJ/Greenfoot at tag `GREENFOOT-RELEASE-3.9.0`
with SuperGreenfoot work on branch `super/main`. See `docs/provenance.md`
for the upstream base and every upstream file we change.

## Rules

- This repository is public. Planning documents (the master plan, decisions
  log, assessments, drafts) are private and live outside the repo. Never
  create, copy or commit them here.

- Upstream merges matter for the engine (the `greenfoot` runtime, debug-VM
  and player side) and for the Classic Greenfoot IDE, which stays close to
  upstream: keep diffs to those files small and isolated. The new
  SuperGreenfoot IDE, built beside Classic and switchable with it, is our own
  code and need not track upstream. Log every change to an upstream file in
  `docs/provenance.md`.
- IDE threading: methods that implement an interface on a JavaFX class (for
  example `GreenfootStage`) need their own `@OnThread(Tag.FXPlatform)`; the
  interface-level tag is not enough for the thread checker.
- Keep upstream copyright headers; new files get the same GPLv2+CPE header
  with "SuperGreenfoot contributors" added.
- `super-scenarios/MrCohenLibrary150/` must compile and run after every
  phase. Never edit it to make a test pass; fix the engine.
- New engine code is TeaVM-clean: no `java.awt` outside
  `greenfoot.backend.awt` (Phase 3 onward), no reflection, no new threads
  outside `Simulation`, the sound mixer and the network module
  (`greenfoot.net`, whose threads never call scenario code; the browser
  build replaces it with the browser's WebSocket).
- Public API changes: keep `getX()/getY()/getRotation()` returning `int`;
  add new methods rather than changing signatures; regenerate the user
  Javadoc (`./gradlew :greenfoot:userJavadoc`) when the nineteen API classes
  change: the nine upstream classes, `ScaleMode`, `ZSortAnchor`, `SuperWindow`,
  `Save`, `Sounds`, `SoundCategory`, and the network module's `Network`,
  `NetServer`, `NetClient` and `NetEvent`. The list lives in `userJavadoc`
  (`greenfoot/build.gradle`).
  Their APIs are in `docs/api/` (`superwindow.md`, `network.md`,
  `mouse-input.md`, `phase1-api.md` for depth and the sort anchor,
  `phase2d-display-api.md` for `ScaleMode` and the cursor).
- Commit messages end with a `Co-Authored-By:` line naming the Claude model
  that actually did the work in that session, for example
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :greenfoot:compileJava      # engine + IDE
./gradlew :greenfoot:test             # headless engine tests (278 pass on 2026-09-25)
./gradlew :bluej:test                 # BlueJ-side tests, incl. the headless editor (492 pass)
./gradlew runGreenfoot                # launch the IDE
```

The owner's shortcut for all of this is the `./dev` script in the repo root
(`./dev run`, `test`, `player <scenario>`, `app`, `dmg`, `install`); keep it
working when build steps change.

On Windows, use `gradlew.bat` from a Windows shell (PowerShell or `cmd`), with
a JDK 21 that has `jmods` and `jpackage` (Temurin "full"):

```bat
set JAVA_HOME=C:\Users\<you>\AppData\Local\Programs\Java\jdk-21.0.12.1+1
gradlew.bat :greenfoot:test
gradlew.bat runGreenfoot
gradlew.bat :greenfoot:packageSuperGreenfootWindows   :: unsigned MSI
```

Build through Windows even when your shell is WSL — a Linux JDK would pull in
Linux JavaFX natives. `installer\windows\build-msi.ps1` is the Windows
equivalent of `installer/mac/build-dmg.sh`, and `dev.ps1` is the Windows
equivalent of `./dev`. Building an MSI needs WiX 3.14 (JDK 21's jpackage
cannot use WiX 4 or 5); without it the script still produces an app folder.
The IDE's preferences live in `%USERPROFILE%\supergreenfoot` on Windows (from
`Config.getBlueJPrefDirName`), not in `%APPDATA%`; pass `-bluej.userHome=<dir>`
to test against a scratch copy.

Windows timers: act pacing (`HDTimer`) must stay on `Thread.sleep`. Measured on
Windows 11 with JDK 21, `LockSupport.parkNanos` and `-XX:+ForceTimeHighResolution`
both drop the pacer onto the 15.6 ms system tick (see the comment in `HDTimer`).
Gradle's test worker sometimes gets that coarse tick too, which is why
`SimulationPacingTest` measures the timer before judging the median.

Gradle 8.5 wrapper, JDK 21, JavaFX 21.0.12 via the openjfx plugin. The
thread-checker annotation processor (`@OnThread`) runs during compile and
fails the build on thread-tag violations.

## Where things are

| Concern | Path |
|---|---|
| Public API (nine upstream classes + `SuperWindow` + `Network`, `NetServer`, `NetClient`, `NetEvent`) | `greenfoot/src/main/java/greenfoot/*.java` |
| Network module (WebSocket over `java.net`, its threads) | `greenfoot/src/main/java/greenfoot/net/` |
| Act loop / speed | `greenfoot/src/main/java/greenfoot/core/Simulation.java` |
| Paint order sets | `greenfoot/src/main/java/greenfoot/{TreeActorSet,ActorSet}.java` |
| Renderer | `greenfoot/src/main/java/greenfoot/gui/WorldRenderer.java` |
| Collision (BSP) | `greenfoot/src/main/java/greenfoot/collision/` |
| Sound | `greenfoot/src/main/java/greenfoot/sound/` |
| Debug-VM to IDE frames | `greenfoot/src/main/java/greenfoot/vmcomm/` |
| IDE window / world view | `greenfoot/src/main/java/greenfoot/guifx/{GreenfootStage,WorldDisplay,ControlPanel}.java` |
| Export | `greenfoot/src/main/java/greenfoot/export/`, `guifx/export/` |
| Headless tests | `greenfoot/src/test/java/greenfoot/` (`WorldCreator`, `TestUtilDelegate`); `NetworkTest` and `net/NetworkAbuseTest` run real sockets on loopback |
| Regression scenario | `super-scenarios/MrCohenLibrary150/` |
| What the IDE writes into a scenario | `project.greenfoot` (settings only, no class layout: `bluej/pkgmgr/Package.java`), `supergreenfoot.properties` (folders, folds, panels: `guifx/superide/folders/ProjectSettingsFile.java`); no `.ctxt` files (`bluej/views/View.java` reads the cached parse). Window geometry is per user in `scenario-layouts.properties` (`bluej/pkgmgr/ProjectLayoutStore.java`). |
| Window demo | `super-scenarios/SuperWindowDemo/` |
