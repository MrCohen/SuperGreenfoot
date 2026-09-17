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
    }

    @Override
    protected void tearDown()
    {
        GreenfootUtil.setDisplayDelegate(null);
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
        assertEquals("fs=true;sm=PIXEL_PERFECT;cv=false;cl=true;ws=2.5;", d.log.toString());
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
    }

    public void testStateEncodingRoundTrips()
    {
        int[] s = DisplayState.encode(true, false, true, true, true, 1512, 982, 2.25, 7);
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
        assertEquals(DisplayState.FULL_SCREEN | DisplayState.CONTROLS_LOCKED | DisplayState.PIXEL_PERFECT,
                DisplayState.valuesOf(back));
        assertEquals(1.0, DisplayState.scaleOf(null), 0.0);
    }
}
