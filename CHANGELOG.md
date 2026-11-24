# Changelog

Super Greenfoot is a fork of Greenfoot 3.9.0. Every existing Greenfoot
scenario keeps working unchanged; that is a rule of the project. The API
notes are in [docs/api](docs/api), and [docs/provenance.md](docs/provenance.md)
records every upstream file this fork changes, with what changed and in which
release, as the notice of modification the GPL asks for.

## 0.2.3 (2026-11-24)

Fixes for Quit, for some MP3 files, and a new name for new scenarios'
project file.

### Fixes

- **Quit could stop working, so the app needed a force quit.** Opening a
  scenario that was already open (double-clicking its `project.greenfoot`
  in the Finder, Open With, or a scenario path on the command line) opened
  it in a second window. Closing both windows closed the scenario twice; the
  second close failed part-way, and from then on Quit, and closing the last
  window, did nothing. Opening an open scenario now brings its window to the
  front, and Quit always quits, even if closing one scenario goes wrong.
  Greenfoot 3.9.0 has the same fault.
- **Some MP3 sounds stopped their world from being created.** A
  `new GreenfootSound("....mp3")` threw `ArrayIndexOutOfBoundsException` (in
  `LayerIIIDecoder.huffman_decode`) for MP3 files that end with an ID3v1 tag
  whose last byte is 0xFF, although the files are valid. The MP3 reader asked
  the decoder for audio past the end of the file; it now stops there.
  Greenfoot 3.9.0 has the same fault but meets it only when the sound ends,
  out of sight; Super Greenfoot reads a sound's whole file when the sound is
  made, so the error reached the scenario.

### Changes

- **A new scenario's project file is `__project.greenfoot`**, so it sorts to
  the top of the scenario's folder in the Finder and Explorer.
  - Scenarios that have `project.greenfoot` keep it. Super Greenfoot reads
    and saves it under that name and never renames it, so they still open in
    Greenfoot 3.9.0.
  - Greenfoot 3.9.0 can't open a scenario whose only project file is
    `__project.greenfoot`; rename it to `project.greenfoot` first.
  - Renaming either way by hand is fine: Super Greenfoot opens both names
    alike. If a folder has both, `__project.greenfoot` is used.
  - Exported apps and gallery uploads still carry `project.greenfoot`, the
    name every player reads.

### Downloads

This release ships the macOS download only. The fixes are in the source for
Windows too; the Windows installer stays at 0.2.1 until the next Windows
build.

## 0.2.2 (2026-11-14)

A fix for the macOS download. **If you use Super Greenfoot on a Mac, please
update.**

### The important one

- **On most Macs, no scenario could run.** The IDE opened, but pressing Run
  (or even opening a scenario) printed `UnsatisfiedLinkError:
  .../libfontmanager.dylib ... libharfbuzz.0.dylib (no such file)` in the
  terminal window, and the world never appeared. The Java runtime inside the
  0.2.0 and 0.2.1 DMGs came from a Homebrew build of Java, which loads its
  font, JPEG, PNG and colour libraries from Homebrew's folders instead of
  carrying them inside. Only Macs that happen to have Homebrew's copies of
  those libraries could run scenarios. The 0.2.2 runtime is built from
  Eclipse Temurin, which carries every library it needs, and the installer
  script now refuses to build a runtime that links to anything outside
  itself and macOS.
- **Native Mac apps exported with 0.2.0 or 0.2.1** (Share > Application,
  "Also build a native app") copied that same runtime and fail the same way
  on other Macs. Export them again with 0.2.2.

### Also in 0.2.2

- The Windows standalone zip of 0.2.1 stopped at startup with
  `NoClassDefFoundError: atlantafx/base/theme/Theme`, because its launcher
  did not put the IDE's theme library on the class path. The fix is in the
  0.2.2 source; this release ships only the Mac download, so on Windows use
  the 0.2.1 installer (`.msi`), which was not affected.

## 0.2.1 (2026-10-26)

A bug-fix release, and the first one with Windows downloads.

### The important one

- **Exporting a native app on Windows or Linux could delete the scenario.**
  In 0.2.0, Share > Application with "Also build a native app" built the app
  in a folder with the same name as the scenario, beside it, and cleared that
  folder first, so on Windows and Linux it deleted the scenario's own files.
  macOS was never affected (its apps are called `<name>.app`). The app is now
  built in a separate folder called `<name> app`, and the export only ever
  clears a folder that it built itself. If you exported a native app on
  Windows or Linux with 0.2.0, check that your scenario folder is intact.

### Windows

- **Two downloads, both unsigned for now.** The **installer** (`.msi`)
  installs for the current user without administrator rights, adds a Start
  menu entry, opens `.greenfoot` and `.gfar` files, and upgrades in place.
  The **standalone zip** needs no installing at all: unzip it, or copy it to
  a USB key, and run it from there, which suits locked-down school
  computers. Windows SmartScreen warns about both until the project has a
  code-signing certificate: choose "More info", then "Run anyway".
- Exporting a game as an MSI is offered only where the WiX 3 toolset is
  installed; otherwise the export builds an app folder.
- The engine and IDE test suites now run on Windows on every change.

### Fixes

- **No more warning spam in the Problems tab.** Every class compiled with a
  warning about an unknown `threadchecker` annotation; the annotation classes
  now ship with the engine.
- **The editor's error popup is readable in the light theme.**
- **Super Greenfoot no longer contacts greenfoot.org when it starts.** It
  used to fetch Greenfoot's news message and send Greenfoot's usage
  statistics (an ID kept on the computer, the operating system, the Java
  version and the language), reporting itself as Greenfoot 3.9.0. Both
  belong to Greenfoot itself. (Share's Publish tab still talks to the
  Greenfoot Gallery; it is removed in 0.3.0.)
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

### Known issues

- Import Class shows its templates as one list; the categories mentioned for
  0.2.0 arrive in the dialog in 0.3.0.
- A scenario that declares its own `distanceTo(Actor)` as `private`, or with a
  return type other than `double`, does not compile, because it clashes with
  `Actor.distanceTo(Actor)`. Rename yours for now; 0.3.0 renames the built-in
  one.
- Stopping a scenario also pauses its sounds, unlike Greenfoot 3.9.0, so a
  sound played just before `Greenfoot.stop()` is not heard. Fixed in 0.3.0.

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
