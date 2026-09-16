# Third-party code and assets audit (Phase 0)

Source of truth upstream: `greenfoot/doc/THIRDPARTYLICENSE.txt` and
`bluej/doc/THIRDPARTYLICENSE.txt`. Both are GPLv2-compatible throughout.

## Libraries declared upstream (greenfoot list)

| Library | License | Note |
|---|---|---|
| Apache Commons (logging, codec) | Apache 2.0 | |
| Apache HttpComponents (httpclient, httpcore, httpmime) | Apache 2.0 | Gallery publishing only |
| classgraph | MIT | |
| diffutils | Apache 2.0 | |
| guava | Apache 2.0 | |
| hamcrest | BSD | tests |
| JLayer (`jl1.0.1.jar`) | LGPL 2.1 | MP3 decoding; keep for the mixer |
| JUnit | CPL 1.0 / EPL | tests |
| nsmenufx | BSD | macOS menus |
| opencsv | Apache 2.0 | `storage.csv` for UserInfo |
| wellbehavedfx | BSD | editor |
| sequence-library | BSD | |
| Source Code Pro font | SIL OFL 1.1 | Stride editor |
| xom | LGPL 2.1 | |
| AppleJavaExtensions | BSD-like | legacy |

The declared list is **stale relative to what Gradle actually bundles**
(e.g. guava 33.3.1, httpclient 4.5.13, jgit 6.8 (EDL/BSD), simple-png 0.2.0,
JavaFX 21 (GPLv2+CPE), jna 5.7 (Apache 2.0/LGPL), JavaEWAH (Apache 2.0)).
Phase 7 task: regenerate the third-party list from `./gradlew dependencies`
before any public release.

## New dependencies SuperGreenfoot will add

| Dependency | License | Phase | Compatible with GPLv2+CPE? |
|---|---|---|---|
| TeaVM | Apache 2.0 | 5 | yes |
| (server) Node runtime on Vercel, `pg` or KV client | MIT | 6 | yes, separate deployable |

## Bundled assets

- `greenfoot/imagelib/` (animals, backgrounds, buildings, food, nature,
  objects, other, people, symbols, transport): Greenfoot's own image library,
  shipped under the project license. No separate credits file in the tree.
- `greenfoot/scenarios/`: `ants`, `lunarlander`, `LTA`, `pengu`,
  `trick-the-turtle` each carry a `README.TXT`; `lunarlander`, `pengu` and
  `trick-the-turtle` name authors/credits inside. Redistributable with the
  project.
- Four `.wav`/`.mp3` files under `greenfoot/` (scenario sounds).
- `super-scenarios/MrCohenLibrary150/`: six images by the owner or from the
  LPC sprite set (`sword_guy.png`, LPC assets are CC-BY-SA 3.0 / GPL 3.0
  dual-licensed; attribution required in any release that ships it). No
  sounds. `GifImage.java` inside it is upstream Greenfoot code (Berry/Brown)
  under the same GPL license.

## Action items

- [ ] Phase 7: regenerate the library list from Gradle.
- [ ] Add an `LPC-credits.txt` next to `sword_guy.png` before publishing
      the reference scenario.
