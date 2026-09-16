# Phase 3: standalone player, desktop export, saving

## What "core extraction" became (decision D12)

The plan called for physically moving the engine into a `supergreenfoot-core`
module. Doing that now would make every upstream merge a rename-fest, so the
boundary is enforced differently and just as strictly:

- The **runtime jar** (`supergreenfoot-runtime.jar`, built by the Gradle task
  `superGreenfootRuntimeJar`, about 3.7 MB) contains the engine packages of the
  `greenfoot` module minus the IDE-only ones (`guifx`, `vmcomm`, `export.mygame`,
  `importer`, `record`, `localdebugger`, `platforms.ide`, `ProjectManager`,
  `GreenfootMain`, the sound recorder), the new `greenfoot.player` package, the
  BlueJ classes (only the ones actually executed get loaded), the JLayer MP3
  decoder, and a `greenfoot.png` default image.
- **No JavaFX anywhere on the player's path.** The engine's last JavaFX ties
  (key codes in `KeyboardManager`, mouse buttons in `MousePollingManager`, the
  old JavaFX standalone delegate and viewer) were removed. The key-name
  translation from JavaFX lives in `greenfoot.vmcomm.FXKeyNames` (IDE side);
  the player has `greenfoot.player.AwtKeyNames`. Mouse buttons are plain ints
  (1 left, 2 middle, 3 right, 0 none) everywhere.
- Proof: `java -cp supergreenfoot-runtime.jar greenfoot.player.PlayerMain <scenario> --headless 30`
  runs a scenario on a bare JDK 21 (verified), and a hand-built exported jar
  runs with `java -jar`.
- `bluej.Config` is never touched by the player (its class initialiser needs
  JavaFX); the one engine path that reached it, the sound-device preference,
  is switched off in the player.

## Standalone player (`greenfoot.player`)

| Class | Role |
|---|---|
| `PlayerMain` | Entry point. `java -jar Game.jar` (exported) or `PlayerMain <scenarioDir>` (development, compiled classes in the folder, `+libs/*.jar` honoured). Flags: `--fullscreen`, `--run`, `--headless N`. |
| `PlayerSession` | Toolkit-free engine wiring: delegates, Simulation, WorldHandler, sound listeners; `start`, `act`, `runPause`, `reset`, `setSpeed`, `shutdown`. Worlds are constructed on the simulation thread via `Simulation.runLater`, as in the IDE. Testable headless (`PlayerSessionTest`). |
| `WorldHandlerDelegatePlayer` | Renders frames on the simulation thread into recycled BufferedImages (cap 120 fps); the UI polls `takeFrame()`. |
| `GreenfootUtilDelegatePlayer` | Resources from the scenario class loader; `UserInfo` stored in `userinfo.txt` in the save folder (so the old API keeps working offline); player name = OS login name unless `player.name` is set. |
| `PlayerFrame` | Swing window: world panel, control bar (Act / Run-Pause / Reset / speed / Full Screen), exclusive full-screen mode with a floating draggable bar (Pixel-perfect, Hide, Lock, Exit), Esc toggles, hold Esc 2 s recovers, Shortcut+Shift+F toggles full screen. Implements `DisplayDelegate`. |
| `AwtKeyNames` | AWT key events to Greenfoot key names, matching the IDE. |

Save location for exported games: `~/Library/Application Support/SuperGreenfoot/<name>`
(macOS), `%APPDATA%\SuperGreenfoot\<name>` (Windows), `$XDG_DATA_HOME/SuperGreenfoot/<name>`
(Linux). For `PlayerMain <scenarioDir>` it is `<scenarioDir>/saves`, the same
folder the IDE uses.

## Scenario-side presentation API (`Greenfoot`)

| Method | In the player | In the IDE |
|---|---|---|
| `Greenfoot.setFullScreen(boolean)` / `isFullScreen()` | switches the window | no-op (use the Controls menu) |
| `Greenfoot.setControlsVisible(boolean)` | shows/hides the bar | no-op |
| `Greenfoot.setControlsLocked(boolean)` | locks it hidden (hold Esc 2 s to recover) | no-op |
| `Greenfoot.isStandalone()` | true | false |

Implemented through `greenfoot.platforms.DisplayDelegate`; the IDE keeps the
no-op `DisplayDelegate.NONE`.

## Save API (`greenfoot.Save`)

Unlimited named values plus local high-score tables, auto-flushed within half
a second and at shutdown. `putInt/getInt`, `putDouble/getDouble`,
`putBoolean/getBoolean`, `putString/getString`, `contains`, `remove`, `clear`,
`getKeys`, `flush`; `submitScore(table, score)` (current player) or
`submitScore(table, player, score)`, `getTopScores(table, n)`,
`getBestScore(table, player)`, `clearScores(table)`. In the IDE the files live
in the scenario's `saves/` folder. Without any save directory (tests) it
works in memory.

## Export to application (IDE)

Share dialog gains an **Application** tab (restored from Greenfoot 3.8.1 and
simplified): choose a `.jar` path, optionally lock the scenario or hide the
controls. The jar is the scenario (classes, images, sounds, `standalone.properties`)
plus the runtime jar merged in, `Main-Class: greenfoot.player.PlayerMain`.
Requires Java 21+ on the target machine; a `jpackage` bundle with a private
runtime is Phase 4 work alongside signing.

## Not done yet in Phase 3

- `jpackage` bundles (with signing, Phase 4).
- The IDE's "Application" tab has been wired but not clicked through by hand.
- World-size guidance in the New Scenario dialog (Phase 2c) still pending.
