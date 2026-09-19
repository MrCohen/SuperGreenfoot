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

import junit.framework.TestCase;

/**
 * Tests reading and writing the Classic/SuperGreenfoot IDE preference value.
 */
public class UiModeTest extends TestCase
{
    public void testPrefValuesRoundTrip()
    {
        for (UiMode mode : UiMode.values())
        {
            assertEquals(mode, UiMode.fromPrefValue(mode.getPrefValue()));
        }
    }

    public void testKnownValues()
    {
        assertEquals(UiMode.SUPER, UiMode.fromPrefValue("super"));
        assertEquals(UiMode.CLASSIC, UiMode.fromPrefValue("classic"));
        // Hand-edited preference files may differ in case or spacing:
        assertEquals(UiMode.SUPER, UiMode.fromPrefValue(" Super "));
    }

    public void testMissingOrUnknownValueGivesTheDefault()
    {
        assertEquals(UiMode.DEFAULT, UiMode.fromPrefValue(null));
        assertEquals(UiMode.DEFAULT, UiMode.fromPrefValue(""));
        assertEquals(UiMode.DEFAULT, UiMode.fromPrefValue("modern"));
    }

    public void testTheDefaultIsClassicForNow()
    {
        // The owner chooses the shipped default later; until then new users get Classic.
        assertEquals(UiMode.CLASSIC, UiMode.DEFAULT);
    }
}
