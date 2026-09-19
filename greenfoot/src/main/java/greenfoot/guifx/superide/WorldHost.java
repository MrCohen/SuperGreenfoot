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

import bluej.utility.javafx.JavaFXUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * The centre of the SuperGreenfoot IDE: shows the world scaled to fit the
 * space the panels leave, so collapsing a panel makes the world bigger.
 *
 * <p>As in the full-screen view, a whole-number scale is drawn crisp
 * (nearest-neighbour) and any other scale smooth. Mouse events reach the
 * world node in its own (unscaled) coordinates, because the scale is a
 * transform on that node.
 */
@OnThread(Tag.FXPlatform)
public class WorldHost extends Region
{
    /** How the world is scaled into the available space. */
    @OnThread(Tag.Any)
    public enum Zoom
    {
        /** As large as fits, any factor. */
        FIT,
        /** The largest whole-number factor that fits (never below fitting, if the world is too big). */
        PIXEL_PERFECT
    }

    /** Space kept free around the world. */
    static final double MARGIN_X = 24;
    static final double MARGIN_Y = 20;

    private final ObjectProperty<Zoom> zoom = new SimpleObjectProperty<>(Zoom.FIT);
    private final ReadOnlyDoubleWrapper scale = new ReadOnlyDoubleWrapper(1.0);
    private final Region frame = new Region();
    private final Rectangle worldClip = new Rectangle();
    private Node world;
    private double worldWidth;
    private double worldHeight;

    public WorldHost()
    {
        getStyleClass().add("sg-world-area");
        frame.getStyleClass().add("sg-world-frame");
        frame.setManaged(false);
        frame.setVisible(false);
        getChildren().add(frame);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        JavaFXUtil.addChangeListenerPlatform(zoom, newZoom -> requestLayout());
        setMinSize(0, 0);
    }

    /**
     * Show a world. The node is laid out at width x height (the world's size in
     * pixels) and scaled; pass null to show nothing.
     */
    public void setWorld(Node node, double width, double height)
    {
        if (world != null)
        {
            world.setClip(null);
            getChildren().remove(world);
        }
        world = node;
        worldWidth = width;
        worldHeight = height;
        if (world != null)
        {
            world.setManaged(false);
            world.setClip(worldClip);
            getChildren().add(world);
        }
        frame.setVisible(world != null);
        requestLayout();
    }

    /** The world changed size (e.g. a new world with different dimensions). */
    public void setWorldSize(double width, double height)
    {
        worldWidth = width;
        worldHeight = height;
        requestLayout();
    }

    public double getWorldWidth()
    {
        return worldWidth;
    }

    public double getWorldHeight()
    {
        return worldHeight;
    }

    public ObjectProperty<Zoom> zoomProperty()
    {
        return zoom;
    }

    /** The current world-to-screen factor. */
    public ReadOnlyDoubleProperty scaleProperty()
    {
        return scale.getReadOnlyProperty();
    }

    /**
     * For the tab strip: "Fit · 86%", "Pixel-perfect · 2×", or, when the world
     * is too big for a whole-number factor, "Pixel-perfect · 86%".
     */
    public String describeScale()
    {
        double s = scale.get();
        if (zoom.get() == Zoom.PIXEL_PERFECT)
        {
            return "Pixel-perfect · " + (isWholeScale(s) ? Math.round(s) + "×" : Math.round(s * 100) + "%");
        }
        return "Fit · " + Math.round(s * 100) + "%";
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        if (world == null || worldWidth <= 0 || worldHeight <= 0)
        {
            return;
        }
        double availW = getWidth() - 2 * MARGIN_X;
        double availH = getHeight() - 2 * MARGIN_Y;
        double s = computeScale(zoom.get(), availW, availH, worldWidth, worldHeight);
        scale.set(s);
        boolean smooth = !isWholeScale(s);
        setSmooth(world, smooth);

        double shownW = worldWidth * s;
        double shownH = worldHeight * s;
        double x = snapPositionX((getWidth() - shownW) / 2);
        double y = snapPositionY((getHeight() - shownH) / 2);
        frame.resizeRelocate(x, y, shownW, shownH);

        // The node is laid out unscaled and scaled about its centre, so centring
        // its unscaled box centres the scaled picture too:
        world.resizeRelocate((getWidth() - worldWidth) / 2, (getHeight() - worldHeight) / 2, worldWidth, worldHeight);
        world.setScaleX(s);
        world.setScaleY(s);
        double arc = 12 / s;
        worldClip.setWidth(worldWidth);
        worldClip.setHeight(worldHeight);
        worldClip.setArcWidth(arc);
        worldClip.setArcHeight(arc);
    }

    /**
     * The factor to draw a w x h world at in a spaceW x spaceH space.
     * (Parameter names differ from the fields on purpose: the thread checker
     * mistakes same-named parameters for the FX-thread fields.)
     */
    @OnThread(Tag.Any)
    public static double computeScale(Zoom mode, double spaceW, double spaceH, double w, double h)
    {
        if (w <= 0 || h <= 0 || spaceW <= 0 || spaceH <= 0)
        {
            return 1.0;
        }
        double fit = Math.min(spaceW / w, spaceH / h);
        if (mode == Zoom.PIXEL_PERFECT && fit >= 1.0)
        {
            return Math.floor(fit);
        }
        return fit;
    }

    /** Whether a factor is close enough to a whole number to draw unsmoothed. */
    @OnThread(Tag.Any)
    public static boolean isWholeScale(double s)
    {
        return s >= 1.0 && Math.abs(s - Math.rint(s)) < 0.001;
    }

    private static void setSmooth(Node node, boolean smooth)
    {
        if (node instanceof ImageView)
        {
            ((ImageView) node).setSmooth(smooth);
        }
        else if (node instanceof Parent)
        {
            for (Node child : ((Parent) node).getChildrenUnmodifiable())
            {
                setSmooth(child, smooth);
            }
        }
    }
}
