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
package greenfoot.guifx;

import bluej.Config;
import bluej.pkgmgr.Project;
import bluej.prefmgr.MiscPrefPanelItem;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.controller.ProjectRegistry;
import greenfoot.guifx.controller.UiMode;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * The Preferences dialog's choice between the Classic Greenfoot IDE and the
 * SuperGreenfoot IDE.  Choosing the other IDE and pressing OK switches every
 * open window over, keeping the scenarios running.
 */
@OnThread(Tag.FXPlatform)
public class UiModePreferencePanel extends VBox implements MiscPrefPanelItem
{
    private final RadioButton superButton = new RadioButton(Config.getString("prefmgr.uimode.super"));
    private final RadioButton classicButton = new RadioButton(Config.getString("prefmgr.uimode.classic"));

    public UiModePreferencePanel()
    {
        ToggleGroup group = new ToggleGroup();
        superButton.setToggleGroup(group);
        classicButton.setToggleGroup(group);
        Label note = new Label(Config.getString("prefmgr.uimode.note"));
        note.setWrapText(true);
        getChildren().addAll(superButton, classicButton, note);
        setSpacing(7);
        showMode(ProjectRegistry.getMode());
    }

    private void showMode(UiMode mode)
    {
        superButton.setSelected(mode == UiMode.SUPER);
        classicButton.setSelected(mode == UiMode.CLASSIC);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void beginEditing(Project project)
    {
        showMode(ProjectRegistry.getMode());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void commitEditing(Project project)
    {
        UiMode chosen = superButton.isSelected() ? UiMode.SUPER : UiMode.CLASSIC;
        if (chosen != ProjectRegistry.getMode())
        {
            // After the dialog has closed, so that no window it belongs to goes away under it:
            JavaFXUtil.runAfterCurrent(() -> ProjectRegistry.switchMode(chosen));
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void revertEditing(Project project)
    {
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public List<Node> getMiscPanelContents()
    {
        return List.of(this);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public String getMiscPanelTitle()
    {
        return Config.getString("prefmgr.uimode.title");
    }
}
