# Phase 1 API additions (precision, depth, text metrics)

All additions are backward compatible: existing methods keep their signatures
and behaviour, and scenarios written for Greenfoot 3.9 render pixel-identically
unless a world opts into smooth rendering.

## Actor: precise position and rotation

| Method | Notes |
|---|---|
| `double getPreciseX()` / `double getPreciseY()` | Fractional cell coordinates. `getX()`/`getY()` return these rounded half-away-from-zero (SuperSmoothMover's `neutralRound`). Throw if not in a world, like `getX()`. |
| `void setLocation(double x, double y)` | Stores the precise location; clamps to `0..width-1` in bounded worlds. Does **not** call `setLocation(int,int)` (and vice versa), so a subclass intercepting movement must override both. |
| `void move(double distance)` | Uses the precise rotation and accumulates fractional movement. |
| `double getPreciseRotation()` | 0 (inclusive) to 360 (exclusive). |
| `void setRotation(double degrees)` / `void turn(double degrees)` | Precise rotation; `getRotation()` returns it rounded (359.7 rounds to 0). |
| `void turnTowards(double x, double y)` / `void turnTowards(Actor other)` | Precise aiming. |
| `double distanceTo(Actor other)` | Euclidean distance in cells between precise locations. |

Rule of thumb: **the int API stays int-exact.** `setLocation(int,int)`, `move(int)`,
`setRotation(int)` and `turn(int)` behave exactly as upstream and snap the precise
values to whole cells/degrees. Precision survives only while you use the
`double` methods. This keeps every existing scenario's behaviour identical.

## Actor: image rotation lock (SuperSmoothMover's "static rotation")

| Method | Notes |
|---|---|
| `void setImageRotation(double degrees)` | Sets the drawn angle and **locks** it: further `turn()`/`setRotation()` change only the movement direction. |
| `int getImageRotation()` / `double getPreciseImageRotation()` | Drawn angle (int return type matches SuperSmoothMover's existing override). |
| `void setImageRotationLocked(boolean)` / `boolean isImageRotationLocked()` | Unlocking snaps the image back to the actor's rotation. |

Collision bounds and `intersects()` use the *image* rotation, since that is what
is on screen.

## Actor and World: depth

| Method | Notes |
|---|---|
| `Actor.setZ(double)` / `getZ()` | Higher z paints later (in front). Ties keep insertion order. Never triggers `addedToWorld`/`removedFromWorld`; never changes act order. |
| `World.setZSortByY(boolean)` | Paint key becomes (precise y, z, insertion). |
| `World.setGlobalZOrder(boolean)` | Ignore class paint order; one global (y, z) order. Default off: z sorts **within** each `setPaintOrder` group (decision D6). |
| `World.setZSortAnchor(ZSortAnchor)` / `getZSortAnchor()` | Added 0.1.4. Which part of an actor's image counts as its ground line: `CENTRE` (default, as before) or `BOTTOM`. See below. |
| `Actor.setSortOffset(double)` / `getSortOffset()` | Added 0.1.4. Pixels to move this actor's ground line down, on top of the world's anchor. |

Implementation: `World.getObjectsInFinalPaintOrder()` returns the live
class-ordered set when no depth feature is in use (zero cost), otherwise a
stably sorted snapshot built once per paint. Mouse picking follows paint order
automatically because it uses the renderer's paint sequence numbers.

### The sort anchor (0.1.4)

Greenfoot draws an actor centred on its position, so y-sorting by that position
asks a tall picture to stand on its own middle. The way to make a tree look
right was to pad its picture with empty rows below until the foot of the trunk
reached the centre. For a picture that draws nothing below its feet that is
**twice the height for nothing**, in memory and in every cached variant of it.
It is why Tenth Realm shares twelve cached sprites per kind of tree (TR17)
instead of giving every tree its own look.

`ZSortAnchor.BOTTOM` measures from the bottom edge of the image instead, so the
picture can be exactly as tall as what it draws:

```java
setZSortByY(true);
setZSortAnchor(ZSortAnchor.BOTTOM);
```

The height is read at every sort, so an actor that swaps its image - a walk
cycle, a tree felled into a stump - needs nothing else. Where the feet are not
quite at the bottom edge, because a shadow or a tuft of grass is drawn below
them, the individual actor trims the line:

```java
tree.setSortOffset(-6);   // the trunk ends 6 pixels above the picture's edge
```

**The key.** With an anchor or an offset in play, the sort key is worked out in
pixels rather than cells, so an image measurement means the same thing at any
cell size:

```
key = preciseY * cellSize
    + (anchor == BOTTOM ? imageHeight / 2 : 0)
    + sortOffset
```

Ties still fall through to z, then to insertion order.

**What it does not do.** The anchor and the offset change paint order and
nothing else: the actor is drawn, dragged, picked and collided with exactly
where it was. Neither has any effect unless `setZSortByY(true)` is on. An actor
with no image contributes no height rather than failing.

**Cost.** A world that uses neither keeps the original comparator, so the plain
y-sorting path is byte for byte what it was. Otherwise the key is computed from
the `image` field directly rather than through `getImage()`, which a subclass
may override - a comparator has to answer the same question the same way every
time or the sort will not terminate.

Demo: `super-scenarios/PrecisionDemo`, the grove at the bottom. Press A to switch
anchors and watch the walker pass among the trees: with `CENTRE` it strolls in
front of a tree it is plainly standing behind.

## World: smooth rendering (decision D7, opt-in)

`World.setSmoothRendering(boolean)`: draw actors at precise positions and
precise image rotations with bilinear interpolation and blended edges.
At whole-cell positions and whole-degree rotations the output is pixel-identical
to Greenfoot (same floor-based placement convention). Collision always uses
whole-cell positions and whole-degree image rotation.

Implementation note: Java2D never blends an image's outer edge, so
`GreenfootImage` keeps a cached copy with a 1-pixel transparent margin
(`getPaddedImage()`, invalidated on any modification) and the smooth path draws
that copy. Cost measured at about 17 microseconds per 64x64 draw versus 2
plain, so this stays opt-in.

## Font and GreenfootImage: text metrics

| Method | Notes |
|---|---|
| `Font.getStringWidth(String)` | Ink width in pixels (widest line if multi-line). Matches a pixel scan of `drawString` output within 1 px (tested). |
| `Font.getStringHeight(String)` | Ink height, including descenders and multi-line spacing. |
| `Font.getAscent()` / `getDescent()` / `getLineHeight()` | Typographic metrics; line height is the spacing `drawString` uses for `\n`. |
| `GreenfootImage.drawCenteredString(String, int cx, int cy)` | Horizontal centring by ink width per line; vertical centring of the cap-height-to-baseline block (descenders hang below). |

These replace the pixel-scanning `getStringWidth`/`getFontHeight` helpers in
`SuperTextBox`, `SuperDisplayLabel` and `Utility` (4-34 ms per call on the
gallery) with sub-millisecond calls.

## Config: separate preferences directory

The IDE now stores preferences under `org.supergreenfoot` (macOS),
`supergreenfoot` (Windows) or `.supergreenfoot` (Linux) instead of sharing
upstream Greenfoot's directory. First launch of the fork therefore starts with
fresh settings.

## Known compatibility notes

- A student class that already declares `getPreciseX()`, `setZ()`, `distanceTo()`
  etc. with a *different return type* will fail to compile. Same-signature
  declarations (as in SuperSmoothMover) simply override.
- `turnTowards(int,int)` keeps upstream's truncating `(int)` conversion; the
  double overload rounds properly.
