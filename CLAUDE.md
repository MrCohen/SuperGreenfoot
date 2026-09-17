# SuperGreenfoot (fork repository)

This is a git clone of upstream BlueJ/Greenfoot at tag `GREENFOOT-RELEASE-3.9.0`
with SuperGreenfoot work on branch `super/main`. See `docs/provenance.md`
for the upstream base and every upstream file we change.

## Rules

- This repository is public. Planning documents (the master plan, decisions
  log, assessments, drafts) are private and live outside the repo. Never
  create, copy or commit them here.

- Keep diffs to upstream files small and isolated; log each one in
  `docs/provenance.md` so upstream merges stay reviewable.
- Keep upstream copyright headers; new files get the same GPLv2+CPE header
  with "SuperGreenfoot contributors" added.
- `super-scenarios/MrCohenLibrary150/` must compile and run after every
  phase. Never edit it to make a test pass; fix the engine.
- New engine code is TeaVM-clean: no `java.awt` outside
  `greenfoot.backend.awt` (Phase 3 onward), no reflection, no new threads
  outside `Simulation` and the sound mixer.
- Public API changes: keep `getX()/getY()/getRotation()` returning `int`;
  add new methods rather than changing signatures; regenerate the user
  Javadoc (`./gradlew :greenfoot:userJavadoc`) when the nine API classes change.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :greenfoot:compileJava      # engine + IDE
./gradlew :greenfoot:test             # headless engine tests (41 upstream tests pass on 2026-09-16)
./gradlew runGreenfoot                # launch the IDE
```

The owner's shortcut for all of this is the `./dev` script in the repo root
(`./dev run`, `test`, `player <scenario>`, `app`, `dmg`, `install`); keep it
working when build steps change.

Gradle 8.5 wrapper, JDK 21, JavaFX 21.0.3 via the openjfx plugin. The
thread-checker annotation processor (`@OnThread`) runs during compile and
fails the build on thread-tag violations.

## Where things are

| Concern | Path |
|---|---|
| Public API (nine classes) | `greenfoot/src/main/java/greenfoot/*.java` |
| Act loop / speed | `greenfoot/src/main/java/greenfoot/core/Simulation.java` |
| Paint order sets | `greenfoot/src/main/java/greenfoot/{TreeActorSet,ActorSet}.java` |
| Renderer | `greenfoot/src/main/java/greenfoot/gui/WorldRenderer.java` |
| Collision (BSP) | `greenfoot/src/main/java/greenfoot/collision/` |
| Sound | `greenfoot/src/main/java/greenfoot/sound/` |
| Debug-VM to IDE frames | `greenfoot/src/main/java/greenfoot/vmcomm/` |
| IDE window / world view | `greenfoot/src/main/java/greenfoot/guifx/{GreenfootStage,WorldDisplay,ControlPanel}.java` |
| Export | `greenfoot/src/main/java/greenfoot/export/`, `guifx/export/` |
| Headless tests | `greenfoot/src/test/java/greenfoot/` (`WorldCreator`, `TestUtilDelegate`) |
| Regression scenario | `super-scenarios/MrCohenLibrary150/` |
