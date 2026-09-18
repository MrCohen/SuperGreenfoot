# Super Greenfoot logo

Created 2026-09-17 by Jordan Cohen for the Super Greenfoot remix.

## Provenance

This mark is **original artwork**. Unlike the splash, about, and DMG artwork in
the parent directory, it does **not** incorporate, trace, or derive from any
upstream Greenfoot image. It was drawn from scratch in DrawSimple; the editable
source is `SuperGreenfootLogo.dsimp` in this directory.

The sole is a single closed bezier path; the toes are five separate rounded
capsules. The design deliberately differs from the upstream Greenfoot icon on
every axis available:

| | Upstream Greenfoot | Super Greenfoot |
|---|---|---|
| Toes | four round dots | five capsules, arced |
| Treatment | glossy, bevelled, drop shadow | flat, hard-edged |
| Ground | none (floating foot) | rounded-square tile |
| Green | warm yellow-green `#88C048` | cool emerald `#047857` → `#34D399` |

The footprint *concept* is shared with upstream, which is deliberate — Super
Greenfoot is a fork and the visual lineage is intentional. The *expression* is
independent.

## Why this matters

Greenfoot's source is GPLv2 with Classpath Exception, which licenses the code
and the image files. A copyright license does **not** convey trademark rights.
Reusing an upstream mark unaltered on a fork is what creates a likelihood of
confusion as to source or sponsorship, so the mark here was redrawn rather than
restyled.

Super Greenfoot is a modified remix by Jordan Cohen and is **not** an official
Greenfoot release, and is not affiliated with or endorsed by the Greenfoot
project, King's College London, or Michael Kölling. Greenfoot was created by
Michael Kölling and Poul Henriksen.

## Files

- `SuperGreenfootLogo.dsimp` — editable DrawSimple source (1024 × 1024)
- `SuperGreenfootLogo@2x.png` — master export, full bleed
- `exports/supergreenfoot-logo-{16,32,48,64,128,256,512,1024}.png` — full bleed
- `exports/supergreenfoot-appicon-1024.png` — content inset to 824/1024 per
  Apple's macOS icon grid; this is the master for the `.icns`
- `exports/supergreenfoot.icns` — macOS icon, 16→1024 including `@2x` variants

The logo artwork is distributed with the modified project under the same
**GNU General Public License version 2 with the Classpath Exception**. See
[`LICENSE.txt`](../../../LICENSE.txt) for the full license and exception text.

## Regenerating the app icons

`exports/` is generated from `SuperGreenfootLogo@2x.png`. Rebuilding the app
icons from a new master is a matter of re-running the resize/`iconutil` steps
and copying into the locations listed in [`APPLIED.md`](APPLIED.md).
