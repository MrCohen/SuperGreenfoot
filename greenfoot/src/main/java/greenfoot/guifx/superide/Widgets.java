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

import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Locale;

/**
 * Small building blocks shared by the SuperGreenfoot IDE's panels.
 */
@OnThread(Tag.FXPlatform)
final class Widgets
{
    /** Tile colours for classes without an image: background and text, as in the mockup. */
    private static final String[][] TILE_COLOURS = {
        {"#3F74A3", "#FFFFFF"}, {"#E8A33D", "#2B2118"}, {"#6E716A", "#FFFFFF"}, {"#5DAF5A", "#10240F"},
        {"#7457A8", "#FFFFFF"}, {"#E9C23A", "#3A2C05"}, {"#C4443F", "#FFFFFF"}, {"#3F5F80", "#FFFFFF"},
        {"#A8433F", "#FFFFFF"}, {"#56625A", "#FFFFFF"}, {"#356894", "#FFFFFF"}, {"#72746C", "#FFFFFF"},
    };

    private Widgets()
    {
    }

    /** A borderless square button showing only an icon; the tooltip doubles as its accessible name. */
    static Button iconButton(String iconPath, String tooltip, double iconSize, Runnable action)
    {
        Button button = new Button();
        button.setGraphic(SuperIcons.icon(iconPath, iconSize));
        button.getStyleClass().add("sg-icon-btn");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.setFocusTraversable(true);
        button.setOnAction(e -> action.run());
        return button;
    }

    /** A bordered button with an optional icon. */
    static Button button(String text, String iconPath, Runnable action)
    {
        Button button = new Button(text);
        button.getStyleClass().add("sg-btn");
        if (iconPath != null)
        {
            button.setGraphic(SuperIcons.icon(iconPath, 14));
        }
        button.setOnAction(e -> action.run());
        return button;
    }

    /** A class's image, or a lettered tile if it has none. */
    static StackPane tile(ClassEntry entry, double size)
    {
        return tile(entry.getName(), entry.getImage(), size);
    }

    static StackPane tile(String name, Image image, double size)
    {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("sg-tile");
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        double radius = Math.round(size / 4);
        if (image != null && image.getWidth() > 0)
        {
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(size);
            view.setFitHeight(size);
            Rectangle clip = new Rectangle(size, size);
            clip.setArcWidth(radius * 2);
            clip.setArcHeight(radius * 2);
            tile.setClip(clip);
            tile.getChildren().add(view);
        }
        else
        {
            String[] colours = tileColours(name);
            tile.setStyle("-fx-background-color: " + colours[0] + "; -fx-background-radius: " + radius + ";");
            String initials = ClassTreeModel.initials(name);
            Label letters = new Label(initials);
            letters.setStyle("-fx-text-fill: " + colours[1] + "; -fx-font-size: "
                    + Math.round(size * (initials.length() > 1 ? 0.4 : 0.5)) + "px;");
            tile.getChildren().add(letters);
        }
        return tile;
    }

    /** A dashed placeholder tile for the built-in World and Actor classes. */
    static StackPane builtinTile(double size)
    {
        StackPane tile = new StackPane();
        tile.getStyleClass().addAll("sg-tile", "sg-tile-builtin");
        tile.setMinSize(size, size);
        tile.setPrefSize(size, size);
        tile.setMaxSize(size, size);
        return tile;
    }

    /** Deterministic tile colours for a class name. */
    static String[] tileColours(String name)
    {
        int hash = name == null ? 0 : name.hashCode();
        return TILE_COLOURS[Math.floorMod(hash, TILE_COLOURS.length)];
    }

    /** Small caps-style section heading. */
    static Label sectionLabel(String text)
    {
        Label label = new Label(text.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("sg-section-label");
        return label;
    }

    /** A region that grows to push siblings apart in an HBox. */
    static Region spacer()
    {
        Region spacer = new Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** The vertical "Classes · 14" label a collapsed panel shows. */
    static Button railLabel(Label label, Runnable action)
    {
        label.setRotate(-90);
        Group group = new Group(label);
        Button button = new Button();
        button.setGraphic(group);
        button.getStyleClass().add("sg-rail-label");
        button.setAccessibleRole(AccessibleRole.BUTTON);
        button.setOnAction(e -> action.run());
        button.setAlignment(Pos.CENTER);
        return button;
    }
}
