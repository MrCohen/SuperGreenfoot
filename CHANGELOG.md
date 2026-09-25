# Changelog

Super Greenfoot is a fork of Greenfoot 3.9.0. Every existing Greenfoot
scenario keeps working unchanged; that is a rule of the project. The API
notes are in [docs/api](docs/api), and [docs/provenance.md](docs/provenance.md)
records every upstream file this fork changes, with what changed and in which
release, as the notice of modification the GPL asks for.

## Unreleased

### Fixes

- **Sound loops survive pause/resume.** `Sounds.resumeAll()` preserves looping
  music and effects instead of playing their remaining audio just once.
- **More robust local saves.** Malformed properties no longer crash a scenario;
  scores remain available during the current run if their directory cannot be
  written. Save files use the existing temporary-file replacement helper, and
  player names containing carriage returns no longer split score records.
- **Exported-game UserInfo matches its API.** Updating a saved player preserves
  the new values, strings containing carriage returns round-trip correctly,
  and `getNearby` handles unlimited requests and the end of a leaderboard.
  API documentation now describes local storage in exported games.
- **Tab reaches the scenario in an exported game.** The standalone player
  used Tab and Shift+Tab to move focus, so `Greenfoot.isKeyDown("tab")` and
  `Greenfoot.getKey()` never saw them there. (The IDE was not affected.)

## 0.2.0 (2026-09-25)

Everything since 0.1.1, the last public release. Versions 0.1.2 to 0.1.4 were
internal steps and are folded in here.

### A second IDE, beside the classic one

- **The Super Greenfoot IDE.** A new window for the same project, with a
  light and dark look: the class diagram as folders (drag classes into
  folders; folders can show inheritance inside them), editors docked as tabs
  beside the World tab (tear one off with Shift+Cmd+D, dock them all again),
  an Output panel, a Problems list that jumps to the line, and an actor
  Inspector with the method buttons. Switch between the two IDEs at any time
  from the Tools menu, while a scenario runs; the classic IDE stays the
  default. Folders are kept in a small `supergreenfoot.properties` file next
  to the scenario, which the original Greenfoot ignores.
- **Import Class, rebuilt.** The templates are sorted into categories, the
  preview shows the real class instead of a blank pane, and each class is
  compiled and checked by the test suite. New `TextBox` and `StatBar`
  templates; `Counter` draws itself in code (no more `Counter.png`).
  Removed: `SmoothMover` (actors move smoothly by themselves now, see
  0.1.0), `Animal` and `Label` (use `TextBox`). `TextBox`, `StatBar` and
  `Counter` use Super Greenfoot calls, so they need Super Greenfoot.
- **A class that overrides a built-in one says so.** A scenario class named
  like a built-in (`SuperWindow`, `Color`...) still loads and runs, and its
  box carries a badge explaining that it hides the built-in. New Class,
  Rename and Duplicate refuse every built-in name, and never overwrite an
  existing class's source (that could happen in Greenfoot 3.9.0 too).
- **Editor fixes** in both IDEs: the first line is no longer half hidden
  under the toolbar, scope colours repaint when they change, and the Import
  Class library is present when running from source.
- **Keys stopped sticking** after a world was reset in a reused window (the
  world had lost focus without saying so).

### Windows inside the world: `SuperWindow`

A window is an actor that holds other actors: a title bar, close and
minimise buttons, dragging, layers (normal, modal, always on top), scrolling
with the mouse wheel and a scroll bar, a picture-frame form, and a
bounded mode that keeps its actors inside. Actors in a window act, collide
and get mouse events as usual, in the window's own coordinates; actors
inside can ask `getParentWindow()`. New in 0.2.0: `setSize(width, height)`.
See the SuperWindowDemo scenario and [docs/api/superwindow.md](docs/api/superwindow.md).

### Networking

- **`Network`, `NetServer`, `NetClient`, `NetEvent`.** One copy of a
  scenario can be a server; others connect to it and exchange text
  messages. Everything is polled from your act method and nothing ever
  calls your code from another thread, so a networked scenario stays as
  single-threaded as any other. Messages arrive whole and in order; each
  connection reports one `CONNECTED`, its messages, then one
  `DISCONNECTED` with the reason in words. The wire protocol is WebSocket,
  so browsers will be able to join later.
- **Dedicated servers.** An exported game runs as a server with no window:
  `java -jar game.jar --server`, settings from `server.properties`, commands
  typed at its console, and a clean stop on Ctrl-C.
- **Hardened before shipping.** Slow handshakes, connections that never
  read, floods, and reconnect storms are all bounded and dropped with a
  reason; a paused host keeps its players; `startServer(port, max, true)`
  makes a server only this computer can reach. Details, limits and what a
  server exposes on a network are in [docs/api/network.md](docs/api/network.md).

### Engine

- **Speed 50 really is 60 acts a second** now, on every platform. Scenarios
  that set speed 51 to work around the old drift can go back to 50.
- **Mouse:** `Greenfoot.mouseReleased(obj)` and
  `Greenfoot.isMouseButtonDown(button)`, with `MouseInfo.LEFT`, `MIDDLE`
  and `RIGHT` naming the buttons.
- **Cursor:** `Greenfoot.setCursorVisible(false)` hides the mouse cursor
  over the world while the scenario runs; `Greenfoot.setCursor(...)`
  replaces it with an image from the images folder or a picture drawn in
  code, drawn by the system with no lag.
- **Depth sorting by the feet:** `World.setZSortAnchor(ZSortAnchor.BOTTOM)`
  sorts y-sorted actors by the bottom of their image, and
  `Actor.setZSortOffset(pixels)` adjusts one actor's ground line.
- **Text and images:** `GreenfootImage.getTextWidth(text, size)` and
  `getTextHeight(text, size)` give the size the text-image constructor would
  make, without making it; `GreenfootImage.tint(color, amount)` moves a
  picture's colours towards a colour while keeping its shape.
- **Faster:** collision queries, the frame transfer to the IDE and the image
  cache were all sped up, and fonts load at start-up rather than at the
  first `drawString`.
- **Runs nobody watches:** an exported game can run headless
  (`--headless N`) or with drawing off (`--no-render`), for tests and
  servers.

### Files and saving

- **Scenario files hold your settings, not the IDE's bookkeeping.**
  `project.greenfoot` shrinks to the scenario's own settings; class-diagram
  layout lives in the IDE's own files, and the `.ctxt` files are gone. The
  original Greenfoot opens these scenarios fine.
- **Saves that never leave a file half written.** Project, layout and
  settings files are written whole or not at all, with a backup of a
  settings file that could not be read, on every operating system.

### Mac

- One Super Greenfoot in the Dock and the app switcher, not two, and dialogs
  from your code come to the front ready to type (0.1.2).
- A new About box and splash screen, and "Super Greenfoot" in the app menu.

### API names settled in 0.2.0

These were only ever in the unreleased 0.1.2 to 0.1.4 builds:
`Actor.getWindow()` is `getParentWindow()`; `ZSortAnchor.CENTRE` is
`CENTER`; `Actor.setSortOffset`/`getSortOffset` are
`setZSortOffset`/`getZSortOffset`.

### Known limits

- Windows and Linux installers are still to come; running from source works
  there (see the README). The Windows build of 0.2.0 has not been tested yet.
- Web export, the online gallery and online saves are planned, not shipped.

## 0.1.1 (2026-09-18)

- Leaving full screen while a scenario ran could hang the app; it leaves
  cleanly now, by every route.
- Full screen opens on the screen the window is on, and returns the window
  to where it was.
- New app icon; tidier installer window.

## 0.1.0 (2026-09-17)

The first release: precise movement and rotation, per-actor depth and
y-sorting, text metrics, the sound engine (many copies of one sound, volume,
pan, categories), full-screen play and the display API, export as a runnable
jar or a signed Mac app, and the `Save` API. See the
[0.1.0 release notes](https://github.com/MrCohen/SuperGreenfoot/releases/tag/v0.1.0).
