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
package greenfoot.guifx.classes;

import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.superide.SuperIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * The fold controls of a superclass in the Classic class diagram: an arrow on the
 * inheritance line just left of its tile, and, while the class is folded, a pill in
 * its tile right of the name with small tiles of the hidden subclasses and how many
 * classes are hidden.
 *
 * <p>The arrow sits in the indent beside the tile, which is empty space, so that
 * folding costs the column no width; only the pill of a folded class does.
 *
 * <p>They follow the SuperGreenfoot IDE's approved superclass-group design ("stacked
 * tiles"), drawn in Classic's own look: the small tiles are miniatures of Classic's
 * class tiles, showing a subclass's image where it has one.  Colours are set here
 * rather than in greenfoot.css so that the stylesheet stays as upstream wrote it.
 */
@OnThread(Tag.FXPlatform)
class FoldControls
{
    /**
     * The arrow's box.  It fills the 9 pixels between a superclass's inheritance line
     * and the tile (the arm running into the tile, which the box covers), and is taller
     * than wide so that it is easier to hit.
     */
    static final double ARROW_WIDTH = 9.0;
    static final double ARROW_HEIGHT = 14.0;
    private static final double ARROW_SIZE = 12.0;
    private static final Color ARROW_INK = Color.rgb(95, 95, 95);
    private static final double MINI_TILE_SIZE = 12.0;
    // A ring of the pill's colour round each small tile, so overlapping tiles stay apart:
    private static final double MINI_TILE_RING = 1.5;
    // Each small tile starts this far right of the one before it:
    private static final double MINI_TILE_STEP = 8.0;
    private static final int MINI_TILES_SHOWN = 3;
    // Classic's class tile colour (.class-display in greenfoot.css) and a near-black outline:
    private static final Color TILE_FILL = Color.rgb(245, 204, 155);
    private static final Color INK = Color.rgb(50, 50, 50);
    // The pill: the tile colour lightened, opaque so the rings match it exactly:
    private static final Color PILL_FILL = Color.rgb(250, 232, 210);

    private FoldControls()
    {
    }

    /**
     * The arrow on the inheritance line left of a superclass's tile: pointing right while
     * folded, down while open.  It takes the place of the short arm from the line into the
     * tile (its back is the diagram's white, covering the arm).  Clicking it folds or unfolds the class and
     * does nothing else (it is not part of the tile, so it neither selects the class nor
     * opens its editor).
     *
     * @param displayName  the class's name, for the tooltip
     * @param folded  whether the class is folded now
     * @param toggle  what a click does
     */
    static Node arrow(String displayName, boolean folded, FXPlatformRunnable toggle)
    {
        StackPane icon = SuperIcons.icon(SuperIcons.CHEVRON_RIGHT, ARROW_SIZE);
        Shape shape = (Shape) SuperIcons.shapeOf(icon);
        shape.setFill(null);
        shape.setStroke(ARROW_INK);
        shape.setStrokeWidth(2.4);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        icon.setRotate(folded ? 0 : 90);

        // The icon itself ignores the mouse; this box around it is the click target.  The
        // icon's frame is wider than the box, but only its invisible edges stick out:
        StackPane arrow = new StackPane(icon);
        arrow.setMinSize(ARROW_WIDTH, ARROW_HEIGHT);
        arrow.setPrefSize(ARROW_WIDTH, ARROW_HEIGHT);
        arrow.setMaxSize(ARROW_WIDTH, ARROW_HEIGHT);
        arrow.setCursor(Cursor.HAND);
        arrow.setStyle("-fx-background-color: white;");
        JavaFXUtil.addChangeListenerPlatform(arrow.hoverProperty(), now -> shape.setStroke(now ? Color.BLACK : ARROW_INK));
        String label = (folded ? "Show " : "Hide ") + displayName + "’s subclasses";
        Tooltip.install(arrow, new Tooltip(label));
        arrow.setAccessibleRole(AccessibleRole.BUTTON);
        arrow.setAccessibleText(label);
        // Consumed, so that the diagram's own context menu and clicks never see it:
        arrow.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                toggle.run();
            }
            e.consume();
        });
        return arrow;
    }

    /**
     * The pill shown right of a folded superclass's name: small tiles of its first few
     * subclasses, overlapping like a stack, and how many classes are hidden beneath it
     * (subclasses of subclasses included).  Clicks pass through to the tile, so the
     * pill selects and opens the class like the rest of it.
     */
    static Node foldedSummary(GClassNode folded)
    {
        double ringed = MINI_TILE_SIZE + 2 * MINI_TILE_RING;
        HBox stack = new HBox(MINI_TILE_STEP - ringed);
        stack.setAlignment(Pos.CENTER_LEFT);
        List<GClassNode> subs = folded.getSubClasses();
        for (int i = 0; i < Math.min(MINI_TILES_SHOWN, subs.size()); i++)
        {
            stack.getChildren().add(miniTile(subs.get(i)));
        }

        int hidden = ClassFolds.countBeneath(folded);
        Label count = new Label(Integer.toString(hidden));
        count.setStyle("-fx-font-size: 10.5px; -fx-text-fill: rgb(50, 50, 50);");

        HBox pill = new HBox(4.0, stack, count);
        pill.setAlignment(Pos.CENTER);
        pill.setStyle("-fx-padding: 1 6 1 4;"
                + " -fx-background-color: rgb(250, 232, 210); -fx-background-radius: 9;"
                + " -fx-border-color: rgba(0, 0, 0, 0.35); -fx-border-radius: 9;");
        Tooltip.install(pill, new Tooltip(hidden + (hidden == 1 ? " subclass" : " subclasses")
                + " under " + folded.getDisplayName()));
        BorderPane.setAlignment(pill, Pos.CENTER);
        BorderPane.setMargin(pill, new Insets(0, 0, 0, 6));
        return pill;
    }

    /**
     * A miniature Classic class tile for one hidden subclass: the tile colour with an
     * outline, and the subclass's image inside it if it has one.
     */
    private static Node miniTile(GClassNode sub)
    {
        Rectangle tile = new Rectangle(MINI_TILE_SIZE, MINI_TILE_SIZE, TILE_FILL);
        tile.setStroke(INK);
        tile.setStrokeType(StrokeType.INSIDE);
        tile.setStrokeWidth(1.0);
        tile.setArcWidth(3.0);
        tile.setArcHeight(3.0);
        double ringed = MINI_TILE_SIZE + 2 * MINI_TILE_RING;
        Rectangle ring = new Rectangle(ringed, ringed, PILL_FILL);
        ring.setArcWidth(3.0 + 2 * MINI_TILE_RING);
        ring.setArcHeight(3.0 + 2 * MINI_TILE_RING);
        StackPane box = new StackPane(ring, tile);
        if (sub.image != null)
        {
            ImageView imageView = new ImageView(sub.image);
            imageView.setFitWidth(MINI_TILE_SIZE - 3);
            imageView.setFitHeight(MINI_TILE_SIZE - 3);
            imageView.setPreserveRatio(true);
            box.getChildren().add(imageView);
        }
        return box;
    }

    /**
     * What the fold controls for a class show, as a string that changes exactly when
     * they need drawing again.  The diagram lays itself out again whenever any tile
     * changes size, and new controls change a tile's size, so rebuilding them on every
     * layout would never settle.
     */
    static String stateOf(GClassNode classInfo, boolean folded)
    {
        if (classInfo.getSubClasses().isEmpty())
        {
            return "";
        }
        if (!folded)
        {
            return "open";
        }
        StringBuilder state = new StringBuilder("folded ").append(ClassFolds.countBeneath(classInfo));
        List<GClassNode> subs = classInfo.getSubClasses();
        for (int i = 0; i < Math.min(MINI_TILES_SHOWN, subs.size()); i++)
        {
            GClassNode sub = subs.get(i);
            state.append(' ').append(sub.getQualifiedName())
                    .append('@').append(System.identityHashCode(sub.image));
        }
        return state.toString();
    }
}
