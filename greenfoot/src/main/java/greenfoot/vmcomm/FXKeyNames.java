/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2005-2023 Poul Henriksen and Michael Kolling
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
package greenfoot.vmcomm;

import javafx.scene.input.KeyCode;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * Translates JavaFX key codes (as sent by the IDE over shared memory) into
 * Greenfoot key names. This is the only JavaFX-dependent part of keyboard
 * handling; the engine's KeyboardManager works purely with names so that the
 * standalone player and the web build can supply their own translation.
 */
public final class FXKeyNames
{
    private static boolean hasNumLock = true;

    private FXKeyNames()
    {
    }

    public static String toGreenfootName(KeyCode keycode, String keyText)
    {
        if (keycode.ordinal() >= KeyCode.NUMPAD0.ordinal() && keycode.ordinal() <= KeyCode.NUMPAD9.ordinal()) {
            // At least on linux, we can only get these codes if numlock is on; in that
            // case we want to map to a digit anyway.
            return "" + (char)('0' + (keycode.ordinal() - KeyCode.NUMPAD0.ordinal()));
        }
        boolean numlock = true;
        if (hasNumLock)
        {
            try
            {
                numlock = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_NUM_LOCK);
            }
            catch (UnsupportedOperationException usoe)
            {
                hasNumLock = false;
            }
        }
        if (numlock)
        {
            if (keycode == KeyCode.KP_UP) keycode = KeyCode.DIGIT8;
            else if (keycode == KeyCode.KP_DOWN) keycode = KeyCode.DIGIT2;
            else if (keycode == KeyCode.KP_LEFT) keycode = KeyCode.DIGIT4;
            else if (keycode == KeyCode.KP_RIGHT) keycode = KeyCode.DIGIT6;
        }
        else
        {
            if (keycode == KeyCode.KP_UP) keycode = KeyCode.UP;
            else if (keycode == KeyCode.KP_DOWN) keycode = KeyCode.DOWN;
            else if (keycode == KeyCode.KP_LEFT) keycode = KeyCode.LEFT;
            else if (keycode == KeyCode.KP_RIGHT) keycode = KeyCode.RIGHT;
        }
        // Handle the keys where the Greenfoot name doesn't line up with the FX KeyCode.getName():
        switch (keycode)
        {
            case ESCAPE:
                return "escape";
            case BACK_SPACE:
                return "backspace";
            case QUOTE:
                return "\'";
            case CONTROL:
                return "control";
            default:
                break;
        }
        if (!keyText.isEmpty())
        {
            switch (keyText)
            {
                case "\r": case "\n":
                    return "enter";
                case "\t":
                    return "tab";
                case "\b":
                    return "backspace";
                case " ":
                    return "space";
                case "\u001B":
                    return "escape";
            }
            return keyText.toLowerCase();
        }
        return keycode.getName().toLowerCase();
    }
}
