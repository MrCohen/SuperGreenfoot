/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2011,2012,2013,2015,2018  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.gui.input;

import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Manage keyboard input, to allow Greenfoot programs to poll for
 * keystrokes. Keystrokes will be delivered on the GUI event thread,
 * but may be polled from another thread.
 * 
 * <p>The following key names are recognized:
 * up, down, left, right (cursor keys); enter, space, tab, escape, backspace,
 * F1-F12.
 * 
 * @author Davin McCall
 */
public class KeyboardManager
{
    // The last key typed, returned by Greenfoot.getKey()
    private String lastKeyTyped;

    // The keys which are latched are those which have been pressed and released in this frame
    // but we want to remember that they were pressed briefly, so that Greenfoot.isKeyDown
    // still returns true for their press, in the case that the frame rate is low and the key
    // was pressed within a frame.  Cleared by clearLatchedKeys() after each frame for
    // those keys which are now released.
    private final Set<String> keyLatched = new HashSet<>();
    // Those keys which are actually pressed down right now.
    private final Set<String> keyDown = new HashSet<>();

    /** Do we think that a numlock key is present? */
    
    /**
     * Constructor for a KeyboardManager. Key events must be delivered
     * from an external source.
     */
    public KeyboardManager()
    {        
    }
    
    /**
     * Clear the latched state of keys which were down, but are no longer
     * down.
     */
    @OnThread(Tag.Simulation)
    public synchronized void clearLatchedKeys()
    {
        for (Iterator<String> i = keyLatched.iterator(); i.hasNext(); )
        {
            String keyCode = i.next();
            if (!keyDown.contains(keyCode))
            {
                i.remove();
            }
        }
    }
        
    /**
     * Get the last key pressed, as a String key name identifying the key.
     */
    @OnThread(Tag.Simulation)
    public synchronized String getKey()
    {
        String r = lastKeyTyped;
        lastKeyTyped = null;
        return r;
    }
    
    /**
     * Check whether a key, identified by a key name (String),
     * is currently down (or latched).
     * 
     * @param key     The name of the key to check
     * @return        True if the key is currently down, or was down since
     *                it was last checked; false otherwise.
     */
    @OnThread(Tag.Simulation)
    public synchronized boolean isKeyDown(String key)
    {
        key = key.toLowerCase();
        boolean pressed = keyDown.contains(key) || keyLatched.contains(key);
        // We forget any was-pressed state here; if the frame is long
        // then we don't necessarily want to record the key as held down all
        // frame if the user taps it lightly (e.g. if someone uses a complex
        // act cycle via Greenfoot.delay() and Greenfoot.ask())
        keyLatched.remove(key);
        return pressed;
    }

    /**
     * Notifies that a key has been pressed.
     * @param keyCode The KeyCode from KeyEvent.getCode()
     * @param keyText The text from KeyEvent.getText()
     */
    /**
     * A key has been pressed. The name is the Greenfoot key name ("a", "space",
     * "up", ...), already translated from the host toolkit's key code by the
     * caller (see greenfoot.vmcomm.FXKeyNames and greenfoot.player.AwtKeyNames).
     */
    public synchronized void keyPressed(String keyName)
    {
        keyLatched.add(keyName);
        keyDown.add(keyName);
    }

    /** A key has been released; see {@link #keyPressed(String)}. */
    public synchronized void keyReleased(String keyName)
    {
        keyDown.remove(keyName);
        lastKeyTyped = keyName;
    }

    /** A key has been typed; see {@link #keyPressed(String)}. */
    public synchronized void keyTyped(String keyName)
    {
        if (!keyName.isEmpty() && !keyName.equals("undefined"))
        {
            lastKeyTyped = keyName;
        }
    }

    public void focusGained() { }

    /**
     * If we loose focus, we should treat all keys as not pressed anymore
     */
    public void focusLost()
    {
        releaseAllKeys();
    }

    /**
     * Release all the keys.
     */
    private synchronized void releaseAllKeys()
    {
        keyDown.clear();
        keyLatched.clear();
    }
}
