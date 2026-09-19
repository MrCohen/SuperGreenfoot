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
package bluej.editor.stride;

import java.util.List;

import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Menu;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * The window an embedded editor host is shown in (the SuperGreenfoot IDE's main
 * window), which answers the questions a stand-alone editor window answers for itself.
 * See {@link FXTabbedEditor#createEmbedded(bluej.pkgmgr.Project)}.
 */
@OnThread(Tag.FXPlatform)
public interface EditorHostSite
{
    /**
     * The window the host is in: used as the owner of dialogs, and for the render
     * scale, the window position and focus.
     */
    @OnThread(Tag.FX)
    Stage getStage();

    /**
     * Show the selected editor tab's menus (Class, Edit, ...) in the window's menu bar,
     * or none (an empty list) when a tab without menus, such as the World tab, is selected.
     */
    void showEditorMenus(List<Menu> menus);

    /**
     * Bring the window to the front (and restore it if it is minimised).
     */
    void bringHostToFront();

    /**
     * The name of the window, used when editors offer to move to it.
     */
    ObservableStringValue hostTitle();
}
