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
package greenfoot.guifx.controller;

import bluej.pkgmgr.Project;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * An IDE main window, as the {@link ProjectRegistry} opens, reuses and closes it:
 * the Classic Greenfoot IDE window or the SuperGreenfoot IDE window.  A window shows
 * one project, or none (an empty window, offering to open or create a scenario).
 */
@OnThread(Tag.FXPlatform)
public interface IdeWindow extends ProjectView
{
    /**
     * Which IDE this window belongs to.
     */
    UiMode getUiMode();

    /**
     * The controller of the project shown, or null if the window is empty.
     */
    GreenfootProjectController getController();

    /**
     * The project shown, or null if the window is empty.
     */
    Project getProject();

    /**
     * Show a project in this (empty) window.  The project may be newly opened, or
     * live (moving here from another window), in which case the controller brings
     * this window up to date.
     */
    void showProject(GreenfootProjectController controller);

    /**
     * The project has been closed: show this window as empty.
     */
    void showNoProject();

    /**
     * The project is moving to another window: stop showing it here, without closing it.
     */
    void detachProject();

    /**
     * Save any settings the window keeps for itself (outside the project file), for
     * example before it closes.  Does nothing if the window keeps none.
     */
    default void saveWindowSettings()
    {
    }
}
