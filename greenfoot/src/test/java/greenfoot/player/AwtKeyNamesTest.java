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
package greenfoot.player;

import greenfoot.gui.input.KeyboardManager;
import junit.framework.TestCase;

import java.awt.Component;
import java.awt.Panel;
import java.awt.event.KeyEvent;

/**
 * AWT key events map to the same Greenfoot key names the IDE produces, and
 * the name-based KeyboardManager tracks them.
 */
public class AwtKeyNamesTest extends TestCase
{
    private static final Component SRC = new Panel();

    private static KeyEvent pressed(int code, char ch)
    {
        return new KeyEvent(SRC, KeyEvent.KEY_PRESSED, 0, 0, code, ch);
    }

    public void testNames()
    {
        assertEquals("up", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED)));
        assertEquals("space", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_SPACE, ' ')));
        assertEquals("a", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_A, 'A')));
        assertEquals("3", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_3, '3')));
        assertEquals("7", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_NUMPAD7, '7')));
        assertEquals("enter", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_ENTER, '\n')));
        assertEquals("escape", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_ESCAPE, (char) 27)));
        assertEquals("shift", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED)));
        assertEquals("f5", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_F5, KeyEvent.CHAR_UNDEFINED)));
        assertEquals("backspace", AwtKeyNames.toGreenfootName(pressed(KeyEvent.VK_BACK_SPACE, '\b')));

        assertEquals("enter", AwtKeyNames.typedName(new KeyEvent(SRC, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '\n')));
        assertEquals("q", AwtKeyNames.typedName(new KeyEvent(SRC, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'Q')));
        assertEquals("", AwtKeyNames.typedName(new KeyEvent(SRC, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, (char) 1)));
    }

    public void testKeyboardManagerByName()
    {
        KeyboardManager km = new KeyboardManager();
        assertNull(km.getKey());
        km.keyPressed("up");
        assertTrue(km.isKeyDown("up"));
        assertTrue(km.isKeyDown("UP"));
        assertFalse(km.isKeyDown("down"));
        km.keyReleased("up");
        assertEquals("up", km.getKey());
        assertNull(km.getKey());
        // Tapped within a frame: latched until read
        km.keyPressed("space");
        km.keyReleased("space");
        assertTrue(km.isKeyDown("space"));
        km.clearLatchedKeys();
        assertFalse(km.isKeyDown("space"));
        km.keyTyped("x");
        assertEquals("x", km.getKey());
        km.keyTyped("undefined");
        assertNull(km.getKey());
        km.focusLost();
        assertFalse(km.isKeyDown("up"));
    }
}
