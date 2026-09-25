/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2017,2018,2019,2019,2020,2021,2022,2023,2024  Poul Henriksen and Michael Kolling
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

import bluej.Config;
import bluej.Main;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.ProjectLayoutStore;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.Utility;
import greenfoot.core.ProjectManager;
import bluej.editor.stride.FXTabbedEditor;
import greenfoot.guifx.GreenfootStage;
import greenfoot.guifx.superide.DockedEditors;
import greenfoot.guifx.superide.SuperIdeWindow;
import greenfoot.vmcomm.GreenfootDebugHandler;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The open IDE windows, and the ways in and out of them: opening a project,
 * showing an empty window, finding the window for a project, closing a window
 * (and the project in it), moving a project to a new window without closing it,
 * switching every window between the Classic IDE and the SuperGreenfoot IDE,
 * and closing everything at exit (which also writes the list of projects to
 * reopen next time).
 *
 * This bookkeeping used to be static state in GreenfootStage.  It lives here so
 * that more than one kind of IDE window can be opened and closed the same way.
 */
public final class ProjectRegistry
{
    // Every open window, in the order it was opened.  The reopen list follows this order.
    private static final List<IdeWindow> windows = new ArrayList<>();
    // The number of windows showing a project (at most one window can be empty):
    private static int numberOfOpenProjects = 0;
    // The IDE that new windows belong to (read from the preferences when first needed):
    private static UiMode mode = null;

    private ProjectRegistry()
    {
    }

    /**
     * The IDE the user works in (the Classic Greenfoot IDE or the SuperGreenfoot IDE).
     * The first call reads the user's preference and sets up that IDE's look.
     */
    public static UiMode getMode()
    {
        if (mode == null)
        {
            mode = UiMode.fromPreferences();
            applyLook(mode);
        }
        return mode;
    }

    private static void applyLook(UiMode uiMode)
    {
        if (uiMode == UiMode.SUPER)
        {
            SuperIdeWindow.activateLook();
        }
        else
        {
            SuperIdeWindow.deactivateLook();
        }
    }

    /**
     * Show a newly opened project: in the single empty window if that is all
     * there is, otherwise in a new window.
     *
     * @param project   The project to display
     * @param greenfootDebugHandler   The debug handler for this project
     */
    public static void open(Project project, GreenfootDebugHandler greenfootDebugHandler)
    {
        GreenfootProjectController controller = new GreenfootProjectController(project, greenfootDebugHandler);
        IdeWindow empty = getSoleEmptyWindow();
        IdeWindow window;
        if (empty != null && empty.getUiMode() == getMode())
        {
            empty.showProject(controller);
            window = empty;
        }
        else
        {
            window = newWindow(getMode(), controller);
        }
        window.getWindow().show();
    }

    /**
     * Make a new window of the given IDE, showing the given controller's project (or
     * none, if the controller is null).  The window registers itself as it is made.
     */
    private static IdeWindow newWindow(UiMode uiMode, GreenfootProjectController controller)
    {
        if (uiMode == UiMode.SUPER)
        {
            return new SuperIdeWindow(controller);
        }
        else
        {
            return GreenfootStage.createWindow(controller);
        }
    }

    /**
     * Show a window with no project in it (used at startup when there is nothing to reopen).
     *
     * @return  the window
     */
    public static Stage showEmptyWindow()
    {
        IdeWindow window = newWindow(getMode(), null);
        window.getWindow().show();
        return window.getWindow();
    }

    /**
     * Open a gfar archive file as a Greenfoot project.
     * The file contents are extracted, the containing directory
     * is then converted into a Greenfoot project, and opened.
     * Opening already extracted gfar will show an error message.
     * @param archive The chosen archived file.
     * @param window  The parent javafx window to show error message if needed. It could be null.
     * @return A true value if the archived is open successfully or false otherwise.
     */
    public static boolean openArchive(File archive, Window window)
    {
        // Determine the output path.
        File oPath = Utility.maybeExtractArchive(archive, () -> window);

        if (oPath != null && Project.isProject(oPath.getPath()))
        {
            ProjectManager.instance().launchProject(Project.openProject(oPath.toString()));
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Find the window currently showing the specified project (if any).
     */
    public static IdeWindow findWindowForProject(Project project)
    {
        for (IdeWindow window : windows)
        {
            if (window.getProject() == project)
            {
                return window;
            }
        }

        return null;
    }

    /**
     * Gets a Stage reference for an open window, or null if there are none.
     */
    public static Stage getOpenStage()
    {
        return windows.isEmpty() ? null : windows.get(0).getWindow();
    }

    /**
     * Close all Greenfoot windows (before exit).
     */
    public static void closeAll()
    {
        Collection<IdeWindow> windowsCopy = new ArrayList<>(windows);

        // Save the list of open projects, to be re-opened next time:
        int i = 0;
        for (IdeWindow window : windowsCopy)
        {
            if (window.getProject() != null)
            {
                i++;
                Config.putPropString(Config.BLUEJ_OPENPACKAGE + i,
                        window.getProject().getProjectDir().getPath());
            }
        }

        // Remove any extra open projects from the list:
        String exists;
        do {
            i++;
            exists = Config.removeProperty(Config.BLUEJ_OPENPACKAGE + i);
        } while (exists != null);

        // Close all windows:
        for (IdeWindow window : windowsCopy)
        {
            // pass keepLast = true to avoid closing the final window causing infinite recursion:
            closeWindow(window, true);
        }
    }

    /**
     * Close a window, and the project it shows (if any).
     *
     * @param window    The window
     * @param keepLast  if true, don't close the last window; leave it open without a project. If
     *                  false, quit when the last window is closed.
     */
    public static void closeWindow(IdeWindow window, boolean keepLast)
    {
        GreenfootProjectController controller = window.getController();
        if (controller != null)
        {
            controller.exitFullScreenView();
            controller.projectClosing();
        }

        if (numberOfOpenProjects <= 1 && ! keepLast)
        {
            // We quit with the current scenario still open, so that it will be saved to the
            // projects-for-re-opening list and re-opened when Greenfoot is next started:
            window.saveWindowSettings();
            window.getWindow().close();
            Main.doQuit();
            return;
        }

        // Remove inspectors, terminal, etc:
        if (controller != null)
        {
            controller.closeProject();
            numberOfOpenProjects--;
        }

        if (numberOfOpenProjects == 0)
        {
            // Keep this window open, but show it as empty
            window.showNoProject();
            if (controller != null)
            {
                controller.dispose();
            }
        }
        else
        {
            if (controller != null)
            {
                // Stop polling the closed project's debug VM:
                controller.dispose();
            }
            windows.remove(window);
            window.getWindow().close();
        }
    }

    /**
     * Close a project, and its window (the window stays open, empty, if it is the last one).
     *
     * @param controller  The project's controller
     * @param keepLast    if true, don't close the last window; leave it open without a project.
     *                    If false, quit when the last window is closed.
     */
    public static void close(GreenfootProjectController controller, boolean keepLast)
    {
        IdeWindow window = findWindowForProject(controller.getProject());
        if (window != null)
        {
            closeWindow(window, keepLast);
        }
    }

    /**
     * The reason a project cannot move to another window right now, or null if it can.
     */
    private static String whyCannotMove(GreenfootProjectController controller)
    {
        if (controller != null && controller.isInvocationRunning())
        {
            return "A method call in " + controller.getProject().getProjectName()
                    + " is still running. Switch when it has finished.";
        }
        return null;
    }

    /**
     * Show the project from the given window in a new window instead, without closing it:
     * the debug VM keeps running and the world keeps its state.  The new window takes the
     * old one's place and position.  This is refused (returning false) while an
     * interactive call is running, because its result would be shown relative to the old
     * window.
     *
     * @param old  The window showing the project
     * @return  true if the project moved to a new window
     */
    public static boolean reopenInNewWindow(IdeWindow old)
    {
        GreenfootProjectController controller = old.getController();
        if (controller == null || !controller.isStarted())
        {
            return false;
        }
        if (whyCannotMove(controller) != null)
        {
            Debug.message("Not moving " + controller.getProject().getProjectName()
                    + " to a new window while an interactive call is running");
            return false;
        }

        Stage oldStage = old.getWindow();
        double x = oldStage.getX(), y = oldStage.getY(), width = oldStage.getWidth(), height = oldStage.getHeight();
        IdeWindow fresh = replaceWindow(old, old.getUiMode());
        Stage freshStage = fresh.getWindow();
        freshStage.setX(x);
        freshStage.setY(y);
        freshStage.setWidth(width);
        freshStage.setHeight(height);
        freshStage.show();
        return true;
    }

    /**
     * Replace a window by a new window of the given IDE showing the same project (if
     * any), keeping its place in the reopen order.  The project stays live.  The new
     * window is not yet shown.
     */
    private static IdeWindow replaceWindow(IdeWindow old, UiMode uiMode)
    {
        GreenfootProjectController controller = old.getController();
        int index = windows.indexOf(old);
        old.saveWindowSettings();
        // The geometry just went to the layout store; write it now, as a
        // project save would, so a switch followed by a crash keeps it.
        ProjectLayoutStore.get().flush();
        if (controller != null)
        {
            old.detachProject();
        }
        windows.remove(old);
        old.getWindow().close();

        // The new window attaches to the running controller, which brings it up to date:
        IdeWindow fresh = newWindow(uiMode, controller);
        // Keep the reopen-list order:
        windows.remove(fresh);
        windows.add(Math.max(0, Math.min(index, windows.size())), fresh);
        return fresh;
    }

    /**
     * Switch every window to the given IDE (the Classic Greenfoot IDE or the
     * SuperGreenfoot IDE), and remember the choice.  Projects stay open: their debug
     * VMs keep running and their worlds keep their state.  This is refused, with a
     * message, while an interactive method call is running in any project.
     *
     * @param target  the IDE to switch to
     * @return  true if every window now belongs to that IDE
     */
    public static boolean switchMode(UiMode target)
    {
        for (IdeWindow window : windows)
        {
            String problem = whyCannotMove(window.getController());
            if (problem != null)
            {
                DialogManager.showErrorTextFX(window.getWindow(), problem);
                return false;
            }
        }

        target.saveToPreferences();
        if (target == getMode() && windows.stream().allMatch(w -> w.getUiMode() == target))
        {
            return true;
        }

        // If an editor window is the focused window, its selected tab stays selected
        // when the new IDE docks it:
        Tab focusedEditorTab = null;
        for (IdeWindow window : windows)
        {
            Project project = window.getProject();
            if (project == null)
            {
                continue;
            }
            for (FXTabbedEditor editorWindow : project.getAllFXTabbedEditorWindows())
            {
                if (!editorWindow.isEmbedded() && editorWindow.getStage() != null && editorWindow.getStage().isFocused())
                {
                    focusedEditorTab = editorWindow.getSelectedTab();
                }
            }
        }
        DockedEditors.forgetMovedEditors();

        // Let go of every window first, so that no window of the old IDE is showing
        // when the look changes (the two IDEs' stylesheets do not mix):
        List<GreenfootProjectController> controllers = new ArrayList<>();
        int focusedIndex = 0;
        for (IdeWindow old : new ArrayList<>(windows))
        {
            if (old.isWindowFocused())
            {
                focusedIndex = controllers.size();
            }
            old.saveWindowSettings();
            ProjectLayoutStore.get().flush();
            GreenfootProjectController controller = old.getController();
            if (controller != null)
            {
                old.detachProject();
            }
            controllers.add(controller);
            windows.remove(old);
            old.getWindow().close();
        }

        mode = target;
        applyLook(target);

        // The new windows attach to the running controllers, which bring them up to date:
        List<IdeWindow> fresh = new ArrayList<>();
        for (GreenfootProjectController controller : controllers)
        {
            IdeWindow window = newWindow(target, controller);
            window.getWindow().show();
            fresh.add(window);
        }
        if (!fresh.isEmpty())
        {
            Stage focus = fresh.get(Math.min(focusedIndex, fresh.size() - 1)).getWindow();
            focus.toFront();
            focus.requestFocus();
        }
        // The project's editors go where the new IDE keeps them: docked in the new IDE
        // (re-selecting the editor that was focused), or back in windows of their own in
        // the Classic IDE, with the one the user was working in at the front:
        for (IdeWindow window : fresh)
        {
            if (window instanceof SuperIdeWindow)
            {
                ((SuperIdeWindow) window).dockStandaloneEditors(focusedEditorTab);
            }
        }
        DockedEditors.focusMovedEditors();
        return true;
    }

    /**
     * If the only open window shows no project, return it, so a project can be shown in it.
     */
    public static IdeWindow getSoleEmptyWindow()
    {
        if (windows.size() == 1 && windows.get(0).getProject() == null)
        {
            return windows.get(0);
        }
        return null;
    }

    /**
     * A window was created.
     */
    public static void windowOpened(IdeWindow window)
    {
        windows.add(window);
    }

    /**
     * A window started showing a project.
     */
    public static void projectOpened()
    {
        numberOfOpenProjects++;
    }
}
