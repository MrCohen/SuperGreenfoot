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

import greenfoot.vmcomm.VMCommsMain;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A window that shows one open project, driven by that project's
 * {@link GreenfootProjectController}.  The Classic Greenfoot IDE window
 * (GreenfootStage) is one; the new SuperGreenfoot IDE window will be another.
 *
 * <p>For now the debug VM's callbacks still reach the view unchanged, through
 * the controller; they are replaced by view-level operations as the rest of
 * the window's logic moves into the controller.
 */
@OnThread(Tag.FXPlatform)
public interface ProjectView extends VMCommsMain.CommsListener
{
}
