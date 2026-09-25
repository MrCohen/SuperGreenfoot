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
| `setCursorVisible(boolean)`, `isCursorVisible()` | Hide the mouse cursor over the world while the scenario runs (a game that aims with the mouse and draws its own crosshair). It comes back while paused, when the mouse leaves the world, and in full screen while the controls are up (Escape). Forgotten on Reset. |
| `setCursor(String)`, `setCursor(String, int, int)` | Replace the cursor over the world with an image from the `images` folder, drawn by the system with no lag; the second form picks the hot spot (default: the image centre; a negative value also means the centre, one outside the image throws). `setCursor((String) null)` restores the normal cursor, as does Reset. Throws `IllegalArgumentException` if the file is missing, exactly as `new GreenfootImage(name)` would, because that is how it is loaded. |
| `setCursor(GreenfootImage)`, `setCursor(GreenfootImage, int, int)` (0.2.0) | The same with a picture drawn or loaded in code (a game whose art is all code, like the exemplar's crosshair). The pixels are copied at the call, so drawing on the picture afterwards changes nothing until the next call; a call with the same pixels and hot spot costs nothing. At most 256 pixels each way (throws). |

Players can always leave full screen with Shortcut+Shift+F.

Custom cursor images are drawn by the operating system, so they do not scale
with the world: a 32x32 crosshair stays 32x32 on a 3x pixel-perfect full
screen. Windows shows custom cursors at its own size (32x32) and scales other
sizes to fit, so keep cursor images about that size.

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
- **Cursor** (added in 0.2.0). `setCursorVisible` is a fifth flag on the same
  request word (`DisplayState.CURSOR_HIDDEN`; the mask bits moved up to bits
  8-15 to make room) and is echoed back in the state; `isCursorVisible()`
  answers with the value last asked for (`GreenfootUtil`), not with the echo,
  which lags a frame. `setCursor` is a separate request after the
  keyboard-return counter: a sequence number, the hot spot, the picture's
  width and height, and its ARGB pixels (0 by 0 means the normal cursor). Both
  forms send pixels: `setCursor(String)` is `setCursor(new GreenfootImage(name))`,
  so the file is found by the one rule every image follows and the IDE never
  looks files up. `GreenfootUtil.requestCursor` drops a request identical to
  the last one (pixels and hot spot), so asking every act costs nothing;
  `WorldHandler.discardWorld` forgets it, so a new world's constructor is
  heard. The IDE builds a JavaFX `WritableImage` from the pixels and an
  `ImageCursor` from that. The controller applies the result in `applyCursor()`: JavaFX
  cursors are per node, so the main window sets it on the `WorldDisplay` (the
  cursor is normal over the class diagram and menus) while the full-screen
  view sets it on its root, so the black margins hide it too. It is only
  hidden while the state is RUNNING, so actors can be dragged while paused,
  and the full-screen view shows it whenever its controls are visible. Both
  settings are cleared when the state goes to NO_WORLD (Reset, recompile), so
  a world constructor decides afresh. `Greenfoot.setWorld` from code keeps
  them.

Encoding helpers and the flag arithmetic live in `greenfoot.vmcomm.DisplayState`
and are unit-tested (`DisplayApiTest`), together with the routing from the
`Greenfoot` methods to the delegate and the safe answers of the null delegate.

## Player notes

Cursors in the player use AWT: `setCursorVisible(false)` is a 1x1 transparent
custom cursor on the world panel (made once; the control bar keeps its own),
and `setCursor` builds a `BufferedImage` from the pixels and goes through
`Toolkit.createCustomCursor`, which scales the image to the toolkit's best
cursor size, so the hot spot is scaled with it. Reset clears both, as in the
IDE.

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
back. H hides the cursor, X swaps in `images/crosshair.png` and D a ring
drawn in code (`setCursor(GreenfootImage, 12, 12)`). The `Readout` bar shows
the live values of every getter.
