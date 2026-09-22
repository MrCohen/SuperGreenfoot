# SuperWindow: windows inside the world

`greenfoot.SuperWindow` is SuperGreenfoot's tenth public API class: a framed,
movable panel that lives inside a world and holds other actors. It replaces
the `SuperWindow` helper from MrCohenLibrary150 (v0.4), which had to fake
z-order by removing and re-adding actors. A scenario that still carries its own
`SuperWindow.java` in the default package keeps using that copy, because a
default-package class shadows the one from `import greenfoot.*`.

Design decisions (owner, 2026-09-22): a true container with window-local
coordinates; windows painted in their own layer above every ordinary actor;
scrolling content with a new mouse-wheel event; a core class, not an Import
Class template. Keyboard focus and nested windows are deferred.

## Model

- A window **is an Actor**. `world.addObject(window, x, y)` places its centre,
  like any actor; `setTopLeft(x, y)` places its top-left corner instead.
- Actors go **inside** a window with `window.addObject(actor, x, y)`. From then
  on the actor's `getX()`, `getY()`, `setLocation`, `move`, `isAtEdge` and the
  precise variants are all in the **window's coordinates**: (0,0) is the
  top-left cell of the content area. The actor moves with the window. Nothing
  in the actor's own code changes; `actor.getWindow()` says which window it is
  in (null for an actor placed directly in the world).
- The window's contents **join and leave the world with it**. Add actors to a
  window before it is in a world, add the window to a world, remove it, add it
  to a different world: the contents come along, positions intact. Removing an
  actor with `world.removeObject` also takes it out of its window, and
  `world.addObject(actor, ...)` on an actor that is in a window takes it out of
  the window and places it in the world.
- **Closing** (`close()`, or the title-bar button) hides the window and its
  contents but keeps everything in the world. `open()` shows it again where it
  was and brings it to the front. While closed or minimised, the contents are
  not drawn, cannot be clicked, do not act, and are invisible to collision
  queries. The window itself still acts, so it can decide to reopen.
- **Painting**: every window is painted after every ordinary actor. Windows are
  ordered by layer (normal, then modal, then always-on-top), then by their `z`
  (`Actor.setZ`), then by creation. Pressing a window, or dragging it in the
  paused IDE, brings it to the front of its layer. Inside a window, the contents
  follow the world's usual paint rules (class paint order, then z) and are
  clipped to the content area. The content background image is drawn first.
- **Mouse**: `Greenfoot.mouseClicked(actor)` and friends work for windows and
  for actors inside them; picking follows what is on screen, including the
  clipping. `MouseInfo.getWindow()` returns the window under the mouse (frame
  or contents), or null over the bare world, so `if (m.getWindow() == null)`
  keeps an inventory click from also firing the sword. `World.getWindowAt(x, y)`
  does the same for any position.
- **Modal**: while a modal window is open, every mouse event outside it is
  reported as being on the modal window, so nothing behind it can be pressed,
  clicked or dragged. Modal windows sit above normal windows.
- **Collision**: `getIntersectingObjects`, `getOneIntersectingObject`,
  `isTouching`, `getObjectsAtOffset`, `getNeighbours` and `getObjectsInRange`
  only return actors in the **same window** as the caller (or, for an actor in
  the world, actors not in any window), never actors in a closed or minimised
  window, and never window frames unless the query asks for `SuperWindow.class`
  (or a subclass). `Actor.intersects(other)` stays pure geometry.
  `World.getObjectsAt(x, y, cls)` is a world-space query and sees actors inside
  open windows too.
- **Scrolling**: `setContentSize(w, h)` makes the content larger than the
  visible area; `setScroll`, `scrollBy`, `getScrollX/Y` move it. With
  `setScrollable(true)` the mouse wheel over the window and a vertical scroll
  bar (drag the thumb, click the track to page) scroll it.
- Windows are designed for cell size 1 worlds. In larger-cell worlds the
  window is still positioned in cells and its decorations in pixels; contents
  are placed at whole cells of the window.

## SuperWindow

| Constructor | |
|---|---|
| `SuperWindow(int width, int height)` | Borderless-style panel: no title bar, locked, not closable or minimisable (a HUD). Width and height are the content area in cells. |
| `SuperWindow(int width, int height, String title)` | Title bar with title, close and minimise buttons; draggable. |
| `SuperWindow(GreenfootImage image, String title)` | Sized to the image, with the image as content background (a picture frame). `null` title means no title bar. |

| Contents | |
|---|---|
| `addObject(Actor, int x, int y)` | Local position. Moves the actor in from the world or another window. |
| `removeObject(Actor)`, `removeObjects(Collection)`, `removeAllObjects()` | Out of the window and the world. |
| `<A> List<A> getObjects(Class<A>)`, `numberOfObjects()` | In insertion order. |

| State | |
|---|---|
| `open()`, `close()`, `toggle()`, `isOpen()` | Show/hide, keeping position and contents. |
| `minimize()`, `restore()`, `isMinimized()` | Collapse to the title bar; the title bar stays put. |
| `bringToFront()`, `sendToBack()`, `isFrontWindow()` | Order within the window's layer. |

| Geometry | |
|---|---|
| `getWidth()`, `getHeight()` | Visible content area, cells. |
| `getFrameWidth()`, `getFrameHeight()` | Whole frame including border and title bar, pixels. |
| `setTopLeft(int x, int y)`, `getLeft()`, `getTop()` | Frame corner in world cells. |
| `setLocation(int,int)` / `(double,double)` | Centre, like any actor; clamped on screen when `isKeepOnScreen()`. |
| `toWorldX/Y(local)`, `toLocalX/Y(world)` | Convert between window and world cells (e.g. `toLocalX(mouse.getX())`). |
| `containsWorldPosition(x, y)` | True inside the visible content area. |

| Appearance | |
|---|---|
| `getBackground()`, `setBackground(GreenfootImage)` | Content-sized image drawn behind the contents (like `World.getBackground()`). |
| `setBackgroundColor`, `setBorderColor`, `setTitleBarColor`, `setTitleColor` (+ getters) | A transparent background colour gives a see-through panel. |
| `setBorderThickness(int)`, `setTitleBarHeight(int)`, `setTitleBarVisible(boolean)`, `setTitle(String)`, `setTitleFont(Font)` | Decorations in pixels. |

| Behaviour | |
|---|---|
| `setClosable`, `setMinimizable`, `setDraggable` (+ `is...`) | Title-bar buttons and dragging. `setDraggable(false)` locks the window. |
| `setKeepOnScreen(boolean)` (default true) | The whole frame stays inside the world. |
| `setBounded(boolean)` (default false) | Contents are clamped to the content area, like a bounded world. |
| `setModal(boolean)` | Captures all mouse input; layer above normal windows. |
| `setAlwaysOnTop(boolean)` | Top layer (tooltips, notifications). |
| `setScrollable(boolean)`, `setContentSize(w, h)`, `getContentWidth/Height()`, `setScroll(x, y)`, `scrollBy(dx, dy)`, `getScrollX/Y()` | Scrolling. |

| Mouse and hooks | |
|---|---|
| `isMouseOver()`, `isBeingDragged()` | Position-based; the mouse need not have moved this act. |
| `protected closeButtonClicked()`, `minimizeButtonClicked()` | Default `close()` / toggle minimise; override to confirm, or to remove the window from the world instead. |
| `protected opened()`, `closed()` | After `open()`/`close()` (not on adding to a world). |

Constants: `DEFAULT_TITLE_BAR_HEIGHT` (20), `DEFAULT_BORDER_THICKNESS` (2),
`DEFAULT_BACKGROUND_COLOR`, `DEFAULT_BORDER_COLOR`, `DEFAULT_TITLE_BAR_COLOR`,
`DEFAULT_TITLE_COLOR`.

## Additions elsewhere

| Method | Notes |
|---|---|
| `Actor.getWindow()` | The containing window or null. |
| `World.getWindows()` | Windows back to front. |
| `World.getWindowAt(int x, int y)` | Front-most open window covering a world position, or null. |
| `MouseInfo.getWindow()` | Window under the mouse, or null. |
| `MouseInfo.getScrollAmount()` | Wheel movement this act, in pixels; positive rolled towards you (scroll down). |
| `Greenfoot.mouseScrolled(Object)` | Same contract as `mouseMoved(Object)`: null, a World, or an Actor. Wheel events in one act add up and survive a click in the same act. |

Mouse wheel plumbing: the IDE forwards JavaFX `ScrollEvent`s over the world
view (Classic, the Super IDE and the full-screen view) as `Command.MOUSE_SCROLLED`;
the Swing player listens for `MouseWheelEvent`s. Both report pixels, positive
downwards.

## IDE integration

- Paused dragging in the IDE moves a window with its contents, and a dragged
  window comes to the front. Dragging an actor inside a window keeps it in the
  window (world pixels are converted to the window's cells).
- Dropping a newly constructed actor onto an open window puts it in the window.
- The Classic class diagram shows `SuperWindow` under Actor, with the
  scenario's window subclasses beneath it; the Super IDE lists them with the
  kind "SuperWindow". Double-clicking the node opens its API documentation.
- Save the World does not record actors inside windows (the window's own code
  places them).

## Semantics worth knowing

- `MouseInfo.getActor()` keeps upstream's meaning: set only on acts with a
  mouse event. For hover effects use `world.getObjectsAt(m.getX(), m.getY(), Item.class)`
  (sees into open windows) or `window.isMouseOver()`.
- `world.getObjects(cls)` and `numberOfObjects()` include windows and their contents.
- A window's own `act()` runs even while it is closed.
- `addedToWorld` fires once for each actor when its window enters a world, and
  again if the window is moved to another world. It never fires because a
  window moved.
- Nested windows are rejected with `IllegalArgumentException`.

## Implementation map

| Piece | Where |
|---|---|
| Container link, local coordinates, bounds translation | `Actor.window`, `originPixelX/Y`, `toPixelX/Y`, `worldCellX/Y`, `containerMoved`, `isActive` |
| Window list, contents transfer, query filtering, picking | `World.windows`, `addObjectFromWindow`, `removeObjectKeepingWindow`, `filterFor`, `getObjectsAtPixel` |
| Frame drawing, geometry, input handling | `SuperWindow.redrawFrame`, `content*Px`, `handleInput` (called from `Simulation.runOneLoop` via `World.processWindows`) |
| Painting | `WorldRenderer.paintObjects` (window layer, clipping, `paintActor`), `WindowVisitor` |
| Modal capture, IDE drag/drop | `WorldHandler.getObject`, `startDrag`, `finishDrag`, `addActorAtPixel` |
| Tests | `greenfoot/src/test/java/greenfoot/SuperWindowTest.java` (27) |
| Demo | `super-scenarios/SuperWindowDemo/` (inventory, HUD, scrolling list, modal pause, picture frame, tooltip, world transfer with W) |
