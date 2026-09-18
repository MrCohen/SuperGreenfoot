# Provenance

## Upstream base

- Repository: https://github.com/k-pet-group/BlueJ-Greenfoot
- Tag: `GREENFOOT-RELEASE-3.9.0`
- Commit: `6c1390e06cb84a243fbba3f7b7a4531bbff24a34` ("Merge pull request #2408 from neilccbrown/main")
- Cloned 2026-09-16 with full history; SuperGreenfoot work is on branch `super/main`.
- Upstream BlueJ version at this tag: 5.4.1. Greenfoot API version: 3.1.0.

## Local reference snapshot

The workspace folder `../BlueJ-Greenfoot-GREENFOOT-RELEASE-3.9.0/` is a
read-only unpacked copy of the same release (file dates 2024-10-14).

- Files (excluding `.DS_Store`): 2107
- Aggregate SHA-256 (sorted per-file `shasum -a 256` list, hashed again):
  `ae79aa6ac780d59023c8b8de09407454caa9115829c927fd082c7fc302004385`
- Verified 2026-09-16: `diff -rq --exclude=.git --exclude=.DS_Store` between
  the snapshot and this clone at the tag reports no differences.

Recompute with:

```sh
cd ../BlueJ-Greenfoot-GREENFOOT-RELEASE-3.9.0 && \
find . -type f ! -name .DS_Store -print0 | sort -z | xargs -0 shasum -a 256 | shasum -a 256
```

## Reference scenario

`super-scenarios/MrCohenLibrary150/` is a source-only copy (no `.class`,
`.ctxt`, or Dropbox "conflicted copy" files) of the workspace folder
`../MrCohenLibrary150/` (Mr Cohen's Library of Stuff, Nov 2021 README,
classes dated up to June 2026). It is the compatibility regression scenario:
every phase must leave it compiling and running.

## Changes to upstream files on `super/main`

Tracked here so upstream merges stay reviewable.

| File | Change | Phase |
|---|---|---|
| `.github/workflows/build-installers.yml` | Trigger changed to `workflow_dispatch` only, so pushes to the fork do not launch three-OS installer builds. | 0 |
| `bluej/src/main/java/bluej/Config.java` | Preferences directory name `greenfoot` -> `supergreenfoot` (`getBlueJPrefDirName`). | 1 |
| `greenfoot/src/main/java/greenfoot/Actor.java` | Precise `preciseX/Y/Rotation`, image-rotation lock, `z`; int API unchanged and routed through the shared `setLocationImpl`/`setRotationImpl`. Bounds and collision fast paths use `imageRotationInt`. | 1 |
| `greenfoot/src/main/java/greenfoot/ActorVisitor.java` | Accessors for precise fields, image rotation and z. | 1 |
| `greenfoot/src/main/java/greenfoot/World.java` | z/y-sort/global-z/smooth-rendering flags and `getObjectsInFinalPaintOrder()`. | 1 |
| `greenfoot/src/main/java/greenfoot/WorldVisitor.java` | `getObjectsInFinalPaintOrder`, `isSmoothRendering`. | 1 |
| `greenfoot/src/main/java/greenfoot/TreeActorSet.java` | Package accessor `getSubSets()`. | 1 |
| `greenfoot/src/main/java/greenfoot/gui/WorldRenderer.java` | Iterates final paint order; smooth path draws at precise position/rotation. | 1 |
| `greenfoot/src/main/java/greenfoot/GreenfootImage.java` | `drawCenteredString`; padded-image cache and `drawImageSmooth` for sub-pixel drawing; `modCount` invalidation. | 1 |
| `greenfoot/src/main/java/greenfoot/ImageVisitor.java` | `drawImageSmooth`. | 1 |
| `greenfoot/src/main/java/greenfoot/Font.java` | `getStringWidth/Height`, `getAscent/Descent/LineHeight`, package helpers for centring. | 1 |
| `greenfoot/src/test/java/greenfoot/{PrecisionTest,ZOrderTest,SmoothRenderTest,FontMetricsTest}.java` | New tests (28). | 1 |
| `greenfoot/src/main/java/greenfoot/GreenfootSound.java` | Rewritten over the mixer (same API); MIDI via legacy player. | 2 |
| `greenfoot/src/main/java/greenfoot/Greenfoot.java` | `playSound` routes through `Sounds`. | 2 |
| `greenfoot/src/main/java/greenfoot/core/GreenfootMain.java` | Registers the mixer's simulation listener. | 2 |
| `greenfoot/src/main/java/greenfoot/{Sounds,SoundCategory}.java`, `greenfoot/sound/{AudioDecoder,PcmClip,SampleSource,StreamSource,Voice,SoundMixer,SoundLibrary}.java` | New sound engine. | 2 |
| `greenfoot/src/main/java/greenfoot/guifx/GreenfootStage.java` | Key/mouse forwarding extracted into `forwardWorldKeyEvent`/`forwardWorldMouseEvent`; full-screen view wiring (menu item, image mirroring, state/speed sync, exit on ask/close/VM restart). | 2 |
| `greenfoot/src/main/java/greenfoot/guifx/FullScreenView.java` | New full-screen play window with floating controls. | 2 |
| `greenfoot/labels/english/greenfoot/greenfoot-labels` | `controls.fullscreen`, `fullscreen.*` labels. | 2 |
| `greenfoot/src/test/java/greenfoot/{SoundsTest,sound/SoundMixerTest}.java` | New tests (26). | 2 |
| `greenfoot/src/main/java/greenfoot/gui/input/KeyboardManager.java` | JavaFX-free: `keyPressed/keyReleased/keyTyped(String name)`; key-code mapping moved to `vmcomm/FXKeyNames.java` (new). | 3 |
| `greenfoot/src/main/java/greenfoot/gui/input/mouse/MousePollingManager.java` | Mouse buttons are ints (1/2/3) instead of JavaFX MouseButton. | 3 |
| `greenfoot/src/main/java/greenfoot/vmcomm/VMCommsSimulation.java` | Uses FXKeyNames and converts MouseButton ordinals to ints. | 3 |
| `greenfoot/src/main/java/greenfoot/export/GreenfootScenarioViewer.java`, `platforms/standalone/WorldHandlerDelegateStandAlone.java` | Deleted (JavaFX standalone viewer; replaced by `greenfoot.player`). | 3 |
| `greenfoot/src/main/java/greenfoot/export/{Exporter,JarCreator}.java`, `guifx/export/{ExportDialog,ExportAppTab}.java` | APP export restored: Application tab, `makeApplication()` merges `supergreenfoot-runtime.jar`; jar Main-Class is `greenfoot.player.PlayerMain`. | 3 |
| `greenfoot/src/main/java/greenfoot/Greenfoot.java`, `util/GreenfootUtil.java`, `platforms/DisplayDelegate.java` (new), `platforms/GreenfootUtilDelegate.java`, `platforms/ide/GreenfootUtilDelegateIDE.java`, `core/ExportedProjectProperties.java` | Presentation API (`setFullScreen`, `setControlsVisible/Locked`, `isStandalone`), `getSaveDirectory()` hook (IDE: `saves/`), properties constructor. | 3 |
| `greenfoot/src/main/java/greenfoot/Save.java` (new), `greenfoot/player/*` (new) | Save API; standalone Swing player. | 3 |
| `greenfoot/src/main/java/greenfoot/util/GraphicsUtilities.java` | Headless fallbacks for compatible-image creation. | 3 |
| `greenfoot/src/main/java/greenfoot/sound/SoundMixer.java` | `setUseDevicePreference(false)` for the player (avoids bluej.Config). | 3 |
| `greenfoot/build.gradle` | `superGreenfootRuntimeJar` task (runtime jar into lib/). | 3 |
| `greenfoot/labels/english/greenfoot/greenfoot-labels` | `export.app.runHint`, `export.app.title`. | 3 |
| `greenfoot/src/test/java/greenfoot/mouse/MousePollTest.java` | Int buttons. New tests: `SaveTest`, `player/PlayerSessionTest`, `player/AwtKeyNamesTest`. | 3 |
| `greenfoot/src/main/java/greenfoot/export/NativePackager.java` (new), `export/Exporter.java`, `export/mygame/ExportInfo.java`, `guifx/export/ExportAppTab.java` | jpackage native packaging with macOS signing/notarization from the Application tab. | 4 |
| `greenfoot/src/main/java/greenfoot/guifx/FullScreenView.java` | Scale readout label. | 2c |
| `greenfoot/labels/english/greenfoot/templates/worldJava.tmpl` | World-size tip comment. | 2c |
| `greenfoot/labels/english/greenfoot/greenfoot-labels` | `export.app.native*`, `export.app.sign*`, `export.app.notarize`. | 4 |
| `greenfoot/src/test/java/greenfoot/export/NativePackagerTest.java` | New tests (4). | 4 |
| `installer/mac/{build-dmg.sh,entitlements.plist,assoc-*.properties}` (new), `greenfoot/build.gradle` (`packageSuperGreenfootMac`) | macOS IDE installer via jpackage; signing and notarization. | 4 |
| `greenfoot/src/main/java/greenfoot/GreenfootImage.java` | Javadoc link fix (`greenfoot.Font`), so `userJavadoc` builds. | 4 |
| `greenfoot/src/main/java/greenfoot/export/Exporter.java` | Runtime jar looked up in the BlueJ lib dir (`Contents/app` when installed) and a missing jar stops the export visibly; thread-tag annotations untangled (`findRuntimeJar` is `Tag.Any`, `makeApplication` `Tag.Worker`). | 4 |
| `greenfoot/src/main/java/greenfoot/export/NativePackager.java` | An earlier app image of the same name at the export destination is replaced (jpackage refuses to overwrite). | 4 |
| `installer/mac/build-dmg.sh`, `greenfoot/build.gradle` | Installer script runs the Gradle build itself (`--no-build` from the Gradle task), refuses stale jars, stamps the git revision in `supergreenfoot-build.txt`. | 4 |
| `greenfoot/src/main/java/greenfoot/ScaleMode.java` (new), `greenfoot/Greenfoot.java`, `platforms/DisplayDelegate.java` | Display API: screen size, full screen everywhere, scale mode, display scale, controls getters, window scale. | 2d |
| `greenfoot/src/main/java/greenfoot/vmcomm/{DisplayState (new),Command,VMCommsSimulation,VMCommsMain}.java`, `platforms/ide/DisplayDelegateIDE.java` (new), `core/GreenfootMain.java` | Display requests (debug VM to IDE, two status ints) and `COMMAND_DISPLAY_STATE` (IDE to debug VM). | 2d |
| `greenfoot/src/main/java/greenfoot/guifx/{GreenfootStage,FullScreenView}.java` | Apply display requests, remember full-screen preferences, report state; pixel-perfect and display-scale accessors. | 2d |
| `greenfoot/src/main/java/greenfoot/player/PlayerFrame.java` | New delegate methods; window scale; cached screen bounds. | 2d |
| `greenfoot/src/test/java/greenfoot/DisplayApiTest.java`, `greenfoot/build.gradle` (`userJavadoc` includes `ScaleMode`) | New tests (4). | 2d |
| `README.md` | Super Greenfoot front page (download, features, run from source, credits); the upstream README is kept in full inside a collapsed section. `.github/ISSUE_TEMPLATE/` and `docs/images/` are new. | release 0.1.0 |
| `boot/src/main/java/bluej/Boot.java`, `boot/build.gradle`, `version.properties` | `SUPER_VERSION` constant stamped from the new `supergreenfoot_version` property; shown in the About box. | release 0.1.0 |
| `greenfoot/src/main/java/greenfoot/guifx/{GreenfootStage,FullScreenView}.java` | Full screen opens on the display the main window is on (it always used the primary display); leaving it exits native full screen before closing the window (closing first deadlocked JavaFX on macOS); leaving full screen through macOS (green button) ends play mode; the Full Screen command brings an existing full-screen window forward instead of closing it. | fix after 0.1.0 |
| `greenfoot/src/main/java/greenfoot/player/PlayerFrame.java` | Full screen uses the display holding the window (the frame's `GraphicsConfiguration` could name the previous display); leaving it restores the window's position and size; the floating bar starts bottom-centre each time. | fix after 0.1.0 |
