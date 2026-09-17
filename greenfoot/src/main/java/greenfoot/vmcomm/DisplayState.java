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
package greenfoot.vmcomm;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * SuperGreenfoot: encoding of the display state and display requests that
 * travel between the IDE and the debug VM.
 *
 * <p><b>Requests</b> (debug VM to IDE, in the debug VM's status area) are one
 * sequence number plus one flags integer. Bits 0-3 hold the requested values
 * ({@link #FULL_SCREEN}, {@link #CONTROLS_VISIBLE}, {@link #CONTROLS_LOCKED},
 * {@link #PIXEL_PERFECT}); the same bits shifted by {@link #MASK_SHIFT} say
 * which of them the scenario actually asked for, so a request to enter full
 * screen does not also re-apply an untouched controls setting that the user
 * may have changed by hand.
 *
 * <p><b>State</b> (IDE to debug VM, {@link Command#COMMAND_DISPLAY_STATE}) is
 * an array of {@link #LENGTH} integers, see the {@code I_*} indexes. The IDE
 * sends it before the first world is created, whenever the state changes, and
 * after every request it applied (with that request's sequence number in
 * {@link #I_APPLIED_REQUEST}, which lets the debug VM stop re-sending it).
 */
@OnThread(Tag.Any)
public final class DisplayState
{
    public static final int FULL_SCREEN = 1;
    public static final int CONTROLS_VISIBLE = 2;
    public static final int CONTROLS_LOCKED = 4;
    public static final int PIXEL_PERFECT = 8;
    public static final int MASK_SHIFT = 4;
    private static final int VALUE_BITS = 0xF;

    public static final int I_FULL_SCREEN = 0;
    public static final int I_CONTROLS_VISIBLE = 1;
    public static final int I_CONTROLS_LOCKED = 2;
    public static final int I_PIXEL_PERFECT = 3;
    public static final int I_SUPPORTED = 4;
    public static final int I_SCREEN_WIDTH = 5;
    public static final int I_SCREEN_HEIGHT = 6;
    public static final int I_SCALE_MILLI = 7;
    public static final int I_APPLIED_REQUEST = 8;
    public static final int LENGTH = 9;

    private DisplayState()
    {
    }

    /** Add one requested value to a flags integer (value bit and mask bit). */
    public static int withRequest(int flags, int field, boolean value)
    {
        int f = value ? (flags | field) : (flags & ~field);
        return f | (field << MASK_SHIFT);
    }

    /** Whether the scenario asked for this field in the request. */
    public static boolean isRequested(int flags, int field)
    {
        return (flags & (field << MASK_SHIFT)) != 0;
    }

    /** The requested value of a field (only meaningful when {@link #isRequested}). */
    public static boolean value(int flags, int field)
    {
        return (flags & field) != 0;
    }

    /** Keep the values but forget which were requested (after the IDE applied them). */
    public static int clearRequested(int flags)
    {
        return flags & VALUE_BITS;
    }

    /** Value bits for a state array, used to seed later requests from the current state. */
    public static int valuesOf(int[] state)
    {
        int f = 0;
        if (state != null)
        {
            if (state[I_FULL_SCREEN] != 0) f |= FULL_SCREEN;
            if (state[I_CONTROLS_VISIBLE] != 0) f |= CONTROLS_VISIBLE;
            if (state[I_CONTROLS_LOCKED] != 0) f |= CONTROLS_LOCKED;
            if (state[I_PIXEL_PERFECT] != 0) f |= PIXEL_PERFECT;
        }
        return f;
    }

    public static int[] encode(boolean fullScreen, boolean controlsVisible, boolean controlsLocked,
            boolean pixelPerfect, boolean supported, int screenWidth, int screenHeight, double scale,
            int appliedRequest)
    {
        int[] s = new int[LENGTH];
        s[I_FULL_SCREEN] = fullScreen ? 1 : 0;
        s[I_CONTROLS_VISIBLE] = controlsVisible ? 1 : 0;
        s[I_CONTROLS_LOCKED] = controlsLocked ? 1 : 0;
        s[I_PIXEL_PERFECT] = pixelPerfect ? 1 : 0;
        s[I_SUPPORTED] = supported ? 1 : 0;
        s[I_SCREEN_WIDTH] = screenWidth;
        s[I_SCREEN_HEIGHT] = screenHeight;
        s[I_SCALE_MILLI] = (int) Math.round(scale * 1000);
        s[I_APPLIED_REQUEST] = appliedRequest;
        return s;
    }

    /** The state carried by a received command (data[0] is the command type). */
    public static int[] fromCommand(int[] data)
    {
        int[] s = new int[LENGTH];
        int n = Math.min(LENGTH, data.length - 1);
        System.arraycopy(data, 1, s, 0, n);
        return s;
    }

    public static double scaleOf(int[] state)
    {
        return state == null ? 1.0 : state[I_SCALE_MILLI] / 1000.0;
    }
}
