# Mouse input: releases and held buttons

SuperGreenfoot 0.2.0. Added because a game that lets you *hold* a mouse button —
aim with the left, block with the right — could not ask the engine about it.
Upstream Greenfoot reports a press, a click, a drag and a drag ending, so holding
a button had to be reconstructed from a click and a drag-end, and a plain release
with no drag was never reported at all.

Both additions are new methods on `greenfoot.Greenfoot`. Nothing existing changed
its meaning, and no scenario needs recompiling.

## `Greenfoot.mouseReleased(Object obj)`

True if a mouse button went up over `obj` during this act. `obj` is an `Actor`,
a `World`, or `null` for anywhere in the world — the same rule as
`mousePressed`, `mouseClicked` and the rest.

Two things to know:

- **It says where the button went up**, not where the press began.
  `mouseDragEnded` is the other way round: it reports the actor the drag
  *started* on. Drag from a tree to a chest and you get
  `mouseDragEnded(tree) == true` and `mouseReleased(chest) == true` in the same
  act. That is what makes drag-and-drop expressible without tracking the pointer
  yourself.
- **It is always reported.** Greenfoot normally keeps one event per act, chosen
  by priority (drag end, then click, then press, then drag, then move), so a
  click can hide a press. A release sits outside that contest, as a mouse-wheel
  movement does, and is reported for the act it happened in even when a click or
  a drag ending was recorded too.

`getMouseInfo()` will carry the released button and position, unless a press,
click, drag or drag ending in the same act has claimed the mouse info — those
still win, so that no existing scenario sees a different `MouseInfo` than it did
before.

```java
if (Greenfoot.mouseReleased(this))
{
    MouseInfo mouse = Greenfoot.getMouseInfo();
    if (mouse.getButton() == MouseInfo.RIGHT)
    {
        stopBlocking();
    }
}
```

## `Greenfoot.isMouseButtonDown(int button)`

True while that button is held: `MouseInfo.LEFT`, `MouseInfo.MIDDLE` or
`MouseInfo.RIGHT` (the numbers 1, 2 and 3, matching `MouseInfo.getButton()`). Any other number throws `IllegalArgumentException`
rather than quietly answering false.

Unlike every other mouse method this is a **state, not an event**. It stays true
for every act between the press and the release, including acts in which the
mouse does nothing at all.

The answer is taken once at the start of each act, so two calls in the same act
always agree — a button that goes down halfway through an act reads as down from
the next act onward. That matches how the rest of the mouse API is frame-based.

```java
public void act()
{
    if (Greenfoot.isMouseButtonDown(MouseInfo.LEFT))
    {
        charge = Math.min(charge + 1, MAX_CHARGE);   // hold to charge
    }
    if (Greenfoot.mouseReleased(null))
    {
        fire(charge);                                 // let go to shoot
        charge = 0;
    }
}
```

### When a held button is let go

A button stops counting as held when it is released, and also when:

- **the scenario starts running**, so a button held while paused does not leak
  into the run; and
- **the world view loses keyboard focus**. The release may then be delivered to
  whatever window took the focus and never reach the scenario, which would leave
  the button stuck down for ever. Held keys are dropped on focus loss for the
  same reason, which is long-standing Greenfoot behaviour.

Both are deliberate: a stuck button is much worse than a missed one.

## Where it works

Everywhere, with no protocol change. `MOUSE_RELEASED` already travelled from the
IDE to the debug VM over `vmcomm` and from the Swing player's `MouseAdapter`;
the engine simply discarded it unless a drag was in progress. So the Classic
IDE, the SuperGreenfoot IDE, the full-screen view and exported games all behave
the same.

## Implementation notes

- `MouseEventData` gains a `mouseReleasedInfo` kept beside `mouseScrolledInfo`,
  outside the priority machinery, and surviving `initKeepingExtras()` (which was
  `initKeepingScroll()`) for the rest of the act.
- `MousePollingManager` records the release whatever the priorities decide, and
  keeps the held buttons in a `buttonsDown` bit set written on the GUI thread,
  snapshotted into `currentButtonsDown` by `newActStarted()` for the simulation
  thread to read.
- `WorldHandler.worldFocusChanged(false)` now tells the mouse manager as well as
  the keyboard manager.
- Tests: ten new cases in `greenfoot/src/test/java/greenfoot/mouse/MousePollTest.java`,
  beside the fifteen upstream ones, which are unchanged.
- Demo: `super-scenarios/DisplayDemo`, `GameWorld` — hold the left button to
  charge, let go to fire a ball from the pointer.
