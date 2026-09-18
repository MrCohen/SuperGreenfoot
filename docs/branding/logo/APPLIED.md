# Where the logo is applied

Every file below was replaced on 2026-09-17 with artwork generated from
`SuperGreenfootLogo@2x.png`. All of them were previously upstream Greenfoot
artwork. Paths are relative to the repository root.

## Wired into the running app

| File | Size | Used by |
|---|---|---|
| `bluej/lib/images/greenfoot-icon-32.png` | 32 | window icon |
| `bluej/lib/images/greenfoot-icon-48.png` | 48 | `BlueJTheme.MEDIUM_ICON_SUFFIX` — window icon on Windows/Linux |
| `bluej/lib/images/greenfoot-icon-256.png` | 256 | `BlueJTheme.LARGE_ICON_SUFFIX` |
| `bluej/lib/images/greenfootvm.icns` | icns | `Config.GREENFOOT_DEBUG_DOCK_ICON` — dock icon of the debug VM |
| `greenfoot/resources/images/greenfoot.icns` | icns | macOS `.app` icon (`bluej/package/greenfoot-build.xml`, `installer/mac/build-dmg.sh`) |
| `greenfoot/resources/images/greenfoot-icon-16.png` | 16 | copied to `greenfoot.png` by `copyGreenfootIcon1`/`2` in `greenfoot/build.gradle` |
| `greenfoot/resources/images/greenfoot-icon-32.png` | 32 | copied to `greenfoot.png` into the jar (`greenfoot/build.gradle`) |
| `bluej/package/winlaunch/greenfoot-icon-256-shadow.ico` | multi | Windows launcher icon |

## Not referenced by code or build, replaced for consistency

These carried the upstream mark but nothing reads them. They were replaced so
no upstream artwork remains in the tree.

- `greenfoot/resources/images/greenfoot-icon-128.png`
- `greenfoot/resources/images/greenfoot-icon-128-darker.png`
- `greenfoot/resources/images/greenfoot-icon-128-shadow.png`
- `greenfoot/resources/images/greenfoot-icon-256-shadow.png`
- `greenfoot/resources/images/greenfoot-icon-256-shadow.ico`
- `greenfoot/resources/images/greenfoot-icon-32.ICO`
- `bluej/package/greenfoot-installer-icon.png`
- `greenfoot/resources/images/greenfoot-icon-{64,medium,big,huge}.jpg` —
  portrait marketing images, regenerated at their original pixel dimensions
  with the square mark centred on white

## Still upstream artwork — outstanding

- **`greenfoot/resources/images/*.psd`** — upstream's layered Photoshop sources
  for *their* footprint (`greenfoot-icon-{128,256-shadow,64,big,huge,medium}.psd`).
  Nothing in the build reads them. They are the upstream mark in editable form
  and should almost certainly be deleted rather than replaced.
- **`docs/branding/SuperGreenfootSplash.dsimp`**, **`SuperGreenfootAbout.dsimp`**,
  **`SuperGreenfootDMG.dsimp`** and their `@2x.png` exports — per
  `SPLASH_CREDITS.md` these *incorporate the upstream footprint image* from
  `greenfoot-icon-256-shadow.png`. Swapping in the new mark would remove the
  last upstream artwork from the shipped product.

## Regenerating

The macOS `.icns` is built from `exports/supergreenfoot-appicon-1024.png` (the
824/1024 padded master, not the full-bleed one) via an `.iconset` directory and
`iconutil -c icns`. Note that `iconutil` needs 8-bit PNGs.
