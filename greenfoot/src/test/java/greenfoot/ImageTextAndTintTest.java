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
package greenfoot;

import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

/**
 * Measuring text without drawing it agrees with the text-image constructor,
 * and tint moves colours while keeping every pixel's transparency.
 */
public class ImageTextAndTintTest extends TestCase
{
    @Override
    protected void setUp()
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
    }

    public void testTextSizeMatchesTheConstructor()
    {
        for (String text : new String[] { "Tooltip", "a much longer line of text", "two\nlines", "", "g" }) {
            for (int size : new int[] { 12, 20, 40 }) {
                GreenfootImage drawn = new GreenfootImage(text, size, Color.BLACK, Color.WHITE);
                assertEquals(text + "@" + size + " width", drawn.getWidth(), GreenfootImage.getTextWidth(text, size));
                assertEquals(text + "@" + size + " height", drawn.getHeight(), GreenfootImage.getTextHeight(text, size));
            }
        }
        assertEquals(GreenfootImage.getTextWidth("", 20), GreenfootImage.getTextWidth(null, 20));
        assertTrue(GreenfootImage.getTextHeight("two\nlines", 20) > GreenfootImage.getTextHeight("one", 20));
    }

    private static void assertClose(Color expected, Color actual, int tolerance)
    {
        assertEquals("alpha of " + actual, expected.getAlpha(), actual.getAlpha());
        assertTrue("expected about " + expected + " but was " + actual,
                Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance);
    }

    public void testTintMovesColourAndKeepsAlpha()
    {
        GreenfootImage img = new GreenfootImage(4, 4);
        img.setColorAt(0, 0, new Color(200, 100, 0, 255));
        img.setColorAt(1, 0, new Color(200, 100, 0, 128));
        img.setColorAt(2, 0, new Color(0, 0, 0, 0));
        Color red = new Color(250, 0, 0);

        GreenfootImage half = new GreenfootImage(img);
        half.tint(red, 0.5);
        assertEquals(new Color(225, 50, 0, 255), half.getColorAt(0, 0));
        assertEquals("alpha kept", 128, half.getColorAt(1, 0).getAlpha());
        // Half-transparent pixels are stored premultiplied, so allow a little rounding.
        assertClose(new Color(225, 50, 0, 128), half.getColorAt(1, 0), 2);
        assertEquals("transparent stays transparent", 0, half.getColorAt(2, 0).getAlpha());
        assertEquals("the original is untouched", new Color(200, 100, 0, 255), img.getColorAt(0, 0));

        GreenfootImage silhouette = new GreenfootImage(img);
        silhouette.tint(red, 1.0);
        assertEquals(new Color(250, 0, 0, 255), silhouette.getColorAt(0, 0));
        assertClose(new Color(250, 0, 0, 128), silhouette.getColorAt(1, 0), 2);

        GreenfootImage same = new GreenfootImage(img);
        same.tint(red, 0);
        assertEquals(img.getColorAt(0, 0), same.getColorAt(0, 0));

        try {
            img.tint(red, 1.5);
            fail("amount over 1");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("between 0 and 1"));
        }
        try {
            img.tint(null, 0.5);
            fail("null colour");
        }
        catch (IllegalArgumentException e) {
            // expected
        }
    }
}
