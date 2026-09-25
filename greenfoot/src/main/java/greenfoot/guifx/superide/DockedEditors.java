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
package greenfoot.guifx.superide;

import bluej.Config;
import bluej.editor.stride.EditorHostSite;
import bluej.editor.stride.FXTabbedEditor;
import bluej.editor.stride.PinnedTab;
import bluej.pkgmgr.Project;
import bluej.utility.javafx.FXPlatformRunnable;
import javafx.beans.binding.StringExpression;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.ArrayList;
import java.util.List;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * The new IDE's docked editors for one project: an editor host inside the main
 * window, whose first tab is the World tab.  Class editors, the readme and
 * documentation open here as tabs, unless the user prefers editors in windows of
 * their own (see {@link #OWN_WINDOW_PREF}).
 */
@OnThread(Tag.FXPlatform)
public final class DockedEditors
{
    /** The user preference: open editors in windows of their own rather than docked. */
    public static final String OWN_WINDOW_PREF = "supergreenfoot.editors.ownWindow";

    private final Project project;
    private final FXTabbedEditor host;
    private final PinnedTab worldTab;

    /**
     * Editors that were the selected tab when their docks were put away by a switch to
     * the Classic IDE; {@link #focusMovedEditors()} brings them to the front once the
     * Classic windows are showing.
     */
    private static final List<Tab> movedActiveEditors = new ArrayList<>();

    /**
     * @param project           The project whose editors dock here
     * @param site              The window the editors are shown in
     * @param worldTabHeader    The World tab's header
     * @param worldArea         What the World tab shows
     * @param onWorldSelected   Run when the World tab is selected while the window has
     *                          focus, and when the window gains focus while it is selected
     * @param onWorldShown      Run just after the World tab is selected
     */
    public DockedEditors(Project project, EditorHostSite site, Node worldTabHeader, Node worldArea,
                         FXPlatformRunnable onWorldSelected, FXPlatformRunnable onWorldShown)
    {
        this.project = project;
        host = FXTabbedEditor.createEmbedded(project);
        worldTab = new PinnedTab(worldTabHeader, worldArea, onWorldSelected, null, onWorldShown);
        host.addTab(worldTab, true, false);
        host.getHostNode().getStyleClass().add("sg-editor-host");
        SuperTheme.installEditorHost(host.getHostNode());
        host.attach(site);
        project.setEmbeddedFXTabbedEditor(host);
        project.setOpenEditorsEmbedded(!isOwnWindowPreferred());
    }

    /**
     * Whether the user prefers editors in windows of their own.
     */
    public static boolean isOwnWindowPreferred()
    {
        return Config.getPropBoolean(OWN_WINDOW_PREF, false);
    }

    /**
     * Set whether editors open in windows of their own (applies to editors opened from now on).
     */
    public void setOwnWindowPreferred(boolean ownWindow)
    {
        Config.putPropString(OWN_WINDOW_PREF, Boolean.toString(ownWindow));
        project.setOpenEditorsEmbedded(!ownWindow);
    }

    /**
     * The node to show in the window's centre.
     */
    public Node getNode()
    {
        return host.getHostNode();
    }

    /**
     * Whether the World tab (rather than an editor) is the selected tab.
     */
    public boolean isWorldSelected()
    {
        return worldTab.isSelected();
    }

    /**
     * Select the World tab.
     */
    public void selectWorld()
    {
        TabPane tabPane = worldTab.getTabPane();
        if (tabPane != null)
        {
            tabPane.getSelectionModel().select(worldTab);
        }
    }

    /**
     * The title of the selected editor with the project, e.g. "Player - CoinQuest".
     */
    public StringExpression editorTitleProperty()
    {
        return host.editorTitleProperty();
    }

    /**
     * Dock the project's editor windows here: every tab of every standalone editor
     * window (except tutorial windows) moves into the main window, as it is (text, undo
     * history, unsaved changes).  Used when the project comes to the new IDE, e.g. on a
     * switch from the Classic IDE.  Does nothing while the user prefers editors in
     * windows of their own.
     *
     * @param reselect  If one of the windows was focused when the switch began, the tab
     *                  that was selected in it (it is selected here); otherwise null,
     *                  and the World tab stays selected.
     */
    public void dockStandaloneEditors(Tab reselect)
    {
        if (isOwnWindowPreferred())
        {
            return;
        }
        dockAll();
        if (reselect != null && FXTabbedEditor.hostOf(reselect) == host)
        {
            host.bringToFront(reselect);
        }
        else
        {
            selectWorld();
        }
    }

    /**
     * Dock every standalone editor window's tabs (except tutorial windows) here.
     */
    public void dockAll()
    {
        for (FXTabbedEditor window : new ArrayList<>(project.getAllFXTabbedEditorWindows()))
        {
            if (window != host && !window.hasTutorial() && window.hasEditorTabs())
            {
                window.moveEditorTabsTo(host);
            }
        }
    }

    /**
     * Put the docked editors away (the window stops showing this project).  Open
     * editors move, as they are and in order, to a window of their own, so nothing is
     * lost; the World tab lets go of the world area.  If an editor was the selected tab,
     * it is remembered for {@link #focusMovedEditors()}.
     *
     * @param projectClosed  true if the project has been closed: its editors are closed
     *                       already, and what is left (documentation tabs) is closed rather
     *                       than moved, since a window of a closed project would just appear
     */
    public void dispose(boolean projectClosed)
    {
        Tab active = isWorldSelected() ? null : host.getSelectedTab();
        // First, so that editors no longer open here or offer to move here:
        project.setEmbeddedFXTabbedEditor(null);
        if (projectClosed)
        {
            host.closeEditorTabs();
        }
        else if (host.hasEditorTabs())
        {
            // A hidden (empty) editor window if there is one, else a new one, so the
            // docked editors are not mixed into a torn-off window:
            FXTabbedEditor destination = null;
            for (FXTabbedEditor window : project.getAllFXTabbedEditorWindows())
            {
                if (!window.isWindowVisible() && !window.hasTutorial())
                {
                    destination = window;
                    break;
                }
            }
            if (destination == null)
            {
                destination = project.createNewFXTabbedEditor();
            }
            host.moveEditorTabsTo(destination);
            if (active != null && FXTabbedEditor.hostOf(active) == destination)
            {
                movedActiveEditors.add(active);
            }
        }
        host.detach();
        worldTab.setContent(null);
        host.close(worldTab);
        host.cleanup();
    }

    /**
     * Bring to the front the editors that were active in docks put away since the last
     * call (see {@link #dispose()}): after a switch to the Classic IDE, the editor the
     * user was working in stays in front of the Classic window.
     */
    public static void focusMovedEditors()
    {
        List<Tab> tabs = new ArrayList<>(movedActiveEditors);
        movedActiveEditors.clear();
        for (Tab tab : tabs)
        {
            FXTabbedEditor window = FXTabbedEditor.hostOf(tab);
            if (window != null)
            {
                window.bringToFront(tab);
            }
        }
    }

    /**
     * Forget editors remembered by {@link #dispose()} (when the windows were put away
     * for another reason than a switch of IDE).
     */
    public static void forgetMovedEditors()
    {
        movedActiveEditors.clear();
    }
}
