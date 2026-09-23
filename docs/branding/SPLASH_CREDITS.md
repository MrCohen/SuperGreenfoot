# Super Greenfoot splash artwork

Created 2026-09-17 and reworked 2026-09-23 by Jordan Cohen for the Super
Greenfoot remix.

The current splash is `SuperGreenfootSplash-v2.dsimp` (editable DrawSimple
source) with its master export `SuperGreenfootSplash-v2@2x.png`. It is
**original artwork**: the mark on the right is the Super Greenfoot logo from
[`logo/`](logo/LOGO_CREDITS.md), and the accent colour is that logo's emerald
(`#34D399`). No upstream Greenfoot image is incorporated.

The first version, `SuperGreenfootSplash.dsimp` / `SuperGreenfootSplash@2x.png`,
is kept for reference. It incorporated the upstream footprint image
`greenfoot/resources/images/greenfoot-icon-256-shadow.png` from the Greenfoot
3.9.0 source release (`GREENFOOT-RELEASE-3.9.0`, commit `6c1390e`) at
[k-pet-group/BlueJ-Greenfoot](https://github.com/k-pet-group/BlueJ-Greenfoot),
and is no longer shipped.

Greenfoot was created by Michael Kölling and Poul Henriksen. Super Greenfoot
is a modified remix by Jordan Cohen, and is not an official Greenfoot release.

The Greenfoot source repository is licensed under the **GNU General Public
License version 2 with the Classpath Exception**. This splash artwork is
distributed with the modified project under those same terms. See
[`LICENSE.txt`](../../LICENSE.txt) for the full license and exception text,
and [`greenfoot/doc/THIRDPARTYLICENSE.txt`](../../greenfoot/doc/THIRDPARTYLICENSE.txt)
for third-party notices.

## Versions on the splash

The artwork leaves two blank slots under the hero rule. The build
(`boot/build.gradle`, tasks `drawGreenfootSplashVersion` and
`drawGreenfootSplashVersion2x`) stamps the Super Greenfoot version
(`supergreenfoot_version` in `version.properties`) beneath `VERSION` and the
upstream Greenfoot version (`greenfoot_*`) beneath `BASED ON GREENFOOT`.
Positions, in the 1x image: (44, 221) and (161, 221); double them for `@2x`.

The two shipped PNGs, `greenfoot/resources/images/greenfoot-splash.png` and
`greenfoot-splash@2x.png`, match the upstream splash sizes: 536 × 329 and
1072 × 658 pixels. The 1x file is a Lanczos downscale of the 2x export.
