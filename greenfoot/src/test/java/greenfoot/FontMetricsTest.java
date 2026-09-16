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
package greenfoot;

import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

/**
 * Tests the SuperGreenfoot Font measurement API against a pixel scan of the
 * text actually drawn by GreenfootImage.drawString (the technique the
 * MrCohenLibrary Utility.getStringWidth used, without the 3-pixel stride).
 */
public class FontMetricsTest extends TestCase
{
    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
    }

    /** Draw text on a large image and return {inkLeft, inkRight, inkTop, inkBottom} by scanning alpha. */
    private static int[] scanInk(Font font, String text)
    {
        int w = 600, h = 200;
        GreenfootImage img = new GreenfootImage(w, h);
        img.setFont(font);
        img.setColor(Color.BLACK);
        img.drawString(text, 50, 100);
        int left = Integer.MAX_VALUE, right = -1, top = Integer.MAX_VALUE, bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (img.getColorAt(x, y).getAlpha() > 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        return new int[] {left, right, top, bottom};
    }

    private static void assertWidthMatchesScan(Font font, String text)
    {
        int[] ink = scanInk(font, text);
        int scanned = ink[1] - ink[0] + 1;
        int measured = font.getStringWidth(text);
        assertTrue(text + " in " + font.getName() + " " + font.getSize()
                + ": scanned " + scanned + " measured " + measured,
                Math.abs(scanned - measured) <= 1);
    }

    private static void assertHeightMatchesScan(Font font, String text)
    {
        int[] ink = scanInk(font, text);
        int scanned = ink[3] - ink[2] + 1;
        int measured = font.getStringHeight(text);
        assertTrue(text + " in " + font.getName() + " " + font.getSize()
                + ": scanned height " + scanned + " measured " + measured,
                Math.abs(scanned - measured) <= 1);
    }

    public void testStringWidthMatchesPixelScan()
    {
        assertWidthMatchesScan(new Font("SansSerif", false, false, 24), "Hello, World!");
        assertWidthMatchesScan(new Font("Serif", true, false, 30), "Score: 12345");
        assertWidthMatchesScan(new Font("Monospaced", false, true, 18), "gjpqy WIDE");
        assertWidthMatchesScan(new Font(40), "A");
    }

    public void testStringHeightMatchesPixelScan()
    {
        assertHeightMatchesScan(new Font("SansSerif", false, false, 24), "Hello, World!");
        assertHeightMatchesScan(new Font("Serif", true, false, 30), "gjpqy");
        assertHeightMatchesScan(new Font("SansSerif", false, false, 24), "Two\nLines");
    }

    public void testEmptyAndMultiline()
    {
        Font f = new Font("SansSerif", false, false, 20);
        assertEquals(0, f.getStringWidth(""));
        assertEquals(0, f.getStringHeight(""));
        int one = f.getStringWidth("abc");
        int two = f.getStringWidth("abc\nabcdef");
        assertTrue(two > one);
        assertEquals(f.getStringWidth("abcdef"), two);
        assertTrue(f.getStringHeight("a\nb") > f.getStringHeight("a"));
    }

    public void testMetricsAreSensible()
    {
        Font f = new Font("SansSerif", false, false, 24);
        assertTrue(f.getAscent() > 0);
        assertTrue(f.getDescent() > 0);
        assertTrue(f.getLineHeight() >= f.getAscent() + f.getDescent());
        // Bigger font, bigger metrics
        Font g = new Font("SansSerif", false, false, 48);
        assertTrue(g.getAscent() > f.getAscent());
        assertTrue(g.getStringWidth("Hello") > f.getStringWidth("Hello"));
    }

    public void testDrawCenteredStringIsCentredHorizontally()
    {
        Font font = new Font("SansSerif", true, false, 28);
        GreenfootImage img = new GreenfootImage(300, 100);
        img.setFont(font);
        img.setColor(Color.BLACK);
        img.drawCenteredString("Centre", 150, 50);
        int left = Integer.MAX_VALUE, right = -1;
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 300; x++) {
                if (img.getColorAt(x, y).getAlpha() > 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        double inkCentre = (left + right) / 2.0;
        assertTrue("ink centre " + inkCentre, Math.abs(inkCentre - 150) <= 1.5);
    }
}
