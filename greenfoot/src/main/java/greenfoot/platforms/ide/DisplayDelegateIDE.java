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
package greenfoot.platforms.ide;

import greenfoot.ScaleMode;
import greenfoot.platforms.DisplayDelegate;
import greenfoot.vmcomm.DisplayState;
import greenfoot.vmcomm.VMCommsSimulation;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * SuperGreenfoot: the presentation hooks inside the IDE's debug VM. Requests
 * (full screen, controls, scale mode) are relayed to the IDE window through the
 * shared-memory channel; the getters answer from the state the IDE last sent
 * back, so scenario code sees what the user did with the Full Screen menu too.
 */
@OnThread(Tag.Any)
public class DisplayDelegateIDE implements DisplayDelegate
{
    private final VMCommsSimulation comms;

    public DisplayDelegateIDE(VMCommsSimulation comms)
    {
        this.comms = comms;
    }

    private int[] state()
    {
        return comms.getDisplayState();
    }

    private boolean flag(int index, boolean ifUnknown)
    {
        int[] s = state();
        return s == null ? ifUnknown : s[index] != 0;
    }

    @Override
    public void setFullScreen(boolean fullScreen)
    {
        comms.requestDisplayChange(DisplayState.FULL_SCREEN, fullScreen);
    }

    @Override
    public boolean isFullScreen()
    {
        return flag(DisplayState.I_FULL_SCREEN, false);
    }

    @Override
    public boolean isFullScreenSupported()
    {
        return flag(DisplayState.I_SUPPORTED, true);
    }

    @Override
    public void setControlsVisible(boolean visible)
    {
        comms.requestDisplayChange(DisplayState.CONTROLS_VISIBLE, visible);
    }

    @Override
    public boolean isControlsVisible()
    {
        return flag(DisplayState.I_CONTROLS_VISIBLE, true);
    }

    @Override
    public void setControlsLocked(boolean locked)
    {
        comms.requestDisplayChange(DisplayState.CONTROLS_LOCKED, locked);
    }

    @Override
    public boolean isControlsLocked()
    {
        return flag(DisplayState.I_CONTROLS_LOCKED, false);
    }

    @Override
    public void setScaleMode(ScaleMode mode)
    {
        comms.requestDisplayChange(DisplayState.PIXEL_PERFECT, mode == ScaleMode.PIXEL_PERFECT);
    }

    @Override
    public ScaleMode getScaleMode()
    {
        return flag(DisplayState.I_PIXEL_PERFECT, false) ? ScaleMode.PIXEL_PERFECT : ScaleMode.SMOOTH;
    }

    @Override
    public double getDisplayScale()
    {
        return DisplayState.scaleOf(state());
    }

    @Override
    public int getScreenWidth()
    {
        int[] s = state();
        return s == null ? 0 : s[DisplayState.I_SCREEN_WIDTH];
    }

    @Override
    public int getScreenHeight()
    {
        int[] s = state();
        return s == null ? 0 : s[DisplayState.I_SCREEN_HEIGHT];
    }
}
