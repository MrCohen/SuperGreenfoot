-- Finder stores the background and icon placement in the mounted disk's .DS_Store.
-- The coordinates match assets/dmg-background.png (660 x 460 points).
on run argv
    set volumeName to item 1 of argv
    set backgroundPath to item 2 of argv

    tell application "Finder"
        tell disk volumeName
            open
            set dmgWindow to container window
            set current view of dmgWindow to icon view
            set toolbar visible of dmgWindow to false
            set statusbar visible of dmgWindow to false
            set bounds of dmgWindow to {120, 120, 780, 580}

            set viewOptions to icon view options of dmgWindow
            set arrangement of viewOptions to not arranged
            set icon size of viewOptions to 96
            set text size of viewOptions to 13
            set background picture of viewOptions to POSIX file backgroundPath

            set position of item "SuperGreenfoot.app" of dmgWindow to {155, 315}
            set position of item "Applications" of dmgWindow to {505, 315}
            close dmgWindow
            open
        end tell
    end tell
    delay 1
end run
