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

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * The SuperGreenfoot IDE's icons: line drawings on a 16 px grid, coloured by
 * the stylesheet (style class {@code sg-icon} for strokes, {@code sg-icon-fill}
 * for solid shapes).
 */
@OnThread(Tag.FXPlatform)
public final class SuperIcons
{
    public static final String LEAF = "M3 13c0-6 4-10 10-10 0 6-4 10-10 10z M3 13l6-6";
    public static final String CHEVRON_DOWN = "M4 6l4 4 4-4";
    public static final String CHEVRON_UP = "M4 9.5l4-4 4 4";
    public static final String CHEVRON_RIGHT = "M6 4l4 4-4 4";
    public static final String ACT = "M4 3.5l7 4.5-7 4.5z M13 3.5v9";
    public static final String PLAY = "M4.5 3v10l8.5-5z";
    public static final String PAUSE = "M5.5 3.5v9 M10.5 3.5v9";
    public static final String RESET = "M3 8a5 5 0 1 0 1.6-3.7 M3 2.5v3h3";
    public static final String CHECK = "M3.5 8.5l3 3 6-7";
    public static final String SUN = "M5 8a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M8 1.5v1.5 M8 13v1.5 M1.5 8H3 M13 8h1.5 "
            + "M3.4 3.4l1 1 M11.6 11.6l1 1 M3.4 12.6l1-1 M11.6 4.4l1-1";
    public static final String MOON = "M13.5 9.8A5.8 5.8 0 0 1 6.2 2.5a5.8 5.8 0 1 0 7.3 7.3z";
    public static final String SWITCH = "M2.5 5.5h10L10 3 M13.5 10.5h-10L6 13";
    public static final String SHARE = "M8 10V2 M5 5l3-3 3 3 M3 9v4.5h10V9";
    public static final String PLUS = "M8 3v10 M3 8h10";
    public static final String FOLDER = "M1.8 4.2h4.3l1.5 1.6h6.6v7H1.8z";
    public static final String FOLDER_PLUS = FOLDER + " M8 7.6v3.4 M6.3 9.3h3.4";
    /** Arrow into a bar on the right: collapse a left-hand panel. */
    public static final String COLLAPSE_LEFT = "M9.5 4L5.5 8l4 4 M12.5 3v10";
    /** Arrow into a bar on the left: expand a left-hand panel, or collapse a right-hand one. */
    public static final String COLLAPSE_RIGHT = "M6.5 4l4 4-4 4 M3.5 3v10";
    public static final String WORLD = "M3.4 3.2h9.2a1.6 1.6 0 0 1 1.6 1.6v6.4a1.6 1.6 0 0 1-1.6 1.6H3.4"
            + "a1.6 1.6 0 0 1-1.6-1.6V4.8a1.6 1.6 0 0 1 1.6-1.6z M1.8 10l3.4-3 3 2.4 2.3-1.8 3.7 2.9";
    public static final String FILE = "M4 1.8h5.2L12.5 5v9.2H4z M9 1.8V5h3.5";
    public static final String CLOSE = "M4 4l8 8 M12 4l-8 8";
    public static final String TERMINAL = "M2.2 3h11.6v10H2.2z M4.6 6.4l2 1.6-2 1.6 M8 10.2h3.2";
    public static final String CLEAR = "M2.5 8a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0 -11 0 M4.2 11.8l7.6-7.6";

    private SuperIcons()
    {
    }

    /** A stroked icon, size by size pixels. */
    public static StackPane icon(String path, double size)
    {
        return make(path, size, "sg-icon");
    }

    /** A solid (filled) icon, size by size pixels. */
    public static StackPane filledIcon(String path, double size)
    {
        return make(path, size, "sg-icon-fill");
    }

    private static StackPane make(String path, double size, String styleClass)
    {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.getStyleClass().add(styleClass);
        // An invisible 16x16 frame keeps the drawing's position on its grid
        // (the path alone would be centred on its own bounds):
        Rectangle frame = new Rectangle(16, 16, Color.TRANSPARENT);
        frame.setMouseTransparent(true);
        Group group = new Group(frame, shape);
        group.setScaleX(size / 16.0);
        group.setScaleY(size / 16.0);
        StackPane box = new StackPane(group);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    /** Find the shape inside an icon made here, e.g. to add a style class. */
    public static Node shapeOf(StackPane icon)
    {
        Group group = (Group) icon.getChildren().get(0);
        return group.getChildren().get(1);
    }
}
