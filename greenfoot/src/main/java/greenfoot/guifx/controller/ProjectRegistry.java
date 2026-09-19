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
import bluej.pkgmgr.Project;
import bluej.utility.Utility;
import greenfoot.core.ProjectManager;
import greenfoot.guifx.GreenfootStage;
import greenfoot.vmcomm.GreenfootDebugHandler;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The open IDE windows, and the ways in and out of them: opening a project,
 * showing an empty window, finding the window for a project, and closing
 * everything at exit (which also writes the list of projects to reopen next time).
 *
 * This bookkeeping used to be static state in GreenfootStage.  It lives here so
 * that more than one kind of IDE window can be opened and closed the same way.
 */
public final class ProjectRegistry
{
    // Every open window, in the order it was opened.  The reopen list follows this order.
    private static final List<GreenfootStage> stages = new ArrayList<>();
    // The number of windows showing a project (at most one window can be empty):
    private static int numberOfOpenProjects = 0;

    private ProjectRegistry()
    {
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
        GreenfootStage.makeStage(new GreenfootProjectController(project, greenfootDebugHandler)).show();
    }

    /**
     * Show a window with no project in it (used at startup when there is nothing to reopen).
     *
     * @return  the window
     */
    public static Stage showEmptyWindow()
    {
        GreenfootStage stage = GreenfootStage.makeStage(null);
        stage.show();
        return stage;
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
    public static GreenfootStage findStageForProject(Project project)
    {
        for (GreenfootStage stage : stages)
        {
            if (stage.getProject() == project)
            {
                return stage;
            }
        }

        return null;
    }

    /**
     * Gets a Stage reference for an open window, or null if there are none.
     */
    public static Stage getOpenStage()
    {
        return stages.isEmpty() ? null : stages.get(0);
    }

    /**
     * Close all Greenfoot windows (before exit).
     */
    public static void closeAll()
    {
        Collection<GreenfootStage> stages_copy = new ArrayList<>(stages);

        // Save the list of open projects, to be re-opened next time:
        int i = 0;
        for (GreenfootStage stage : stages_copy)
        {
            if (stage.getProject() != null)
            {
                i++;
                Config.putPropString(Config.BLUEJ_OPENPACKAGE + i,
                        stage.getProject().getProjectDir().getPath());
            }
        }

        // Remove any extra open projects from the list:
        String exists;
        do {
            i++;
            exists = Config.removeProperty(Config.BLUEJ_OPENPACKAGE + i);
        } while (exists != null);

        // Close all stages:
        for (GreenfootStage stage : stages_copy)
        {
            // pass keepLast = true to avoid closing the final stage causing infinite recursion:
            stage.doClose(true);
        }
    }

    /**
     * If the only open window shows no project, return it, so a project can be shown in it.
     */
    public static GreenfootStage getSoleEmptyStage()
    {
        if (stages.size() == 1 && stages.get(0).getProject() == null)
        {
            return stages.get(0);
        }
        return null;
    }

    /**
     * A window was created.
     */
    public static void windowOpened(GreenfootStage stage)
    {
        stages.add(stage);
    }

    /**
     * A window was closed.
     */
    public static void windowClosed(GreenfootStage stage)
    {
        stages.remove(stage);
    }

    /**
     * A window started showing a project.
     */
    public static void projectOpened()
    {
        numberOfOpenProjects++;
    }

    /**
     * A window stopped showing a project.
     */
    public static void projectClosed()
    {
        numberOfOpenProjects--;
    }

    /**
     * The number of windows showing a project.
     */
    public static int getNumberOfOpenProjects()
    {
        return numberOfOpenProjects;
    }
}
