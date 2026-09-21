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
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.controller.ProjectRegistry;
import greenfoot.guifx.controller.UiMode;
import greenfoot.guifx.superide.SuperIcons;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * The bar across the top of the Classic Greenfoot IDE's class-diagram column,
 * above the classes.  Upstream put a single Share button there, stretched across
 * the whole column; this bar keeps that button and adds SuperGreenfoot's own
 * controls to its right.
 *
 * <p>This is the one place SuperGreenfoot adds to the Classic window.  Keeping the
 * bar here, rather than in {@link GreenfootStageContentPane}, means that adding a
 * control later touches no upstream file: the content pane only knows it has been
 * given some region to lay out, and everything of ours goes through
 * {@link #addTool(Node)}.
 *
 * <p>Width matters here.  The content pane will not let the class-diagram column be
 * narrower than this bar needs, so the bar is built to shrink: Share takes whatever
 * space is left over and gives it up (its label eventually shortens) before our own
 * controls do.
 */
@OnThread(Tag.FXPlatform)
public class SuperToolBar extends HBox
{
    private static final double SPACING = 6.0;
    private static final double ICON_SIZE = 14.0;

    /**
     * Create the bar.
     *
     * @param shareButton  upstream's Share button, which keeps the left of the bar
     */
    public SuperToolBar(Button shareButton)
    {
        super(SPACING);
        getStyleClass().add("super-tool-bar");
        setAlignment(Pos.CENTER_LEFT);

        // Share keeps the whole width it had before we added to the bar, less what
        // our own controls need, so it still reads as the column's heading:
        shareButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(shareButton, Priority.ALWAYS);

        getChildren().addAll(shareButton, makeSuperIdeButton());
    }

    /**
     * Add one of SuperGreenfoot's controls to the bar, to the left of the Super IDE
     * button (which stays at the right-hand end).  This is the reserved space: the
     * Classic window has room here for a few more controls, and putting one in needs
     * no change to any upstream file.
     *
     * @param control  the control to add
     */
    public void addTool(Node control)
    {
        // Size - 1 keeps the Super IDE button last:
        getChildren().add(getChildren().size() - 1, control);
    }

    /**
     * The button that moves every open scenario to the SuperGreenfoot IDE.  It is
     * never disabled: switching with no scenario open simply swaps the empty window,
     * which is what the Tools menu item does too.
     */
    private Button makeSuperIdeButton()
    {
        Button button = new Button(Config.getString("button.switchToSuper"));
        button.getStyleClass().add("switch-to-super-button");
        button.setGraphic(makeSwitchIcon(button));
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(Config.getString("button.switchToSuper.tooltip")));
        // Deferred, so the window holding this button is not torn down from inside
        // its own button press (UiModePreferencePanel defers the same call):
        button.setOnAction(e -> JavaFXUtil.runAfterCurrent(() -> ProjectRegistry.switchMode(UiMode.SUPER)));
        return button;
    }

    /**
     * The two-way arrow from the new IDE's icon set, drawn to match the button's own
     * text.  The strokes are set here rather than by a style class because the new
     * IDE's stylesheet, which normally colours these icons, is not loaded in Classic.
     *
     * @param button  the button the icon belongs to, whose text colour it follows
     */
    private static Node makeSwitchIcon(Button button)
    {
        StackPane icon = SuperIcons.icon(SuperIcons.SWITCH, ICON_SIZE);
        Shape shape = (Shape) SuperIcons.shapeOf(icon);
        shape.setFill(null);
        shape.setStrokeWidth(1.7);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        // Following the text keeps the icon right in the disabled and pressed looks:
        shape.strokeProperty().bind(button.textFillProperty());
        return icon;
    }
}
