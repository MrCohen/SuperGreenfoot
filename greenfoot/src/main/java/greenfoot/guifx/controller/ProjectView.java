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

import bluej.utility.javafx.FXPlatformConsumer;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A window that shows one open project, driven by that project's
 * {@link GreenfootProjectController}.  The Classic Greenfoot IDE window
 * (GreenfootStage) is one; the new SuperGreenfoot IDE window will be another.
 *
 * <p>The full-screen display callbacks still reach the view unchanged, through
 * the controller, until full screen moves into the controller.
 */
@OnThread(Tag.FXPlatform)
public interface ProjectView
{
    // Debug VM callbacks, passed on unchanged by the controller for now
    // (see VMCommsMain.CommsListener):

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
     * Showing the next world image removes the grey-out.
     */
    void greyOutWorld();

    /**
     * A world image of a new size is about to be shown: make room for it if need be.
     */
    void worldImageSizeChanged(double width, double height);

    /**
     * Show a world image (this also removes any grey-out).
     */
    void showWorldImage(Image image);

    /**
     * Show a Greenfoot.ask prompt over the (greyed-out) world, and call onAnswer once the
     * user answers, after hiding the prompt.  This is called repeatedly while the prompt is
     * pending, and must not disturb a prompt that is already showing.
     */
    void showAsk(String prompt, FXPlatformConsumer<String> onAnswer);

    /**
     * Hide any ask prompt (and remove the grey-out that came with it).  This is called
     * often, whether or not a prompt is showing.
     */
    void hideAsk();

    /**
     * Show or hide the execution twirler (user code has been running a long time).
     */
    void setExecutionTwirling(boolean twirling);

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
