/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2011,2012,2018  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.gui.input.mouse;


import greenfoot.Actor;
import greenfoot.MouseInfo;
import greenfoot.MouseInfoVisitor;
import greenfoot.World;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;


/**
 * Class to hold data collected from the mouse events. Using MouseInfo is
 * not enough, since a mouse info object doesn't contain all the info we
 * need (whether it was a drag, move, etc)
 * @author Poul Henriksen
 * 
 */
@OnThread(Tag.Any)
class MouseEventData
{
    private MouseInfo mouseInfo;
    
    // We need to hold the data for each individual action that might have
    // happened, because what to report depends on what we are interested in,
    // with relation to the actor, world or globally.
    private MouseInfo mouseDragEndedInfo;
    private MouseInfo mouseClickedInfo;
    private MouseInfo mousePressedInfo;
    private MouseInfo mouseDraggedInfo;
    private MouseInfo mouseMovedInfo;
    // SuperGreenfoot: mouse wheel movement this act. Kept separately from the other
    // events because it never competes with them for priority.
    private MouseInfo mouseScrolledInfo;
    private int scrollTotal;
    // SuperGreenfoot: a button going up. Kept separately for the same reason: a release
    // is always reported, even in an act that also holds a click or a drag ending.
    private MouseInfo mouseReleasedInfo;
    private MouseEventData dragStartedBy;

    public void init()
    {
        mousePressedInfo = null;
        mouseClickedInfo = null;
        mouseDraggedInfo = null;
        mouseDragEndedInfo = null;
        mouseMovedInfo = null;
        mouseScrolledInfo = null;
        scrollTotal = 0;
        mouseReleasedInfo = null;
        if (mouseInfo != null)
        {
            MouseInfo blankedMouseInfo = MouseInfoVisitor.newMouseInfo();
            // Only retain info on latest location, not clicks etc:
            MouseInfoVisitor.setLoc(blankedMouseInfo, mouseInfo.getX(), mouseInfo.getY(),
                    MouseInfoVisitor.getPx(mouseInfo), MouseInfoVisitor.getPy(mouseInfo));
            mouseInfo = blankedMouseInfo;
        }
        
    }
    
    public MouseInfo getMouseInfo()
    {
        if (mouseInfo != null && scrollTotal != 0 && mouseInfo != mouseScrolledInfo) {
            MouseInfoVisitor.setScrollAmount(mouseInfo, scrollTotal);
        }
        return mouseInfo;
    }

    public boolean isMousePressed()
    {
        return mousePressedInfo != null;
    }

    public boolean isMousePressedOn(Object obj)
    {
        return checkObject(obj, mousePressedInfo);
    }

    /**
     * Record a mouse press event in the event data.
     * 
     * @param x   x-coordinate in world cells
     * @param y   y-coordinate in world cells
     * @param px    x-coordinate in pixels
     * @param py    y-coordinate in pixels
     * @param button    which button was pressed.
     */
    public void mousePressed(int x, int y, int px, int py, int button)
    {
        initKeepingExtras();
        mousePressedInfo = MouseInfoVisitor.newMouseInfo();
        mouseInfo = mousePressedInfo;  
        MouseInfoVisitor.setButton(mouseInfo, button);
        MouseInfoVisitor.setLoc(mouseInfo, x, y, px, py);
    }

    public boolean isMouseClickedOn(Object obj)
    { 
        // if the mouse was pressed outside the object we are looking for, it
        // can't be clicked on that object
        if(obj != null && (isMousePressed() && !isMousePressedOn(obj))) {
            return false;
        }
        return checkObject(obj, mouseClickedInfo);
    }
    
    public boolean isMouseClicked()
    {
        return mouseClickedInfo != null;
    }
    
    /**
     * Record a mouse click in the event data.
     * 
     * @param x   x-coordinate in world cells
     * @param y   y-coordinate in world cells
     * @param px    x-coordinate in pixels
     * @param py    y-coordinate in pixels
     * @param button    which button was clicked
     * @param clickCount   the click count (how many times the button has been clicked)
     */
    public void mouseClicked(int x, int y, int px, int py, int button, int clickCount)
    {
        MouseInfo tempPressedInfo = mousePressedInfo;        
        initKeepingExtras();       
        mousePressedInfo = tempPressedInfo;
        
        mouseClickedInfo = MouseInfoVisitor.newMouseInfo();;
        mouseInfo = mouseClickedInfo;
        MouseInfoVisitor.setButton(mouseInfo, button);
        MouseInfoVisitor.setLoc(mouseInfo, x, y, px, py);
        MouseInfoVisitor.setClickCount(mouseInfo, clickCount);
    }

    public boolean isMouseDragged()
    {
        return mouseDraggedInfo != null;
    }

    public boolean isMouseDraggedOn(Object obj)
    {
        return checkObject(obj, mouseDraggedInfo);
    }

    /**
     * Record a mouse drag in the event data.
     * 
     * @param x   x-coordinate in world cells
     * @param y   y-coordinate in world cells
     * @param px    x-coordinate in pixels
     * @param py    y-coordinate in pixels
     * @param button    which button is pressed
     * @param actor    the actor being dragged.
     */
    public void mouseDragged(int x, int y, int px, int py, int button, Actor actor)
    {
        initKeepingExtras();
        mouseDraggedInfo = MouseInfoVisitor.newMouseInfo();
        mouseInfo = mouseDraggedInfo;
        MouseInfoVisitor.setButton(mouseInfo, button);
        MouseInfoVisitor.setLoc(mouseInfo, x, y, px, py);
        MouseInfoVisitor.setActor(mouseInfo, actor);
    }

    public boolean isMouseDragEnded()
    {
        return mouseDragEndedInfo != null;
    }

    public boolean isMouseDragEndedOn(Object obj)
    {
        return checkObject(obj, mouseDragEndedInfo);
    }

    /**
     * Record a mouse drag ending in the event data.
     * 
     * @param x   x-coordinate in world cells
     * @param y   y-coordinate in world cells
     * @param px    x-coordinate in pixels
     * @param py    y-coordinate in pixels
     * @param button    which button was pressed
     * @param dragStartData  the data object holding information about the drag start event.
     */
    public void mouseDragEnded(int x, int y, int px, int py, int button, MouseEventData dragStartData)
    {
        MouseInfo tempPressedInfo = mousePressedInfo;
        MouseInfo tempClickedInfo = mouseClickedInfo;
        initKeepingExtras();
        mousePressedInfo = tempPressedInfo;
        mouseClickedInfo = tempClickedInfo;
        mouseDragEndedInfo = MouseInfoVisitor.newMouseInfo();;
        mouseInfo = mouseDragEndedInfo;
        MouseInfoVisitor.setButton(mouseInfo, button);
        MouseInfoVisitor.setLoc(mouseInfo, x, y, px, py);
        MouseInfoVisitor.setActor(mouseInfo, dragStartData.getActor());
        this.dragStartedBy = dragStartData;
    }

    public void mouseExited()
    {
        mouseInfo = mouseDraggedInfo;
        mouseMovedInfo = null;
    }
    
    public boolean isMouseMoved()
    {
        return mouseMovedInfo != null;
    }

    public boolean isMouseMovedOn(Object obj)
    {
        return checkObject(obj, mouseMovedInfo);
    }

    /** SuperGreenfoot: was the mouse wheel used this act? */
    public boolean isMouseScrolled()
    {
        return mouseScrolledInfo != null;
    }

    /** SuperGreenfoot: was the mouse wheel used over the given object this act? */
    public boolean isMouseScrolledOn(Object obj)
    {
        return checkObject(obj, mouseScrolledInfo);
    }

    /**
     * SuperGreenfoot: the mouse wheel moved. Several wheel events in one act add up.
     * Other events in the same act keep their priority; the scroll amount is carried
     * on whichever MouseInfo is current.
     */
    public void mouseScrolled(int x, int y, int px, int py, int amount)
    {
        if (mouseScrolledInfo == null) {
            mouseScrolledInfo = MouseInfoVisitor.newMouseInfo();
        }
        MouseInfoVisitor.setLoc(mouseScrolledInfo, x, y, px, py);
        scrollTotal += amount;
        MouseInfoVisitor.setScrollAmount(mouseScrolledInfo, scrollTotal);
        if (mouseInfo == null || mouseInfo == mouseScrolledInfo
                || (mousePressedInfo == null && mouseClickedInfo == null && mouseDraggedInfo == null
                    && mouseDragEndedInfo == null)) {
            mouseInfo = mouseScrolledInfo;
        }
    }

    /** SuperGreenfoot: did a mouse button go up this act? */
    public boolean isMouseReleased()
    {
        return mouseReleasedInfo != null;
    }

    /** SuperGreenfoot: did a mouse button go up over the given object this act? */
    public boolean isMouseReleasedOn(Object obj)
    {
        return checkObject(obj, mouseReleasedInfo);
    }

    /**
     * SuperGreenfoot: a mouse button went up, at the place the pointer was when it did.
     * Unlike a drag ending, which reports where the drag started, this reports what the
     * pointer is over now. It never displaces a press, click or drag as the act's
     * MouseInfo, but it does stand in for a move, or for nothing at all.
     *
     * @param x   x-coordinate in world cells
     * @param y   y-coordinate in world cells
     * @param px    x-coordinate in pixels
     * @param py    y-coordinate in pixels
     * @param button    which button was released
     */
    public void mouseReleased(int x, int y, int px, int py, int button)
    {
        mouseReleasedInfo = MouseInfoVisitor.newMouseInfo();
        MouseInfoVisitor.setButton(mouseReleasedInfo, button);
        MouseInfoVisitor.setLoc(mouseReleasedInfo, x, y, px, py);
        if (mousePressedInfo == null && mouseClickedInfo == null
                && mouseDraggedInfo == null && mouseDragEndedInfo == null) {
            if (scrollTotal != 0) {
                MouseInfoVisitor.setScrollAmount(mouseReleasedInfo, scrollTotal);
            }
            mouseInfo = mouseReleasedInfo;
        }
    }

    /**
     * SuperGreenfoot: like init(), but the events that do not compete for priority -
     * a wheel movement and a button going up - survive the rest of this act.
     */
    private void initKeepingExtras()
    {
        MouseInfo scrolled = mouseScrolledInfo;
        int total = scrollTotal;
        MouseInfo released = mouseReleasedInfo;
        init();
        mouseScrolledInfo = scrolled;
        scrollTotal = total;
        mouseReleasedInfo = released;
    }

    /**
     * Record a mouse movement (with no buttons down) in the event data.
     * 
     * @param x   x-coordinate in world cells
     * @param y   y-coordinate in world cells
     * @param px    x-coordinate in pixels
     * @param py    y-coordinate in pixels
     */
    public void mouseMoved(int x, int y, int px, int py)
    {
        initKeepingExtras();
        mouseMovedInfo = MouseInfoVisitor.newMouseInfo();;
        mouseInfo = mouseMovedInfo;
        MouseInfoVisitor.setLoc(mouseInfo, x, y, px, py);
    }

    public Actor getActor()
    {
        if(mouseInfo == null) {
            return null;
        }
        return mouseInfo.getActor();
    }

    public int getButton()
    {
        if(mouseInfo == null) {
            return 0;
        }
        return mouseInfo.getButton();
    }
    
    /**
     * Check whether the given object can be considered to match the MouseInfo.
     * 
     * @param obj The query
     * @param info To check against
     * @return
     */
    private boolean checkObject(Object obj, MouseInfo info)
    {
        if(info == null) {
            return false;
        }
        Actor actor = info.getActor();
        return obj == null || (obj instanceof World && actor == null) || actor == obj;
    }

    
    public String toString()
    {
        String s = "MouseEventData ";
        if(mouseInfo != null) {
            s += mouseInfo.toString();
        }
        if(mousePressedInfo != null) {
            s += " pressed";
        }
        if(mouseClickedInfo != null) {
            s += " clicked";
        }
        if(mouseDraggedInfo != null) {
            s += " dragged";
        }
        if(mouseDragEndedInfo != null) {
            s += " dragEnded";
        }
        if(mouseMovedInfo != null) {
            s += " moved";
        }
        if(mouseScrolledInfo != null) {
            s += " scrolled " + scrollTotal;
        }
        if(mouseReleasedInfo != null) {
            s += " released";
        }
        return s;
    }

    /**
     * From the simulation thread (when it is safe to access the actors via the world's locator),
     * go through our mouse data and map X,Y positions into actors.
     * @param locator
     */
    @OnThread(Tag.Simulation)
    public void setActors(WorldLocator locator)
    {
        for (MouseInfo info : Arrays.asList(mouseInfo, mouseClickedInfo, mouseDragEndedInfo,
                mouseMovedInfo, mousePressedInfo, mouseDraggedInfo, mouseScrolledInfo,
                mouseReleasedInfo))
        {
            if (info != null && info.getActor() == null)
            {
                int x = MouseInfoVisitor.getPx(info);
                int y = MouseInfoVisitor.getPy(info);
                MouseInfoVisitor.setActor(info, locator.getTopMostActorAt(x, y));
            }
        }
    }

    /**
     * If drag ended, and was started by the given MouseEventData, copy the drag-start
     * actor to the drag-end info.
     */
    public void setDragStartActor(MouseEventData dragStartData)
    {
        // 
        if (mouseDragEndedInfo != null && dragStartedBy == dragStartData)
        {
            MouseInfoVisitor.setActor(mouseDragEndedInfo, dragStartData.getActor());
        }
    }
}