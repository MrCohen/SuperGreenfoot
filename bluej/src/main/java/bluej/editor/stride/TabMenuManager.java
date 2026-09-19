/*
 This file is part of the BlueJ program.
 Copyright (C) 2015,2016,2019 Michael Kolling and John Rosenberg

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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import bluej.compiler.CompileReason;
import bluej.compiler.CompileType;
import bluej.Config;
import bluej.utility.javafx.JavaFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 26/11/15.
 */
public abstract class TabMenuManager
{
    protected final FXTab tab;
    private final MenuItem contextMoveNew;
    private final Menu contextMoveMenu;
    protected final Menu mainMoveMenu;
    private final MenuItem mainMoveNew;
    /** While the project docks editors in the new IDE: move a torn-off tab back into the main window */
    private final MenuItem contextDock;
    private final MenuItem mainDock;
    /** Shift+Shortcut+D: dock a torn-off tab, or tear a docked one off (new IDE only) */
    private static final KeyCombination DOCK_KEY = new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    @OnThread(Tag.FXPlatform)
    public TabMenuManager(FXTab tab)
    {
        this.tab = tab;
        contextMoveMenu = JavaFXUtil.makeMenu(Config.getString("frame.classmenu.move"));
        contextMoveNew = JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.move.new"), () -> tab.getParent().moveToNewLater(tab), null);
        mainMoveNew = JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.move.new"), () -> tab.getParent().moveToNewLater(tab), null);
        mainMoveMenu = JavaFXUtil.makeMenu(Config.getString("frame.classmenu.move"));
        contextDock = JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.move.dock", "Dock in Main Window"), () -> dock(), null);
        mainDock = JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.move.dock", "Dock in Main Window"), () -> dock(), null);

        // We may not have a parent yet, so use runLater:
        JavaFXUtil.runAfterCurrent(() -> {
            if (tab.getParent() != null)
                updateMoveMenus();

            tab.setContextMenu(new ContextMenu(
                    contextMoveMenu
                    , JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.close"), () -> tab.getParent().close(tab), null)
            ));

            if (tab instanceof FrameEditorTab)
                tab.getContextMenu().getItems().add(JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.compile"),
                        () ->  ((FrameEditorTab)tab).getFrameEditor().getWatcher().scheduleCompilation(true, CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE)
                        , null
                ));

            if (tab instanceof FlowFXTab)
                tab.getContextMenu().getItems().add(JavaFXUtil.makeMenuItem(Config.getString("frame.classmenu.compile"),
                        () ->  ((FlowFXTab)tab).getFlowEditor().scheduleCompilation(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE)
                        , null
                ));
        });
    }

    @OnThread(Tag.FXPlatform)
    protected void updateMoveMenus()
    {
        // Have to do everything double because the menus don't share:
        ArrayList<MenuItem> classMoveItems = new ArrayList<>();
        ArrayList<MenuItem> contextMoveItems = new ArrayList<>();
        // While the project docks editors in the new IDE's main window, a torn-off tab
        // offers to dock, and Shift+Shortcut+D docks it or (for a docked tab) tears it off:
        FXTabbedEditor dockHost = tab.getParent().getProject().getEmbeddedFXTabbedEditor();
        boolean docked = dockHost != null && dockHost == tab.getParent();
        if (dockHost != null && !docked)
        {
            classMoveItems.add(mainDock);
            contextMoveItems.add(contextDock);
        }
        mainDock.setAccelerator(dockHost != null && !docked ? DOCK_KEY : null);
        mainMoveNew.setAccelerator(docked ? DOCK_KEY : null);
        classMoveItems.add(mainMoveNew);
        contextMoveItems.add(contextMoveNew);
        mainMoveNew.setDisable(tab.getParent().hasOneTab());
        contextMoveNew.setDisable(tab.getParent().hasOneTab());
        List<FXTabbedEditor> allWindows = tab.getParent().getProject().getAllFXTabbedEditorWindows();
        // (While docking, the main window is offered above, and hidden, empty editor
        // windows are not offered:)
        List<FXTabbedEditor> otherWindows = allWindows.stream()
                .filter(w -> w != tab.getParent() && (dockHost == null || (w != dockHost && w.isWindowVisible())))
                .collect(Collectors.toList());
        if (!otherWindows.isEmpty())
        {
            classMoveItems.add(new SeparatorMenuItem());
            contextMoveItems.add(new SeparatorMenuItem());
            otherWindows.forEach(w -> {
                StringExpression itemText = new ReadOnlyStringWrapper(Config.getString("frame.classmenu.move.existing") + ": ").concat(w.titleProperty());
                contextMoveItems.add(JavaFXUtil.makeMenuItem(itemText, () -> tab.getParent().moveTabTo(tab, w), null));
                classMoveItems.add(JavaFXUtil.makeMenuItem(itemText, () -> tab.getParent().moveTabTo(tab, w), null));
            });
        }

        if (mainMoveMenu != null)
        {
            mainMoveMenu.getItems().setAll(classMoveItems);
        }
        if (contextMoveMenu != null)
        {
            contextMoveMenu.getItems().setAll(contextMoveItems);
        }
    }

    /**
     * Move this tab into the new IDE's main window (if the project has one).
     */
    @OnThread(Tag.FXPlatform)
    private void dock()
    {
        FXTabbedEditor dockHost = tab.getParent().getProject().getEmbeddedFXTabbedEditor();
        if (dockHost != null && dockHost != tab.getParent())
        {
            tab.getParent().moveTabTo(tab, dockHost);
        }
    }

    @OnThread(Tag.FXPlatform)
    abstract List<Menu> getMenus();
}
