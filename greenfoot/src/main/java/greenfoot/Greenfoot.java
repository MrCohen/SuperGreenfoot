/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2011,2012,2014,2015,2019,2022,2026  Poul Henriksen and Michael Kolling 
 
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
package greenfoot;

import java.util.Random;

import greenfoot.core.Simulation;
import greenfoot.core.WorldHandler;
import greenfoot.sound.MicLevelGrabber;
import greenfoot.sound.Sound;
import greenfoot.sound.SoundFactory;
import greenfoot.util.GreenfootUtil;


/**
 * This utility class provides methods to control the simulation
 * and interact with the system.
 * 
 * <h2>Key names</h2>
 * 
 * <p>Part of the functionality provided by this class is the ability to
 * retrieve keyboard input. The methods getKey() and isKeyDown() are used
 * for this and they return/understand the following key names:
 * 
 * <ul>
 * <li>"a", "b", .., "z" (alphabetical keys), "0".."9" (digits), most
 *     punctuation marks. getKey() also returns uppercase characters when
 *     appropriate.
 * <li>"up", "down", "left", "right" (the cursor keys)
 * <li>"enter", "space", "tab", "escape", "backspace", "shift", "control"
 * <li>"F1", "F2", .., "F12" (the function keys)
 * </ul>
 * 
 * @author Davin McCall
 * @version 2.7
 */
public class Greenfoot
{

    private static Random randomGenerator = new Random();

    /**
     * Sets the World to run to the one given.
     * This World will now be the main World that Greenfoot runs with on the
     * next act.
     *
     * @param world The World to switch running to, cannot be null.
     */
    public static void setWorld(World world)
    {
        if ( world == null ) {
            throw new NullPointerException("The given world cannot be null.");
        }

        WorldHandler.getInstance().setWorld(world, true);
    }

    /**
     * Get the most recently pressed key, since the last time this method was
     * called. If no key was pressed since this method was last called, it
     * will return null. If more than one key was pressed, this returns only
     * the most recently pressed key.
     * 
     * @return  The name of the most recently pressed key
     */
    public static String getKey()
    {
        return WorldHandler.getInstance().getKeyboardManager().getKey();
    }
    
    /**
     * Check whether a given key is currently pressed down.
     * 
     * @param keyName  The name of the key to check
     * @return         True if the key is down
     */
    public static boolean isKeyDown(String keyName)
    {
        return WorldHandler.getInstance().getKeyboardManager().isKeyDown(keyName);
    }
    
    /**
     * Delay the current execution by a number of time steps. 
     * The size of one time step is defined by the Greenfoot environment (the speed slider).
     * 
     * @param time  The number of steps the delay will last.
     * @see #setSpeed(int)
     */
    public static void delay(int time)
    {
        Simulation.getInstance().sleep(time);
    }
    
    /**
     * Set the speed of the execution.
     *  
     * @param speed  The new speed. the value must be in the range (1..100)
     */
    public static void setSpeed(int speed)
    {
        Simulation.getInstance().setSpeed(speed);
    }
    
    /**
     * Pause the execution.
     */
    public static void stop()
    {
        Simulation.getInstance().setPaused(true);
    }
    
    /**
     * Run (or resume) the execution.
     */
    public static void start()
    {
        Simulation.getInstance().setPaused(false);
    }
    
    /**
     * Return a random number between 0 (inclusive) and limit (exclusive).
     * 
     * @param limit  An upper limit which the returned random number will be smaller than.
     * @return A random number within 0 to (limit-1) range.
     */
    public static int getRandomNumber(int limit)
    {
        return randomGenerator.nextInt(limit);
    }

    /**
     * Play sound from a file. The following formats are supported: AIFF, AU and
     * WAV.
     * <p>
     * The file name may be an absolute path, a base name for a file located in
     * the project directory or in the sounds directory of the project
     * directory.
     * 
     * @param soundFile Typically the name of a file in the sounds directory in
     *            the project directory.
     * @throws IllegalArgumentException If the sound can not be loaded.
     */
    public static void playSound(final String soundFile)
    {
        // SuperGreenfoot: play through the mixer so repeated calls overlap
        // and the clip is decoded only once.
        Sounds.play(soundFile);
    }


    /**
     * True if the mouse has been pressed (changed from a non-pressed state to
     * being pressed) on the given object. If the parameter is an Actor the
     * method will only return true if the mouse has been pressed on the given
     * actor. If there are several actors at the same place, only the top most
     * actor will receive the press. 
     * If the parameter is a World then true will be returned if the mouse was
     * pressed on the world background. If the parameter is null,
     * then true will be returned for any mouse press, independent of the target 
     * pressed on.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been pressed as explained above
     */
    public static boolean mousePressed(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMousePressed(obj);
    }

    /**
     * True if the mouse has been released (changed from pressed to non-pressed) on the given
     * object. If the parameter is an Actor the method will only return true if
     * the mouse has been clicked on the given actor. If there are several
     * actors at the same place, only the top most actor will receive the click.
     * If the parameter is a World then true will be returned if the mouse was
     * clicked on the world background. If the parameter is null,
     * then true will be returned for any click, independent of the target 
     * clicked on.  Note that mouseClicked does not require the press to have
     * occurred on the parameter object, just that the release did.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been clicked as explained above
     */
    public static boolean mouseClicked(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMouseClicked(obj);
    }

    /**
     * True if the mouse is currently being dragged on the given object. The mouse is
     * considered to be dragged on an object if the drag started on that
     * object - even if the mouse has since been moved outside of that object.
     * <p>
     * If the parameter is an Actor the method will only return true if the drag
     * started on the given actor. If there are several actors at the same
     * place, only the top most actor will receive the drag. 
     * If the parameter is a World then true will be returned if the drag action
     * was started on the world background. If the parameter is null,
     * then true will be returned for any drag action, independent of the target 
     * clicked on.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been dragged as explained above
     */
    public static boolean mouseDragged(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMouseDragged(obj);
    }

    /**
     * True if a mouse drag has ended. This happens when the mouse has been
     * dragged and the mouse button released.
     * <p>
     * If the parameter is an Actor the method will only return true if the drag
     * started on the given actor. If there are several actors at the same
     * place, only the top most actor will receive the drag. 
     * If the parameter is a World then true will be returned if the drag action
     * was started on the world background. If the parameter is null,
     * then true will be returned for any drag action, independent of the target 
     * clicked on.
     * 
     * @param obj
     *            Typically one of Actor, World or null
     * @return True if the mouse has been dragged as explained above
     */
    public static boolean mouseDragEnded(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMouseDragEnded(obj);
    }

    /**
     * True if the mouse has been moved on the given object. The mouse is
     * considered to be moved on an object if the mouse pointer is above
     * that object.
     * <p>
     * If the parameter is an Actor the method will only return true if the move
     * is on the given actor. If there are several actors at the same
     * place, only the top most actor will receive the move. 
     * If the parameter is a World then true will be returned if the move
     * was on the world background. If the parameter is null,
     * then true will be returned for any move, independent of the target 
     * under the move location.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been moved as explained above
     */
    public static boolean mouseMoved(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMouseMoved(obj);
    }

    /**
     * True if the mouse wheel was turned (or a trackpad scrolled) while the mouse
     * was over the given object during this act. The amount and direction come from
     * {@link MouseInfo#getScrollAmount()}.
     *
     * @param obj Typically one of Actor, World or null. If null, it will return true
     *            if the wheel was used anywhere. If World, it will return true if
     *            the wheel was used over the world background (not over an actor).
     *            If an Actor, it will return true if the wheel was used over that actor.
     * @return True if the mouse wheel was used as explained above.
     * @since SuperGreenfoot 0.2.0
     */
    public static boolean mouseScrolled(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMouseScrolled(obj);
    }

    /**
     * True if a mouse button was released (changed from pressed to non-pressed) over
     * the given object during this act.
     * <p>
     * This reports what the mouse pointer was over at the moment the button went up,
     * which is not necessarily where the press began. {@link #mouseDragEnded(Object)}
     * is the other way round: it reports where the drag started.
     * <p>
     * A release is always reported for the act it happened in, even when a click or a
     * drag ending was recorded in the same act. Use {@link MouseInfo#getButton()} on
     * {@link #getMouseInfo()} to find out which button it was, unless another event in
     * the same act has claimed the mouse info.
     *
     * @param obj Typically one of Actor, World or null. If null, it will return true
     *            if a button was released anywhere in the world. If World, it will
     *            return true only if the release was over the world background and not
     *            over an actor. If an Actor, only if it was over that actor.
     * @return True if a mouse button was released as explained above
     * @since SuperGreenfoot 0.2.0
     */
    public static boolean mouseReleased(Object obj)
    {
        return WorldHandler.getInstance().getMouseManager().isMouseReleased(obj);
    }

    /**
     * True while the given mouse button is being held down.
     * <p>
     * Unlike the other mouse methods, this is a state rather than something that
     * happened during the act: it stays true for every act between the press and the
     * release, which is what a game needs for "hold the right button to block".
     * The answer is taken once at the start of each act, so it cannot change half way
     * through one.
     * <p>
     * A button is no longer counted as held when the scenario starts running, or when
     * the world loses keyboard focus, since the release may then never arrive.
     *
     * <pre>
     * if (Greenfoot.isMouseButtonDown(MouseInfo.RIGHT)) {
     *     block();
     * }
     * </pre>
     *
     * @param button {@link MouseInfo#LEFT}, {@link MouseInfo#MIDDLE} or
     *               {@link MouseInfo#RIGHT} (1, 2 or 3, matching
     *               {@link MouseInfo#getButton()})
     * @return True if that button is down right now
     * @throws IllegalArgumentException if the button number is not 1, 2 or 3
     * @since SuperGreenfoot 0.2.0
     */
    public static boolean isMouseButtonDown(int button)
    {
        if (button < 1 || button > 3)
        {
            throw new IllegalArgumentException("Mouse button must be 1 (left), 2 (middle)"
                    + " or 3 (right), not " + button);
        }
        return WorldHandler.getInstance().getMouseManager().isMouseButtonDown(button);
    }

    /**
     * Return a mouse info object with information about the state of the
     * mouse.
     * 
     * @return The info about the current state of the mouse, or null if the mouse
     *         cursor is outside the world boundary (unless being dragged).
     */
    public static MouseInfo getMouseInfo()
    {
        return WorldHandler.getInstance().getMouseManager().getMouseInfo();
    }
    
    /**
     * Get the microphone input level. This level is an approximation of the loudness
     * any noise that is currently being received by the microphone.
     * 
     * @return The microphone input level (between 0 and 100, inclusive).
     */
    public static int getMicLevel()
    {
        return MicLevelGrabber.getInstance().getLevel();
    }
    
    /**
     * Get input from the user (and freeze the scenario while we are waiting).
     * The prompt String parameter will be shown to the user (e.g. "How many players?"), and the answer will be returned as a String.
     * If you want to ask for a number, you can use methods like <code>Integer.parseInt</code> to turn
     * the returned String into a number.
     * <p>
     * This method can only be used when a world is in place and the scenario is running.
     * It returns null if that is not the case, or if the scenario is reset while the prompt
     * is being shown.
     *
     * @param prompt The prompt to show to the user.
     * @return The string that the user typed in.
     */
    public static String ask(String prompt)
    {
        return WorldHandler.getInstance().ask(prompt);
    }

    // ==================================
    //
    // SuperGreenfoot: presentation
    //
    // ==================================

    /**
     * Show the scenario full screen, or return to a window. Works in the
     * exported game and inside the IDE (where it opens or closes the
     * full-screen view). Combine with {@link #setScaleMode(ScaleMode)} to choose
     * crisp whole-number scaling or smooth fill, and with
     * {@link #getScreenWidth()} / {@link #getScreenHeight()} to pick a world size
     * that fills the screen exactly. Players can always leave full screen with
     * Shortcut+Shift+F.
     *
     * @param fullScreen true for full screen, false for a window.
     * @since SuperGreenfoot 0.1.0
     */
    public static void setFullScreen(boolean fullScreen)
    {
        GreenfootUtil.getDisplayDelegate().setFullScreen(fullScreen);
    }

    /**
     * @return true if the scenario is currently shown full screen.
     * @since SuperGreenfoot 0.1.0
     */
    public static boolean isFullScreen()
    {
        return GreenfootUtil.getDisplayDelegate().isFullScreen();
    }

    /**
     * @return true if this environment can show the scenario full screen
     *         (the exported game and the IDE can; a web page may not).
     * @since SuperGreenfoot 0.1.0
     */
    public static boolean isFullScreenSupported()
    {
        return GreenfootUtil.getDisplayDelegate().isFullScreenSupported();
    }

    /**
     * Choose how the world is scaled up when it is shown larger than its own
     * size (full screen, or an enlarged window). {@link ScaleMode#PIXEL_PERFECT}
     * uses whole-number factors and crisp pixels; {@link ScaleMode#SMOOTH}
     * fills the screen with interpolation. The setting is remembered until
     * changed, also across entering and leaving full screen.
     *
     * @param mode the scaling mode.
     * @since SuperGreenfoot 0.1.0
     */
    public static void setScaleMode(ScaleMode mode)
    {
        if (mode != null)
        {
            GreenfootUtil.getDisplayDelegate().setScaleMode(mode);
        }
    }

    /**
     * @return the current scaling mode.
     * @since SuperGreenfoot 0.1.0
     */
    public static ScaleMode getScaleMode()
    {
        return GreenfootUtil.getDisplayDelegate().getScaleMode();
    }

    /**
     * The factor by which the world is currently enlarged on screen: 1.0 in a
     * normal window, 3.0 for a 640x360 world shown pixel-perfect on a 1080p
     * screen, a fraction such as 2.25 when scaled smoothly.
     *
     * @return the current world-to-screen scale factor.
     * @since SuperGreenfoot 0.1.0
     */
    public static double getDisplayScale()
    {
        return GreenfootUtil.getDisplayDelegate().getDisplayScale();
    }

    /**
     * The width of the screen the scenario is shown on, in the same logical
     * pixels a world is measured in (so on a "Retina" display this is the
     * scaled size, e.g. 1512, not the physical 3024). Use it before creating
     * a world to pick a size that fills the screen at a whole-number scale:
     * for a 1920x1080 screen, 640x360 fills it at 3x and 960x540 at 2x.
     *
     * @return the screen width in pixels, or 0 if it is not known.
     * @since SuperGreenfoot 0.1.0
     */
    public static int getScreenWidth()
    {
        return GreenfootUtil.getDisplayDelegate().getScreenWidth();
    }

    /**
     * The height of the screen the scenario is shown on, in logical pixels.
     *
     * @return the screen height in pixels, or 0 if it is not known.
     * @see #getScreenWidth()
     * @since SuperGreenfoot 0.1.0
     */
    public static int getScreenHeight()
    {
        return GreenfootUtil.getDisplayDelegate().getScreenHeight();
    }

    /**
     * Show or hide the run controls (act, run/pause, reset, speed). A finished
     * game usually hides them. In the exported game this affects the control
     * bar; in the IDE it affects the floating bar of the full-screen view (the
     * main IDE window keeps its controls). Players can bring hidden controls
     * back with Escape unless they are locked.
     *
     * @param visible true to show the controls.
     * @since SuperGreenfoot 0.1.0
     */
    public static void setControlsVisible(boolean visible)
    {
        GreenfootUtil.getDisplayDelegate().setControlsVisible(visible);
    }

    /**
     * @return true if the run controls are currently shown.
     * @since SuperGreenfoot 0.1.0
     */
    public static boolean isControlsVisible()
    {
        return GreenfootUtil.getDisplayDelegate().isControlsVisible();
    }

    /**
     * Lock the run controls hidden so that Escape does not reveal them (a
     * teacher can still hold Escape for two seconds). Applies to the exported
     * game and to the IDE full-screen view.
     *
     * @param locked true to lock the controls hidden.
     * @since SuperGreenfoot 0.1.0
     */
    public static void setControlsLocked(boolean locked)
    {
        GreenfootUtil.getDisplayDelegate().setControlsLocked(locked);
    }

    /**
     * @return true if the run controls are locked hidden.
     * @since SuperGreenfoot 0.1.0
     */
    public static boolean isControlsLocked()
    {
        return GreenfootUtil.getDisplayDelegate().isControlsLocked();
    }

    /**
     * In the exported game, make the window show the world enlarged (or
     * reduced) by the given factor without going full screen, e.g. 2.0 to
     * show a 640x360 world in a 1280x720 window. Whole-number factors look
     * crisp with {@link ScaleMode#PIXEL_PERFECT}. No effect in the IDE.
     *
     * @param scale the factor, between 0.25 and 8.
     * @since SuperGreenfoot 0.1.0
     */
    public static void setWindowScale(double scale)
    {
        GreenfootUtil.getDisplayDelegate().setWindowScale(scale);
    }

    /**
     * @return the window scale factor set with {@link #setWindowScale(double)} (1.0 by default).
     * @since SuperGreenfoot 0.1.0
     */
    public static double getWindowScale()
    {
        return GreenfootUtil.getDisplayDelegate().getWindowScale();
    }

    /**
     * @return true when running as an exported standalone game rather than in the IDE.
     * @since SuperGreenfoot 0.1.0
     */
    public static boolean isStandalone()
    {
        return GreenfootUtil.getDisplayDelegate().isStandalone();
    }

    /**
     * Hide the mouse cursor while it is over the world, or show it again. Use
     * it in a game that aims with the mouse and draws its own crosshair, or
     * that replaces the cursor with an actor which follows
     * {@link #getMouseInfo()}. The cursor is only hidden while the scenario
     * is running: it comes back when the scenario is paused, when the mouse
     * leaves the world, and in the full-screen view when the controls are
     * shown with Escape. The setting is forgotten when the world is reset.
     * To replace the cursor with a picture instead, see {@link #setCursor(GreenfootImage)}.
     *
     * @param visible false to hide the cursor over the world, true to show it.
     * @since SuperGreenfoot 0.2.0
     */
    public static void setCursorVisible(boolean visible)
    {
        GreenfootUtil.requestCursorVisible(visible);
    }

    /**
     * @return false if the cursor was hidden with {@link #setCursorVisible(boolean)}.
     * @since SuperGreenfoot 0.2.0
     */
    public static boolean isCursorVisible()
    {
        return GreenfootUtil.isCursorVisibleRequested();
    }

    /** The largest cursor picture accepted, in pixels each way. */
    private static final int MAX_CURSOR_SIZE = 256;

    /**
     * Replace the mouse cursor over the world with an image from the
     * scenario's images folder, for example a crosshair. The system draws the
     * image at the mouse position with no lag, and its centre is the point
     * that clicks happen at. Windows shows custom cursors at 32x32 pixels, so
     * keep the image about that size. The cursor is restored when the world is
     * reset, or by calling {@code setCursor((String) null)}.
     *
     * @param imageName the file name of the image in the images folder, or null
     *                  for the normal cursor.
     * @throws IllegalArgumentException if the image cannot be found.
     * @since SuperGreenfoot 0.2.0
     */
    public static void setCursor(String imageName)
    {
        setCursor(imageName, -1, -1);
    }

    /**
     * Replace the mouse cursor over the world with an image from the images
     * folder, choosing which of its pixels is the point that clicks happen at
     * (the "hot spot"): (0, 0) for an arrow whose tip is the top-left corner,
     * for example.
     *
     * @param imageName the file name of the image in the images folder, or null
     *                  for the normal cursor.
     * @param hotSpotX  the x coordinate of the hot spot within the image, or -1 for the centre.
     * @param hotSpotY  the y coordinate of the hot spot within the image, or -1 for the centre.
     * @throws IllegalArgumentException if the image cannot be found, or the hot
     *         spot is outside it.
     * @see #setCursor(GreenfootImage, int, int)
     * @since SuperGreenfoot 0.2.0
     */
    public static void setCursor(String imageName, int hotSpotX, int hotSpotY)
    {
        setCursor(imageName == null ? null : new GreenfootImage(imageName), hotSpotX, hotSpotY);
    }

    /**
     * Replace the mouse cursor over the world with a picture you drew or
     * loaded, with its centre as the point that clicks happen at. Unlike an
     * actor that follows the mouse, the system draws it with no lag. The
     * picture is copied when you call this, so drawing on it afterwards
     * changes nothing until you call again; calling again with the same
     * picture costs nothing. The cursor is restored when the world is reset,
     * or by calling {@code setCursor((GreenfootImage) null)}.
     *
     * @param image  the picture, at most 256 pixels each way (Windows shows
     *               custom cursors at 32x32, so keep it about that size), or
     *               null for the normal cursor.
     * @throws IllegalArgumentException if the picture is larger than 256 pixels each way.
     * @since SuperGreenfoot 0.2.0
     */
    public static void setCursor(GreenfootImage image)
    {
        setCursor(image, -1, -1);
    }

    /**
     * Replace the mouse cursor over the world with a picture, choosing which
     * of its pixels is the point that clicks happen at (the "hot spot"):
     * (0, 0) for an arrow whose tip is the top-left corner, for example.
     *
     * @param image     the picture, at most 256 pixels each way, or null for the normal cursor.
     * @param hotSpotX  the x coordinate of the hot spot within the picture, or -1 for the centre.
     * @param hotSpotY  the y coordinate of the hot spot within the picture, or -1 for the centre.
     * @throws IllegalArgumentException if the picture is larger than 256 pixels
     *         each way, or the hot spot is outside it.
     * @see #setCursor(GreenfootImage)
     * @since SuperGreenfoot 0.2.0
     */
    public static void setCursor(GreenfootImage image, int hotSpotX, int hotSpotY)
    {
        if (image == null)
        {
            GreenfootUtil.requestCursor(null, 0, 0, -1, -1);
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > MAX_CURSOR_SIZE || h > MAX_CURSOR_SIZE)
        {
            throw new IllegalArgumentException("A cursor picture is at most " + MAX_CURSOR_SIZE
                    + " pixels each way; this one is " + w + "x" + h);
        }
        if (hotSpotX < 0 || hotSpotY < 0)
        {
            hotSpotX = -1;
            hotSpotY = -1;
        }
        else if (hotSpotX >= w || hotSpotY >= h)
        {
            throw new IllegalArgumentException("The hot spot (" + hotSpotX + ", " + hotSpotY
                    + ") is outside the " + w + "x" + h + " cursor picture");
        }
        int[] argb = image.getAwtImage().getRGB(0, 0, w, h, null, 0, w);
        GreenfootUtil.requestCursor(argb, w, h, hotSpotX, hotSpotY);
    }
}
