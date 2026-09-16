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
