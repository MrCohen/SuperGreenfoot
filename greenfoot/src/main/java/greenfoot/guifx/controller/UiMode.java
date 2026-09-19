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

import bluej.Config;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Which IDE window the user works in: the Classic Greenfoot IDE, or the new
 * SuperGreenfoot IDE. It is a per-user preference, and applies to every open
 * project at once.
 */
@OnThread(Tag.Any)
public enum UiMode
{
    CLASSIC("classic"),
    SUPER("super");

    /** The user preference holding the mode. */
    public static final String PREF_KEY = "supergreenfoot.ui.mode";
    /** The mode used when the user has not chosen one. */
    public static final UiMode DEFAULT = CLASSIC;

    private final String prefValue;

    UiMode(String prefValue)
    {
        this.prefValue = prefValue;
    }

    /**
     * The value stored in the preference file.
     */
    public String getPrefValue()
    {
        return prefValue;
    }

    /**
     * The mode for a preference value (the default for an unknown or missing value).
     */
    public static UiMode fromPrefValue(String value)
    {
        if (value != null)
        {
            for (UiMode mode : values())
            {
                if (mode.prefValue.equalsIgnoreCase(value.trim()))
                {
                    return mode;
                }
            }
        }
        return DEFAULT;
    }

    /**
     * The mode the user last chose.
     */
    public static UiMode fromPreferences()
    {
        return fromPrefValue(Config.getPropString(PREF_KEY, null));
    }

    /**
     * Remember this mode as the user's choice.
     */
    public void saveToPreferences()
    {
        Config.putPropString(PREF_KEY, prefValue);
    }
}
