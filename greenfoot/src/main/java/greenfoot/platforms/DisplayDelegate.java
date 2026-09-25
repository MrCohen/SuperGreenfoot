/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2026 SuperGreenfoot contributors

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 This file is subject to the Classpath exception as provided in the
 LICENSE.txt file that accompanied this code.
 */
package greenfoot.platforms;

import greenfoot.ScaleMode;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Hooks through which scenario code controls how the world is presented: full
 * screen, the scaling mode, the run controls, the window size, and what it can
 * find out about the screen. The standalone player implements these directly
 * on its window; inside the IDE they are relayed to the IDE window through the
 * inter-VM channel. {@link #NONE} (tests, or before a host installs a delegate)
 * ignores every request and reports no screen.
 */
@OnThread(Tag.Any)
public interface DisplayDelegate
{
    /** A delegate that ignores every request (tests). */
    DisplayDelegate NONE = new DisplayDelegate() {};

    default void setFullScreen(boolean fullScreen) {}

    default boolean isFullScreen()
    {
        return false;
    }

    /** Whether this host can show the scenario full screen at all. */
    default boolean isFullScreenSupported()
    {
        return false;
    }

    default void setControlsVisible(boolean visible) {}

    default boolean isControlsVisible()
    {
        return true;
    }

    default void setControlsLocked(boolean locked) {}

    default boolean isControlsLocked()
    {
        return false;
    }

    default void setScaleMode(ScaleMode mode) {}

    default ScaleMode getScaleMode()
    {
        return ScaleMode.SMOOTH;
    }

    /** The factor the world image is currently shown at (1.0 when unscaled). */
    default double getDisplayScale()
    {
        return 1.0;
    }

    /** Logical width in pixels of the screen the scenario is shown on, or 0 if unknown. */
    default int getScreenWidth()
    {
        return 0;
    }

    /** Logical height in pixels of the screen the scenario is shown on, or 0 if unknown. */
    default int getScreenHeight()
    {
        return 0;
    }

    /** Enlarge or shrink the window that shows the world (player only). */
    default void setWindowScale(double scale) {}

    default double getWindowScale()
    {
        return 1.0;
    }

    /** Whether the scenario is running in the standalone player rather than the IDE. */
    default boolean isStandalone()
    {
        return false;
    }

    /** Show or hide the mouse cursor over the world while the scenario runs. */
    default void setCursorVisible(boolean visible) {}

    default boolean isCursorVisible()
    {
        return true;
    }

    /**
     * Replace the mouse cursor over the world with a picture, or restore the
     * normal cursor. The picture arrives as pixels, so the IDE and the player
     * never look a file up themselves: {@code Greenfoot.setCursor(String)} has
     * already loaded it, the way any GreenfootImage is loaded.
     *
     * @param argb       the pixels, row by row (ARGB ints), or null for the normal cursor
     * @param width      the picture's width in pixels
     * @param height     the picture's height in pixels
     * @param hotSpotX   the x of the pixel that is the cursor's point, or -1 for the centre
     * @param hotSpotY   the y of the pixel that is the cursor's point, or -1 for the centre
     */
    default void setCursor(int[] argb, int width, int height, int hotSpotX, int hotSpotY) {}
}
