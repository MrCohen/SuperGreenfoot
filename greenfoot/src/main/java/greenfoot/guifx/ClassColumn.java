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

import bluej.utility.Debug;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.superide.folders.ProjectSettingsFile;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;

/**
 * The width of the Classic IDE's class-diagram column, and the handle that sets it.
 *
 * <p>Upstream sizes the column to fit the classes on every layout, so anything that
 * changes the widest class tile moves the world sideways; folding superclasses did so
 * constantly.  Instead:
 * <ul>
 * <li>until the user sets a width, the column grows to fit the classes but never
 *     shrinks back;</li>
 * <li>dragging the handle, the gap between the world and the column, sets the width
 *     exactly.  It is kept per scenario in {@value ProjectSettingsFile#FILE_NAME} as
 *     {@value #KEY_WIDTH};</li>
 * <li>double-clicking the handle forgets the width and fits the classes again.</li>
 * </ul>
 * The content pane still has the last word: it never lets the column push the world
 * narrower than the world's own size, nor the column fall below its minimum.
 */
@OnThread(Tag.FXPlatform)
class ClassColumn
{
    static final String KEY_WIDTH = "classic.classes.width";

    private final Region handle = new Region();
    // The scenario whose settings file holds the width (null: none, nothing is saved):
    private File projectDir;
    // The width the user chose (NaN: none, so the column fits the classes):
    private double userWidth = Double.NaN;
    // The widest the classes have needed since the scenario was shown or the width was reset:
    private double fitWidth = 0;
    // The width the content pane last gave the column:
    private double shownWidth = 0;
    private double dragStartScreenX;
    private double dragStartWidth;
    private boolean dragged;

    ClassColumn()
    {
        handle.getStyleClass().add("class-column-handle");
        handle.setCursor(Cursor.H_RESIZE);
        Tooltip.install(handle, new Tooltip("Drag to set the width of the classes."
                + " Double-click to fit the classes again."));
        // A faint line down the middle of the gap while the pointer is over it:
        JavaFXUtil.addChangeListenerPlatform(handle.hoverProperty(), this::showGrip);

        handle.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                dragStartScreenX = e.getScreenX();
                dragStartWidth = shownWidth;
                dragged = false;
                e.consume();
            }
        });
        handle.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown())
            {
                // The column is on the right, so dragging left widens it:
                userWidth = dragStartWidth - (e.getScreenX() - dragStartScreenX);
                dragged = true;
                requestLayout();
                e.consume();
            }
        });
        handle.setOnMouseReleased(e -> {
            if (dragged)
            {
                // Keep the width the column was given, which the content pane may have
                // limited, rather than wherever the pointer ended up:
                userWidth = shownWidth;
                saveWidth(Integer.toString((int) Math.round(userWidth)));
                dragged = false;
            }
        });
        handle.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
            {
                userWidth = Double.NaN;
                fitWidth = 0;
                saveWidth(null);
                requestLayout();
            }
        });
    }

    /**
     * The handle, for the content pane to place in the gap left of the column.
     */
    Region getHandle()
    {
        return handle;
    }

    /**
     * The width the column should have.
     *
     * @param fitsClasses  the width the classes need now
     * @return  the user's width if they set one, otherwise the widest the classes have
     *          needed so far
     */
    double widthFor(double fitsClasses)
    {
        fitWidth = Math.max(fitWidth, fitsClasses);
        return Double.isNaN(userWidth) ? fitWidth : userWidth;
    }

    /**
     * The content pane has laid the column out this wide.
     */
    void shown(double width)
    {
        shownWidth = width;
    }

    /**
     * The window now shows the scenario in the given folder (null: none).  Its saved
     * width, if any, applies from now on; otherwise the column fits its classes afresh.
     */
    void setProject(File projectDir)
    {
        this.projectDir = projectDir;
        userWidth = Double.NaN;
        fitWidth = 0;
        if (projectDir != null)
        {
            String saved = ProjectSettingsFile.load(projectDir).get(KEY_WIDTH);
            if (saved != null)
            {
                try
                {
                    userWidth = Math.max(0, Integer.parseInt(saved.trim()));
                }
                catch (NumberFormatException e)
                {
                    // Not a width; fit the classes instead.
                }
            }
        }
        requestLayout();
    }

    private void requestLayout()
    {
        Parent parent = handle.getParent();
        if (parent != null)
        {
            parent.requestLayout();
        }
    }

    private void showGrip(boolean show)
    {
        handle.setStyle(show ? "-fx-background-color: linear-gradient(to right,"
                + " transparent 45%, rgba(0, 0, 0, 0.22) 45%, rgba(0, 0, 0, 0.22) 55%, transparent 55%);"
                : "");
    }

    /**
     * Write the width to the settings file (null removes it).  The file is read afresh
     * first, so everything else in it is kept as last written.
     */
    private void saveWidth(String value)
    {
        if (projectDir == null)
        {
            return;
        }
        ProjectSettingsFile settings = ProjectSettingsFile.load(projectDir);
        settings.put(KEY_WIDTH, value);
        try
        {
            settings.save();
        }
        catch (IOException e)
        {
            Debug.reportError("Could not save " + settings.getFile(), e);
        }
    }
}
