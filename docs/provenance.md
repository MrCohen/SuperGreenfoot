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
