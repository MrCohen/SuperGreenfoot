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

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Hooks through which scenario code can control how the world is presented:
 * full screen and the visibility of the run controls. The standalone player
 * implements these; inside the IDE they are no-ops (the IDE has its own
 * Full Screen menu command) so the same scenario runs in both.
 */
@OnThread(Tag.Any)
public interface DisplayDelegate
{
    /** A delegate that ignores every request (IDE, tests). */
    DisplayDelegate NONE = new DisplayDelegate() {};

    default void setFullScreen(boolean fullScreen) {}

    default boolean isFullScreen()
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

    /** Whether the scenario is running in the standalone player rather than the IDE. */
    default boolean isStandalone()
    {
        return false;
    }
}
