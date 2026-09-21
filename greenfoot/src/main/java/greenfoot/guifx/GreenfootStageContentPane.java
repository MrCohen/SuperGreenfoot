/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2018,2021  Poul Henriksen and Michael Kolling 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of f
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */

package greenfoot.guifx;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * The content pane for GreenfootStage.  This needs to be a custom pane to get the layout
 * algorithm implemented as we want.
 */
@OnThread(Tag.FXPlatform)
class GreenfootStageContentPane extends Pane
{
    private static final int CLASS_DIAGRAM_PADDING = 12;
    private static final int IDEAL_WORLD_PADDING = 30;
    private final Pane worldViewScroll;
    // SuperGreenfoot: upstream's Share button, in a bar that also holds our own controls.
    private final Region topBar;
    private final ScrollPane classDiagramScroll;
    private final Pane controlPanel;

    /**
     * Construct a content pane for the three major components: the world view,
     * the class diagram, and the control panel.
     */
    public GreenfootStageContentPane(Pane worldViewScroll, Region topBar, ScrollPane classDiagramScroll, ControlPanel controlPanel)
    {
        this.worldViewScroll = worldViewScroll;
        this.topBar = topBar;
        this.classDiagramScroll = classDiagramScroll;
        this.controlPanel = controlPanel;
        getChildren().addAll(worldViewScroll, topBar, classDiagramScroll, controlPanel);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        final double ourWidth = getWidth();
        final double ourHeight = getHeight();
        
        final double idealWorldWidth = worldViewScroll.prefWidth(-1);

        // The top bar gets its ideal height, and the width it cannot do without.
        // SuperGreenfoot: this was the share button's preferred width, but the bar now
        // holds more than one control, and using the preferred width would widen the
        // floor below (and so narrow the world) by everything we have added:
        final double topBarWidth = topBar.minWidth(-1);
        final double topBarHeight = topBar.prefHeight(-1);
        
        // Class diagram height is known: our height minus padding minus topBarHeight
        final double classDiagramHeight = ourHeight - 3 * CLASS_DIAGRAM_PADDING - topBarHeight;
        final double idealClassDiagramWidth = classDiagramScroll.prefWidth(classDiagramHeight);
        
        double classDiagramWidth;
        if (idealClassDiagramWidth + 2 * CLASS_DIAGRAM_PADDING + idealWorldWidth > ourWidth)
        {
            // Someone is going to have lose some width.  We start by taking it from class diagram:
            double minClassDiagramWidth = classDiagramScroll.minWidth(classDiagramHeight);
            classDiagramWidth = Math.max(minClassDiagramWidth, 
                ourWidth - idealWorldWidth - 2 * CLASS_DIAGRAM_PADDING);
        }
        else
        {
            // Everyone can have what they want, width-wise:
            classDiagramWidth = idealClassDiagramWidth;
        }
        
        // To avoid some wrong GUI effect when OS scaling is not to 100%:
        classDiagramWidth = snapSizeX(classDiagramWidth);
        
        // Make sure the width is not smaller than a minimum required
        // to show the top bar properly.
        classDiagramWidth = Math.max(classDiagramWidth, topBarWidth + 2 * CLASS_DIAGRAM_PADDING);
        
        // The control panel is always its preferred height:
        final double worldWidth = ourWidth - (classDiagramWidth + 2 * CLASS_DIAGRAM_PADDING);
        final double controlPanelHeight = controlPanel.prefHeight(worldWidth);
        
        worldViewScroll.resizeRelocate(0, 0, worldWidth, ourHeight - controlPanelHeight);
        topBar.resizeRelocate(worldWidth + CLASS_DIAGRAM_PADDING, CLASS_DIAGRAM_PADDING,
                classDiagramWidth, topBarHeight);
        classDiagramScroll.resizeRelocate(worldWidth + CLASS_DIAGRAM_PADDING, 
                2 * CLASS_DIAGRAM_PADDING + topBarHeight,
                classDiagramWidth, classDiagramHeight);
        controlPanel.resizeRelocate(0, ourHeight - controlPanelHeight, worldWidth, controlPanelHeight);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefWidth(double height)
    {
        // Not quite accurate, but shouldn't matter when we have no real parent.
        // This is really just for calculating the initial window size:
        return worldViewScroll.prefWidth(height) + 2 * IDEAL_WORLD_PADDING /* Some world spacing */ 
                + Math.max(topBar.prefWidth(-1), classDiagramScroll.prefWidth(height)) + 2 * CLASS_DIAGRAM_PADDING;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        // Again, not quite accurate, but should be close enough when we are topmost container:
        return Math.max(topBar.prefHeight(-1) + classDiagramScroll.prefHeight(-1) + 3 * CLASS_DIAGRAM_PADDING, 
            worldViewScroll.prefHeight(-1) + 2 * IDEAL_WORLD_PADDING + controlPanel.prefHeight(-1));
    }
}
