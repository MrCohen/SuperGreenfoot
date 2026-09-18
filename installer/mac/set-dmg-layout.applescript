-- Finder stores the background and icon placement in the mounted disk's .DS_Store.
-- The coordinates match assets/dmg-background.png: 660 x 460 points of artwork
-- plus a 32-point strip of plain footer colour below it (660 x 492 in total).
--
-- Finder's bounds are the whole window frame, so the height adds the title bar
-- (32 points on macOS 26 and later) and the path bar (28 points). The path bar
-- follows each viewer's global Finder setting and is not saved in the disk image
-- (never set "pathbar visible" here: it changes the builder's own Finder), so the
-- window leaves room for it. Without a path bar, or with the shorter title bar of
-- older macOS, the extra space shows the footer strip instead of Finder's blank
-- background.
on run argv
    set volumeName to item 1 of argv
    set backgroundPath to item 2 of argv
    set artworkHeight to 460
    set titleBarHeight to 32
    set pathBarHeight to 28

    tell application "Finder"
        tell disk volumeName
            open
            set dmgWindow to container window
            set current view of dmgWindow to icon view
            set toolbar visible of dmgWindow to false
            set statusbar visible of dmgWindow to false
            set bounds of dmgWindow to {120, 120, 780, 120 + artworkHeight + titleBarHeight + pathBarHeight}

            set viewOptions to icon view options of dmgWindow
            set arrangement of viewOptions to not arranged
            set icon size of viewOptions to 96
            set text size of viewOptions to 13
            set background picture of viewOptions to POSIX file backgroundPath

            -- Icon and label together cover y-12 to y+99; y = 290 centres them between the
            -- card headers (text ends at 268) and the cards' inner bottom edge (398).
            -- x is the icon centre; the cards are centred on 152.5 and 507.5.
            set position of item "SuperGreenfoot.app" of dmgWindow to {153, 290}
            set position of item "Applications" of dmgWindow to {508, 290}
            -- .background is hidden, but a viewer who shows hidden files (Command-Shift-.)
            -- would see it on the artwork: park it beyond the right edge instead.
            try
                set position of item ".background" of dmgWindow to {900, 60}
            end try
            close dmgWindow
            open
        end tell
    end tell
    delay 1
end run
