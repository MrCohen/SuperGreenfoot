# Super Greenfoot macOS installer artwork

Created 2026-09-17 by Jordan Cohen in DrawSimple for the Super Greenfoot remix.

The artwork uses the footprint from the Greenfoot 3.9.0 source release
(`GREENFOOT-RELEASE-3.9.0`, commit `6c1390e`) at
[k-pet-group/BlueJ-Greenfoot](https://github.com/k-pet-group/BlueJ-Greenfoot):
`greenfoot/resources/images/greenfoot-icon-256-shadow.png`. The editable source
is `SuperGreenfootDMG.dsimp` in this directory. The 1320 × 920 export is
`SuperGreenfootDMG@2x.png`; the installer uses the 660 × 460 export at
`installer/mac/assets/dmg-background.png`, extended by a 32-point strip of the
footer colour (660 × 492 in total). The Finder window leaves room for the
viewer's path bar, and the strip fills that space when the path bar is hidden.
After re-exporting from DrawSimple, add the strip again.

The Finder layout places `SuperGreenfoot.app` at (153, 290) and the Applications
shortcut at (508, 290), with 96-point icons, which centres each icon and its
label in its card. The hidden `.background` folder is parked beyond the right
edge of the window, so it stays off the artwork for viewers who show hidden
files. The background deliberately leaves those two areas empty so the real
Finder icons remain readable. The text and arrow show how to install the app and
acknowledge the independent remix.

This artwork is distributed with Super Greenfoot under the **GNU General Public
License version 2 with the Classpath Exception**. See
[`LICENSE.txt`](../../LICENSE.txt) for the full license and exception text.
