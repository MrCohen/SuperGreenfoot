/*
 This file is part of the Greenfoot program.
 Copyright (C) 2005-2009,2010,2011,2013,2015,2016,2018,2019,2021  Poul Henriksen and Michael Kolling
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

import greenfoot.platforms.DisplayDelegate;
import greenfoot.util.GreenfootUtil;
import greenfoot.vmcomm.DisplayState;
import junit.framework.TestCase;

/**
 * The scenario-side display API routes to the installed DisplayDelegate, the
 * NONE delegate answers safely, and the IDE channel encoding round-trips.
 */
public class DisplayApiTest extends TestCase
{
    /** Records every call and answers from its fields. */
    private static class Recording implements DisplayDelegate
    {
        boolean fullScreen, controlsVisible = true, controlsLocked;
        ScaleMode mode = ScaleMode.SMOOTH;
        double windowScale = 1.0;
        boolean cursorVisible = true;
        StringBuilder log = new StringBuilder();

        public void setFullScreen(boolean on) { fullScreen = on; log.append("fs=" + on + ";"); }
        public boolean isFullScreen() { return fullScreen; }
        public boolean isFullScreenSupported() { return true; }
        public void setControlsVisible(boolean v) { controlsVisible = v; log.append("cv=" + v + ";"); }
        public boolean isControlsVisible() { return controlsVisible; }
        public void setControlsLocked(boolean l) { controlsLocked = l; log.append("cl=" + l + ";"); }
        public boolean isControlsLocked() { return controlsLocked; }
        public void setScaleMode(ScaleMode m) { mode = m; log.append("sm=" + m + ";"); }
        public ScaleMode getScaleMode() { return mode; }
        public double getDisplayScale() { return 3.0; }
        public int getScreenWidth() { return 1920; }
        public int getScreenHeight() { return 1080; }
        public void setWindowScale(double s) { windowScale = s; log.append("ws=" + s + ";"); }
        public double getWindowScale() { return windowScale; }
        public boolean isStandalone() { return true; }
        public void setCursorVisible(boolean v) { cursorVisible = v; log.append("cur=" + v + ";"); }
        public boolean isCursorVisible() { return cursorVisible; }
        public void setCursor(int[] argb, int w, int h, int hx, int hy)
        {
            log.append("img=" + (argb == null ? "none" : w + "x" + h) + "@" + hx + "," + hy + ";");
        }
    }

    @Override
    protected void setUp()
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        GreenfootUtil.forgetCursor();
    }

    @Override
    protected void tearDown()
    {
        GreenfootUtil.setDisplayDelegate(null);
        GreenfootUtil.forgetCursor();
    }

    public void testNoneDelegateIsSafe()
    {
        GreenfootUtil.setDisplayDelegate(null);
        Greenfoot.setFullScreen(true);
        Greenfoot.setScaleMode(ScaleMode.PIXEL_PERFECT);
        Greenfoot.setWindowScale(2);
        assertFalse(Greenfoot.isFullScreen());
        assertFalse(Greenfoot.isFullScreenSupported());
        assertEquals(ScaleMode.SMOOTH, Greenfoot.getScaleMode());
        assertEquals(1.0, Greenfoot.getDisplayScale(), 0.0);
        assertEquals(0, Greenfoot.getScreenWidth());
        assertEquals(0, Greenfoot.getScreenHeight());
        assertTrue(Greenfoot.isControlsVisible());
        assertFalse(Greenfoot.isControlsLocked());
        assertEquals(1.0, Greenfoot.getWindowScale(), 0.0);
        assertFalse(Greenfoot.isStandalone());
        Greenfoot.setCursorVisible(false);
        Greenfoot.setCursor((String) null);
        Greenfoot.setCursor(new GreenfootImage(4, 4));
        assertFalse("the request is what isCursorVisible answers with, even with no delegate", Greenfoot.isCursorVisible());
        GreenfootUtil.forgetCursor();
        assertTrue(Greenfoot.isCursorVisible());
    }

    public void testApiRoutesToDelegate()
    {
        Recording d = new Recording();
        GreenfootUtil.setDisplayDelegate(d);
        Greenfoot.setFullScreen(true);
        Greenfoot.setScaleMode(ScaleMode.PIXEL_PERFECT);
        Greenfoot.setScaleMode(null);   // ignored
        Greenfoot.setControlsVisible(false);
        Greenfoot.setControlsLocked(true);
        Greenfoot.setWindowScale(2.5);
        Greenfoot.setCursorVisible(false);
        Greenfoot.setCursor((String) null);            // no file lookup for the normal cursor
        Greenfoot.setCursor((String) null, 3, 4);      // the same request: not sent twice
        GreenfootImage ring = new GreenfootImage(8, 6);
        Greenfoot.setCursor(ring);
        Greenfoot.setCursor(ring);                     // unchanged: not sent again
        Greenfoot.setCursor(ring, 2, 3);               // a new hot spot is a new request
        ring.setColor(Color.RED);
        ring.fill();
        Greenfoot.setCursor(ring, 2, 3);               // new pixels: sent
        assertEquals("fs=true;sm=PIXEL_PERFECT;cv=false;cl=true;ws=2.5;cur=false;img=none@-1,-1;"
                + "img=8x6@-1,-1;img=8x6@2,3;img=8x6@2,3;", d.log.toString());
        assertFalse(Greenfoot.isCursorVisible());
        assertTrue(Greenfoot.isFullScreen());
        assertTrue(Greenfoot.isFullScreenSupported());
        assertEquals(ScaleMode.PIXEL_PERFECT, Greenfoot.getScaleMode());
        assertEquals(3.0, Greenfoot.getDisplayScale(), 0.0);
        assertEquals(1920, Greenfoot.getScreenWidth());
        assertEquals(1080, Greenfoot.getScreenHeight());
        assertFalse(Greenfoot.isControlsVisible());
        assertTrue(Greenfoot.isControlsLocked());
        assertEquals(2.5, Greenfoot.getWindowScale(), 0.0);
        assertTrue(Greenfoot.isStandalone());
    }

    public void testCursorPictureIsChecked()
    {
        Recording d = new Recording();
        GreenfootUtil.setDisplayDelegate(d);
        try {
            Greenfoot.setCursor(new GreenfootImage(8, 6), 8, 0);
            fail("a hot spot outside the picture");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("hot spot"));
        }
        try {
            Greenfoot.setCursor(new GreenfootImage(300, 10));
            fail("too large");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("256"));
        }
        try {
            Greenfoot.setCursor("no-such-cursor-picture.png");
            fail("a missing file");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no-such-cursor-picture.png"));
        }
        assertEquals("nothing reached the delegate", "", d.log.toString());
        // A negative hot spot means the centre, and the pixels sent are the picture's.
        GreenfootImage dot = new GreenfootImage(2, 2);
        dot.setColorAt(1, 1, Color.BLUE);
        Greenfoot.setCursor(dot, -5, 0);
        assertEquals("img=2x2@-1,-1;", d.log.toString());
        // After a reset the same picture is asked for again.
        GreenfootUtil.forgetCursor();
        Greenfoot.setCursor(dot);
        assertEquals("img=2x2@-1,-1;img=2x2@-1,-1;", d.log.toString());
    }

    public void testRequestFlagsCarryValuesAndMask()
    {
        int f = 0;
        assertFalse(DisplayState.isRequested(f, DisplayState.FULL_SCREEN));
        f = DisplayState.withRequest(f, DisplayState.FULL_SCREEN, true);
        assertTrue(DisplayState.isRequested(f, DisplayState.FULL_SCREEN));
        assertTrue(DisplayState.value(f, DisplayState.FULL_SCREEN));
        assertFalse(DisplayState.isRequested(f, DisplayState.CONTROLS_VISIBLE));
        // A second request merges: both fields requested, latest values kept
        f = DisplayState.withRequest(f, DisplayState.CONTROLS_VISIBLE, false);
        f = DisplayState.withRequest(f, DisplayState.FULL_SCREEN, false);
        assertTrue(DisplayState.isRequested(f, DisplayState.CONTROLS_VISIBLE));
        assertFalse(DisplayState.value(f, DisplayState.CONTROLS_VISIBLE));
        assertFalse(DisplayState.value(f, DisplayState.FULL_SCREEN));
        // After the IDE applied it, the mask goes but the values stay
        int cleared = DisplayState.clearRequested(f);
        assertFalse(DisplayState.isRequested(cleared, DisplayState.FULL_SCREEN));
        assertFalse(DisplayState.isRequested(cleared, DisplayState.CONTROLS_VISIBLE));
        assertFalse(DisplayState.value(cleared, DisplayState.CONTROLS_VISIBLE));
        // The cursor flag's value bit and mask bit do not overlap the other fields' bits
        int c = DisplayState.withRequest(0, DisplayState.CURSOR_HIDDEN, true);
        assertTrue(DisplayState.isRequested(c, DisplayState.CURSOR_HIDDEN));
        assertTrue(DisplayState.value(c, DisplayState.CURSOR_HIDDEN));
        for (int other : new int[] {DisplayState.FULL_SCREEN, DisplayState.CONTROLS_VISIBLE,
                DisplayState.CONTROLS_LOCKED, DisplayState.PIXEL_PERFECT})
        {
            assertFalse(DisplayState.isRequested(c, other));
            assertFalse(DisplayState.value(c, other));
        }
        assertEquals(DisplayState.CURSOR_HIDDEN, DisplayState.clearRequested(c));
    }

    public void testStateEncodingRoundTrips()
    {
        int[] s = DisplayState.encode(true, false, true, true, true, 1512, 982, 2.25, 7, true);
        assertEquals(DisplayState.LENGTH, s.length);
        // As received: command type first, then the state
        int[] data = new int[s.length + 1];
        data[0] = 42;
        System.arraycopy(s, 0, data, 1, s.length);
        int[] back = DisplayState.fromCommand(data);
        assertEquals(1, back[DisplayState.I_FULL_SCREEN]);
        assertEquals(0, back[DisplayState.I_CONTROLS_VISIBLE]);
        assertEquals(1, back[DisplayState.I_CONTROLS_LOCKED]);
        assertEquals(1, back[DisplayState.I_PIXEL_PERFECT]);
        assertEquals(1, back[DisplayState.I_SUPPORTED]);
        assertEquals(1512, back[DisplayState.I_SCREEN_WIDTH]);
        assertEquals(982, back[DisplayState.I_SCREEN_HEIGHT]);
        assertEquals(2.25, DisplayState.scaleOf(back), 0.0005);
        assertEquals(7, back[DisplayState.I_APPLIED_REQUEST]);
        assertEquals(1, back[DisplayState.I_CURSOR_HIDDEN]);
        assertEquals(DisplayState.FULL_SCREEN | DisplayState.CONTROLS_LOCKED | DisplayState.PIXEL_PERFECT
                | DisplayState.CURSOR_HIDDEN, DisplayState.valuesOf(back));
        assertEquals(1.0, DisplayState.scaleOf(null), 0.0);
    }
}
