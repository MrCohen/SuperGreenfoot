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

import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.nio.IntBuffer;

/**
 * A window that shows one open project, driven by that project's
 * {@link GreenfootProjectController}.  The Classic Greenfoot IDE window
 * (GreenfootStage) is one; the new SuperGreenfoot IDE window will be another.
 *
 * <p>Some of the debug VM's callbacks still reach the view unchanged, through
 * the controller; they are replaced by view-level operations as the rest of
 * the window's logic moves into the controller.
 */
@OnThread(Tag.FXPlatform)
public interface ProjectView
{
    // Debug VM callbacks, passed on unchanged by the controller for now
    // (see VMCommsMain.CommsListener):

    void receivedWorldImage(int width, int height, IntBuffer buffer);

    void bringTerminalToFront();

    void receivedAsk(int askId, int[] promptCodepoints);

    void cancelAsk();

    void setLastUserExecutionStartTime(long lastExecStartTime, boolean delayLoop);

    void sendDisplayState();

    void receivedDisplayRequest(int seq, int flags);

    // What the controller asks of the window:

    /**
     * The window, as the owner for dialogs.
     */
    Stage getWindow();

    /**
     * Whether the window has keyboard focus.
     */
    boolean isWindowFocused();

    /**
     * Show a new simulation state (enable and disable the controls to suit).
     */
    void stateChanged(SimulationState state, boolean atBreakpoint);

    /**
     * Something the message shown in place of the world depends on has changed.
     */
    void worldStatusChanged();

    /**
     * Show or hide the world (when hidden, a message is shown in its place).
     */
    void setWorldVisible(boolean visible);

    /**
     * Grey out the world while it is out of date (compiling, or being replaced).
     */
    void greyOutWorld();

    /**
     * Whether a Greenfoot.ask prompt is showing.
     */
    boolean isWorldAsking();

    /**
     * Whether the world is greyed out.
     */
    boolean isWorldGreyedOut();

    /**
     * Give the world keyboard focus.
     */
    void requestWorldFocus();

    /**
     * Remove any debugger highlight of an actor.
     */
    void clearActorHighlight();

    /**
     * Show the simulation speed on the speed slider.
     *
     * @param speed              the speed
     * @param includeFullScreen  whether to update the full-screen controls as well
     */
    void showSpeed(int speed, boolean includeFullScreen);

    /**
     * The project's classes have been recompiled; refresh how they are shown.
     */
    void classesChanged();

    /**
     * The debug VM has terminated: clear the world, any ask prompt and anything
     * else tied to the old VM (a new VM is starting).
     */
    void vmTerminated();
}
