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

import java.awt.event.KeyEvent;

/**
 * Translates AWT key events into Greenfoot key names, matching the names the
 * IDE produces from JavaFX key codes (see greenfoot.vmcomm.FXKeyNames), so a
 * scenario's Greenfoot.isKeyDown("up") works identically in both.
 */
public final class AwtKeyNames
{
    private AwtKeyNames()
    {
    }

    /** Name for a KEY_PRESSED / KEY_RELEASED event. */
    public static String toGreenfootName(KeyEvent e)
    {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP: return "up";
            case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN: return "down";
            case KeyEvent.VK_LEFT: case KeyEvent.VK_KP_LEFT: return "left";
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_KP_RIGHT: return "right";
            case KeyEvent.VK_SPACE: return "space";
            case KeyEvent.VK_ENTER: return "enter";
            case KeyEvent.VK_TAB: return "tab";
            case KeyEvent.VK_ESCAPE: return "escape";
            case KeyEvent.VK_BACK_SPACE: return "backspace";
            case KeyEvent.VK_DELETE: return "delete";
            case KeyEvent.VK_SHIFT: return "shift";
            case KeyEvent.VK_CONTROL: return "control";
            case KeyEvent.VK_ALT: return "alt";
            case KeyEvent.VK_META: return "meta";
            case KeyEvent.VK_CAPS_LOCK: return "caps lock";
            case KeyEvent.VK_QUOTE: return "'";
            case KeyEvent.VK_INSERT: return "insert";
            case KeyEvent.VK_HOME: return "home";
            case KeyEvent.VK_END: return "end";
            case KeyEvent.VK_PAGE_UP: return "page up";
            case KeyEvent.VK_PAGE_DOWN: return "page down";
            default:
                break;
        }
        if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) {
            return "f" + (code - KeyEvent.VK_F1 + 1);
        }
        if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9) {
            return "" + (char) ('0' + (code - KeyEvent.VK_NUMPAD0));
        }
        char ch = e.getKeyChar();
        if (ch != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(ch) && ch != ' ') {
            return String.valueOf(Character.toLowerCase(ch));
        }
        String text = KeyEvent.getKeyText(code);
        return text == null ? "undefined" : text.toLowerCase();
    }

    /** Name for a KEY_TYPED event (has a character but no key code). */
    public static String typedName(KeyEvent e)
    {
        char ch = e.getKeyChar();
        switch (ch) {
            case '\r': case '\n': return "enter";
            case '\t': return "tab";
            case '\b': return "backspace";
            case ' ': return "space";
            case 27: return "escape";
            default:
                break;
        }
        if (ch == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(ch)) {
            return "";
        }
        return String.valueOf(Character.toLowerCase(ch));
    }
}
