# Phase 2d: display API for scenario code

Games can drive their own presentation: ask the player how they want to play,
pick a world size that fills the screen, go full screen, choose crisp or
smooth scaling, and hide or lock the run controls. Everything works the same
in the IDE and in an exported game. Demo: `super-scenarios/DisplayDemo`.

## API (`greenfoot.Greenfoot`, plus the `greenfoot.ScaleMode` enum)

| Method | Meaning |
|---|---|
| `getScreenWidth()`, `getScreenHeight()` | Logical size of the screen the scenario is on (Retina: the scaled size). 0 if unknown. Call it before constructing a world to choose a size that fills the screen at a whole-number scale. |
| `setFullScreen(boolean)`, `isFullScreen()` | Enter or leave full screen. In the IDE this opens or closes the full-screen view. |
| `isFullScreenSupported()` | True in the IDE and the exported game; a future web host may say false. |
| `setScaleMode(ScaleMode)`, `getScaleMode()` | `PIXEL_PERFECT` (whole-number factor, nearest-neighbour) or `SMOOTH` (fit, interpolated). Remembered across entering and leaving full screen. |
| `getDisplayScale()` | The current world-to-screen factor (1.0 in a plain window, 3.0 for 640x360 pixel-perfect on 1080p, 2.25 smooth on 1440x900...). |
| `setControlsVisible(boolean)`, `isControlsVisible()` | Show or hide the run controls: the exported game's bar, or the IDE full-screen view's floating bar. |
| `setControlsLocked(boolean)`, `isControlsLocked()` | Lock the controls hidden (Escape does not reveal them; holding Escape for two seconds still does, for teachers). |
| `setWindowScale(double)`, `getWindowScale()` | Exported game only: show the world enlarged in the window (0.25 to 8). No effect in the IDE. |
| `isStandalone()` | True in the exported game. |

Players can always leave full screen with Shortcut+Shift+F.

## How it works

`greenfoot.platforms.DisplayDelegate` is the seam. The player's window
(`PlayerFrame`) implements it directly. In the IDE the scenario runs in the
debug VM, so `platforms/ide/DisplayDelegateIDE` relays through the existing
shared-memory channel (`greenfoot.vmcomm`):

- **Requests** (debug VM to IDE) are two integers appended to the debug VM's
  status block after the "VM ready" flag: a sequence number and a flags word
  whose low bits carry the requested values (full screen, controls visible,
  controls locked, pixel-perfect) and whose next four bits say which of them
  the scenario actually set. So `setFullScreen(true)` does not re-apply a
  controls setting the user changed by hand. `VMCommsMain` hands a new request
  to `GreenfootStage.receivedDisplayRequest`, which applies it.
- **State** (IDE to debug VM) is `Command.COMMAND_DISPLAY_STATE` carrying
  `DisplayState.LENGTH` integers: the four flags, "supported", screen width and
  height, the display scale in thousandths, and the sequence number of the last
  request applied. The stage sends it before the first world is instantiated
  (so a world constructor can already read the screen size), when the VM
  becomes ready, whenever the full-screen view opens, closes or its bar is
  changed by the user, and after every request. Once the debug VM sees its
  latest request echoed back, it clears the request mask.
- The stage keeps the full-screen preferences (pixel-perfect, controls
  visible, locked) while no view is open, so a scenario can set them before
  entering full screen and they survive leaving and re-entering.

Encoding helpers and the flag arithmetic live in `greenfoot.vmcomm.DisplayState`
and are unit-tested (`DisplayApiTest`), together with the routing from the
`Greenfoot` methods to the delegate and the safe answers of the null delegate.

## Player notes

`Greenfoot.setWindowScale(2)` makes a 640x360 world show in a 1280x720 window;
the scale mode decides between nearest-neighbour and bilinear drawing. Mouse
coordinates are mapped back through the scale. The screen size comes from the
window's graphics configuration (logical pixels), refreshed when the window
moves between screens.

## Demo

`super-scenarios/DisplayDemo`: `MenuWorld` shows where it runs and the screen
size, and for 640x360, 960x540 and 1280x720 says whether that world fills the
screen at a whole-number scale. Keys 1, 2, 3 build that `GameWorld`; F, P, C,
L, W toggle full screen, scale mode, controls, lock and window scale; M goes
back. The `Readout` bar shows the live values of every getter.
