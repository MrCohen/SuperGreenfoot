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

import bluej.compiler.CompileType;
import bluej.compiler.Diagnostic;
import bluej.debugger.DebuggerObject;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.Reflective;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.javafx.FXPlatformConsumer;
import javafx.geometry.Point2D;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * A window that shows one open project, driven by that project's
 * {@link GreenfootProjectController}.  The Classic Greenfoot IDE window
 * (GreenfootStage) is one; the new SuperGreenfoot IDE window will be another.
 *
 * <p>The controller holds all the state; the window shows what it is told and
 * passes the user's actions (and its world view's input) back to the controller.
 */
@OnThread(Tag.FXPlatform)
public interface ProjectView
{

    /**
     * The window, as the owner for dialogs.
     */
    Stage getWindow();

    /**
     * Whether the window has keyboard focus.
     */
    boolean isWindowFocused();

    /**
     * Add this window's own settings (such as its position and size) to the
     * properties being saved to the project file.
     */
    void writeViewProperties(Properties p);

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
     * Highlight an actor in the world (while the debugger shows it).
     *
     * @param x The centre X coordinate (in pixels, in the world)
     * @param y The centre Y coordinate (in pixels, in the world)
     * @param width The width of the image (in pixels)
     * @param height The height of the image (in pixels)
     * @param rotation The rotation of the actor (in degrees)
     */
    void showActorHighlight(int x, int y, int width, int height, int rotation);

    /**
     * Convert a point in world pixel coordinates to screen coordinates.
     */
    Point2D worldToScreen(Point2D worldPos);

    /**
     * Show a context menu with the given items on the world, at the given
     * position (in world pixel coordinates), replacing any showing already.
     */
    void showWorldContextMenu(List<MenuItem> items, Point2D worldPos);

    /**
     * Hide the world's context menu, if one is showing.
     */
    void hideWorldContextMenu();

    /**
     * Whether the user is placing a new actor into the world (so mouse events
     * on the world are not passed to the scenario).
     */
    boolean isPlacingActor();

    /**
     * An actor has been constructed interactively: let the user place it into the
     * world (by clicking), then call the controller's placeNewActor.
     *
     * @param actor       The new actor
     * @param ir          The invoker record for its construction
     * @param paramTypes  The parameter types of the constructor call
     * @param type        The actor's class
     */
    void beginPlacingActor(DebuggerObject actor, InvokerRecord ir, JavaType[] paramTypes, Reflective type);

    /**
     * Show the simulation speed on the speed slider.
     */
    void showSpeed(int speed);

    /**
     * The project's classes have been recompiled; refresh how they are shown.
     */
    void classesChanged();

    /**
     * The debug VM has terminated: clear the world, any ask prompt and anything
     * else tied to the old VM (a new VM is starting).
     */
    void vmTerminated();

    /**
     * The project's classes have started compiling.  (The Classic window shows this
     * on the classes themselves, so it does nothing here.)
     */
    default void compileStarted()
    {
    }

    /**
     * The project's classes have finished compiling.
     *
     * @param successful  whether the compilation succeeded
     */
    default void compileFinished(boolean successful)
    {
    }

    /**
     * These source files are about to be compiled, so any problems reported for them
     * earlier are out of date.  (The Classic window shows problems in the editors, so
     * it does nothing here.)
     */
    default void compilingFiles(List<File> sourceFiles)
    {
    }

    /**
     * The compiler reported an error or warning.
     */
    default void compilerMessage(Diagnostic diagnostic, CompileType type)
    {
    }

    /**
     * The user left-clicked an actor in the world while the scenario was paused, or
     * the world's background (actor is null).  The Classic window does nothing here
     * (the controller passes clicked actors to the debugger's object selection).
     */
    default void actorClicked(DebuggerObject actor)
    {
    }
}
