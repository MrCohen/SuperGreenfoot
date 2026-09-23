/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2012,2018  Poul Henriksen and Michael Kolling 
 
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

import greenfoot.MouseInfo;
import greenfoot.gui.input.mouse.MouseEventData;

import java.awt.event.MouseEvent;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * There are two ways that the mouse can be handled in Greenfoot. One is the
 * built-in mouse support like it was in Greenfoot release 1.3.0 and earlier.
 * This still works the same way in newer Greenfoot versions as long as the
 * simulation is not running. When the simulation is running there is no default
 * mouse support for dragging objects already added into the world. When the
 * simulation is running the user classes have to poll for mouse information
 * through the greenfoot.Greenfoot class.
 * <p>
 * MouseEvents are collected in frames. A frame is defined as the interval
 * between the first poll of anything in the previous act-round to the first
 * poll in the current act-round.
 * <p>
 * When the first poll in an act loop happens, the future mouse data is copied
 * into current mouseInfo and the creation of a new future mouse data object is
 * started.
 * <p>
 * 
 * If several events happen in the same frame the events are prioritized like
 * this: <br>
 * Priorities with highest priority first:
 * <ul>
 * <li> dragEnd </li>
 * <li> click </li>
 * <li> press </li>
 * <li> drag </li>
 * <li> move </li>
 * </ul>
 * 
 * In general only one event can happen in a frame, the only exception is click
 * and press which could happen in the same frame if a mouse is clicked in one
 * frame. <br>
 * If several of the same type of event happens, then the last one is used.
 * <p>
 * If, for instance, two buttons are pressed at the same time, the behaviour is
 * undefined. Maybe we should define it so that button1 always have higher
 * priority than button2 and button2 always higher than button3. But not
 * necessarily documenting this to the user.
 * 
 * @author Poul Henriksen
 */
public class MousePollingManager
{
    /*
     * Methods in this class are called from two threads: the simulation thread and the
     * GUI even thread. Some methods are called by one and some by the other; the GUI produces
     * the data while the simulation consumes it.
     * 
     * A small number of fields can be accessed be either thread and so access to them must
     * be synchronized. Other fields are accessed by only one thread or the other.
     */
    
    /**
     * The current mouse data This will be the mouse info returned for the rest
     * of this act loop.
     * 
     * <p>Accessed only from the simulation thread.
     */
    private MouseEventData currentData = new MouseEventData();

    /**
     * The future mouse data is build up from mouse events happening from the
     * first time the user requested mouse info in this act loop, until the user
     * requests again in the next act-loop, at which point the future will
     * become the current.
     * 
     * <p>Access to this field must be synchronized.
     */
    private MouseEventData futureData = new MouseEventData();
    
    /**
     * Used to collect data if we already have a highest priority dragEnded
     * collected. We need this in order to collect data for a potential new
     * dragEnd since we want to report the latest dragEnd in case there are more
     * than one.
     * 
     * <p>Access to this field must be synchronized.
     */
    private MouseEventData potentialNewDragData = new MouseEventData();
    
    /**
     * Locates the actors in the world (read only field, requires no synchronization).
     */
    private WorldLocator locator;

    /**
     * Keeps track of where a drag started. This should never be explicitly set
     * to null, because it might result in exceptions when doing undefined
     * things like dragging with two buttons down at the same time.
     * 
     * <p>Accessed only from the GUI thread.
     */
    private MouseEventData dragStartData = new MouseEventData();

    /**
     * Track whether the mouse is currently being dragged.
     * 
     * <p>Accessed only from the GUI thread.
     */
    private boolean isDragging;

    /**
     * Whether we have received more mouse data since we last gave data to the simulation.
     * 
     * <p>Access to this field must be synchronized.
     */
    private boolean gotNewEvent;
    private boolean gotNewDragStartEvent;

    /**
     * SuperGreenfoot: which mouse buttons are held down right now, one bit per
     * Greenfoot button number (1 left, 2 middle, 3 right). Unlike the events, this is
     * a running state rather than something that happens in a frame, so it is kept
     * outside the MouseEventData and is not subject to event priority.
     *
     * <p>Access to this field must be synchronized.
     */
    private int buttonsDown;

    /**
     * SuperGreenfoot: the held buttons as they were when this act round started, so
     * that the answer cannot change half way through an act.
     *
     * <p>Accessed only from the simulation thread.
     */
    private int currentButtonsDown;
    

    /**
     * Creates a new mouse manager. The mouse manager should be notified
     * whenever a new act round starts by calling {@link #newActStarted()}.
     * 
     * @param locator
     *            Used to locate things (actors and coordinates) within the
     *            World. May be null, but in that case the locator must be set
     *            later.
     */
    @OnThread(Tag.Any)
    public MousePollingManager(WorldLocator locator) 
    {
        this.locator = locator;
    }
    
    /**
     * Set the locator to be used by this mouse polling manager.
     */
    public void setWorldLocator(WorldLocator locator)
    {
        this.locator = locator;
    }
    
    /**
     * This method should be called when a new act-loop is started.
     */
    @OnThread(Tag.Simulation)
    public synchronized void newActStarted()
    {
        // SuperGreenfoot: the held buttons are a state, not an event, so they are
        // snapshotted every round whether or not any event arrived.
        currentButtonsDown = buttonsDown;

        // The current data was already polled, or we have a new event since;
        // use futureData as our current data. (If there's been no event, i.e. if
        // gotNewEvent is false, futureData will contain no events).
        if (gotNewEvent) {
            MouseEventData newData = new MouseEventData();
            futureData.setActors(locator);
            currentData = futureData;
            futureData = newData;
            potentialNewDragData = new MouseEventData();

            if (gotNewDragStartEvent)
            {
                dragStartData.setActors(locator);
                // We need to also set the actor on the drag-ended if it both
                // started this frame (true if gotNewDragStartEvent was true) and
                // ended this frame (check inside setDragStartActor):
                currentData.setDragStartActor(dragStartData);
                gotNewDragStartEvent = false;
            }
            
            // Indicate that we have processed all current events.
            gotNewEvent = false;
        }
        else {
            currentData.init();
        }
    }

    /**
     * This method should be called every time we receive a mouse event. It is
     * used to keep track of whether any events have been occurring in this
     * frame.
     * 
     * <p>This must be called from a synchronized context.
     */
    private void registerEventRecieved()
    {
        gotNewEvent = true;
    }
    
    

    // ************************************
    // Methods available to the user
    // ************************************

    /**
     * Whether the mouse had been pressed (changed from a non-pressed state to
     * being pressed) on the given object. If the parameter is an Actor the
     * method will only return true if the mouse has been pressed on the given
     * actor - if there are several actors at the same place, only the top most
     * actor will count. If the parameter is a World then true will be returned
     * only if the mouse was pressed outside the boundaries of all Actors. If
     * the parameter is null, then it will return true no matter where the mouse
     * was pressed as long as it is inside the world boundaries.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been pressed as explained above
     */
    @OnThread(Tag.Simulation)
    public boolean isMousePressed(Object obj)
    {
        return currentData.isMousePressedOn(obj);
    }

    /**
     * Whether the mouse had been clicked (pressed and released) on the given
     * object. If the parameter is an Actor the method will only return true if
     * the mouse has been clicked on the given actor - if there are several
     * actors at the same place, only the top most actor will count. If the
     * parameter is a World then true will be returned only if the mouse was
     * clicked outside the boundaries of all Actors. If the parameter is null,
     * then it will return true no matter where the mouse was clicked as long as
     * it is inside the world boundaries.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been clicked as explained above
     */
    @OnThread(Tag.Simulation)
    public boolean isMouseClicked(Object obj)
    {
        return currentData.isMouseClickedOn(obj);
    }

    /**
     * Whether the mouse is being dragged on the given object. The mouse is
     * considered to be dragged on an object, only if the drag started on that
     * object - even if the mouse has since been moved outside of that object.
     * <p>
     * If the parameter is an Actor the method will only return true if the drag
     * started on the given actor - if there are several actors at the same
     * place, only the top most actor will count. If the parameter is a World
     * then true will be returned only if the drag was started outside the
     * boundaries of all Actors. If the parameter is null, then it will return
     * true no matter where the drag was started as long as it is inside the
     * world boundaries.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been pressed as explained above
     */
    @OnThread(Tag.Simulation)
    public boolean isMouseDragged(Object obj)
    {
        return currentData.isMouseDraggedOn(obj);
    }

    /**
     * A mouse drag has ended. This happens when the mouse has been dragged and
     * the mouse button released.
     * <p>
     * If the parameter is an Actor the method will only return true if the drag
     * started on the given actor - if there are several actors at the same
     * place, only the top most actor will count. If the parameter is a World
     * then true will be returned only if the drag was started outside the
     * boundaries of all Actors. If the parameter is null, then it will return
     * true no matter where the drag was started as long as it is inside the
     * world boundaries.
     * 
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been pressed as explained above
     */
    @OnThread(Tag.Simulation)
    public boolean isMouseDragEnded(Object obj)
    {
        return currentData.isMouseDragEndedOn(obj);
    }

    /**
     * Whether the mouse had been moved on the given object. The mouse is
     * considered to be moved on an object, only if the mouse pointer is above that
     * object.
     * <p>
     * If the parameter is an Actor the method will only return true if the move
     * is on the given actor - if there are several actors at the same
     * place, only the top most actor will count. If the parameter is a World
     * then true will be returned only if the move is outside the
     * boundaries of all Actors. If the parameter is null, then it will return
     * true no matter where the drag was started as long as it is inside the
     * world boundaries.
     * 
     * @param obj Typically one of Actor, World or null
     * @return True if the mouse has been moved as explained above
     */
    @OnThread(Tag.Simulation)
    public boolean isMouseMoved(Object obj)
    {
        return currentData.isMouseMovedOn(obj);
    }

    /**
     * Gets the mouse info with information about the current state of the
     * mouse. Within the same act-loop it will always return exactly the same
     * MouseInfo object with exactly the same contents.
     * 
     * @return The info about the current state of the mouse; Null if the mouse is outside
     *         the world boundaries (unless being dragged).
     */
    @OnThread(Tag.Simulation)
    public MouseInfo getMouseInfo()
    {
        return currentData.getMouseInfo();
    }   

    /** SuperGreenfoot: was the mouse wheel used over the given object this act? */
    @OnThread(Tag.Simulation)
    public boolean isMouseScrolled(Object obj)
    {
        return currentData.isMouseScrolledOn(obj);
    }

    /**
     * SuperGreenfoot: whether a mouse button went up over the given object this act.
     * The object is the one under the pointer when the button was released, which is
     * not necessarily where the press or the drag began.
     *
     * @param obj Typically one of Actor, World or null
     * @return True if a button was released as explained above
     */
    @OnThread(Tag.Simulation)
    public boolean isMouseReleased(Object obj)
    {
        return currentData.isMouseReleasedOn(obj);
    }

    /**
     * SuperGreenfoot: whether the given button (1 left, 2 middle, 3 right) is being
     * held down. This is the state as of the start of this act round, so it gives the
     * same answer everywhere in one act.
     */
    @OnThread(Tag.Simulation)
    public boolean isMouseButtonDown(int button)
    {
        return (currentButtonsDown & buttonMask(button)) != 0;
    }

    /**
     * SuperGreenfoot: the mouse wheel moved.
     *
     * @param x      The mouse x position in world pixels.
     * @param y      The mouse y position in world pixels.
     * @param amount The movement in pixels; positive means rolled towards the user (scroll down).
     */
    @OnThread(Tag.Any)
    public void mouseScrolled(int x, int y, int amount)
    {
        if (locator == null || amount == 0)
        {
            return;
        }
        synchronized (this)
        {
            registerEventRecieved();
            int tx = locator.getTranslatedX(x);
            int ty = locator.getTranslatedY(y);
            futureData.mouseScrolled(tx, ty, x, y, amount);
        }
    }

    /**
     * The mouse got clicked at the given world location
     * @param x The pixel location in the world (not cells)
     * @param y The pixel location in the world (not cells)
     * @param button The button reported by the original event.
     * @param clickCount The click count recorded by the original event.
     */
    @OnThread(Tag.Any)
    public void mouseClicked(int x, int y, int button, int clickCount)
    {
        if (locator == null)
        {
            return;
        }
        
        synchronized (this)
        {
            MouseEventData mouseData = futureData;
            // In case we already have a dragEnded and we get another
            // dragEnded, we need to start collection data for that.            
            if (futureData.isMouseDragEnded())
            {
                mouseData = potentialNewDragData;
            }
            if (!PriorityManager.isHigherPriority(MouseEvent.MOUSE_CLICKED, mouseData))
            {
                return;
            }
            registerEventRecieved();
            int tx = locator.getTranslatedX(x);
            int ty = locator.getTranslatedY(y);
            
            mouseData.mouseClicked(tx, ty, x, y, getButton(button), clickCount);
            isDragging = false;
        }
    }

    /**
     * Translates a JavaFX button to 1/2/3 as used by the Greenfoot API for left/middle/right.
     */
    /**
     * Buttons are passed in as Greenfoot button numbers already: 1 left/primary,
     * 2 middle, 3 right/secondary, 0 none.
     */
    private int getButton(int button)
    {
        return button;
    }

    /**
     * SuperGreenfoot: the bit standing for a Greenfoot button number in buttonsDown.
     * Anything outside 1..3 has no bit, so it can neither be set nor be reported.
     */
    private static int buttonMask(int button)
    {
        return (button >= 1 && button <= 3) ? (1 << button) : 0;
    }

    /**
     * The mouse left the world area.
     */
    @OnThread(Tag.Any)
    public synchronized void mouseExited()
    {
        futureData.mouseExited();
        registerEventRecieved();
    }

    /**
     * The mouse got pressed on the given world location
     * @param x The pixel location in the world (not cells)
     * @param y The pixel location in the world (not cells)
     * @param button The button reported by the original event.
     */
    @OnThread(Tag.Any)
    public void mousePressed(int x, int y, int button)
    {
        if (locator == null)
        {
            return;
        }
        
        synchronized(this)
        {
            // SuperGreenfoot: the button is now held, whatever the event priorities decide below.
            buttonsDown |= buttonMask(getButton(button));

            MouseEventData mouseData = futureData;
            // In case we already have a dragEnded and we get another
            // dragEnded, we need to start collection data for that.
            if (futureData.isMouseDragEnded())
            {
                mouseData = potentialNewDragData;
            }
        
            // This might be the beginning of a drag so we store it
            dragStartData = new MouseEventData();
            int tx = locator.getTranslatedX(x);
            int ty = locator.getTranslatedY(y);
            dragStartData.mousePressed(tx, ty, x, y, getButton(button));
            gotNewDragStartEvent = true;

            // We only really want to register this event as a press if there is no higher priorities
            if (!PriorityManager.isHigherPriority(MouseEvent.MOUSE_PRESSED, mouseData))
            {
                return;
            }
            registerEventRecieved();
            mouseData.mousePressed(tx, ty, x, y, getButton(button));
            isDragging = false;
        }
    }

    /**
     * The mouse got released at the given world location
     * @param x The pixel location in the world (not cells)
     * @param y The pixel location in the world (not cells)
     * @param button The button reported by the original event.
     */
    @OnThread(Tag.Any)
    public void mouseReleased(int x, int y, int button)
    {
        if (locator == null)
        {
            return;
        }
        
        synchronized(this)
        {
            // SuperGreenfoot: the button is no longer held, whatever happens below.
            buttonsDown &= ~buttonMask(getButton(button));

            int tx = locator.getTranslatedX(x);
            int ty = locator.getTranslatedY(y);

            // This might be the end of a drag
            if(isDragging)
            {
                // In case we already have a dragEnded and we get another
                // dragEnded, should use the new one
                if (futureData.isMouseDragEnded())
                {
                    futureData = potentialNewDragData;
                }
                
                // A lower-priority event already collected wins, as it always has; the
                // release itself is still recorded below either way.
                if (PriorityManager.isHigherPriority(MouseEvent.MOUSE_RELEASED, futureData))
                {
                    registerEventRecieved();

                    futureData.mouseClicked(tx, ty, x, y, getButton(button), 1);

                    futureData.mouseDragEnded(tx, ty, x, y, getButton(button), dragStartData);
                    isDragging = false;
                    potentialNewDragData = new MouseEventData();
                }
            }

            // SuperGreenfoot: a release is reported in the act it happened in even when
            // it is not the end of a drag, which is the only case upstream recorded.
            registerEventRecieved();
            futureData.mouseReleased(tx, ty, x, y, getButton(button));
        }
    }

    /**
     * The mouse got dragged to the given world location
     * @param x The pixel location in the world (not cells)
     * @param y The pixel location in the world (not cells)
     * @param button The button reported by the original event.
     */
    @OnThread(Tag.Any)
    public void mouseDragged(int x, int y, int button)
    {
        if (locator == null)
        {
            return;
        }
        
        synchronized(this)
        {
            isDragging = true;
            
            if (!PriorityManager.isHigherPriority(MouseEvent.MOUSE_DRAGGED, futureData))
            {
                return;
            }
            registerEventRecieved();
            
            // Find and store the actor that relates to this drag.
            int tx = locator.getTranslatedX(x);
            int ty = locator.getTranslatedY(y);
            futureData.mouseDragged(tx, ty, x, y, dragStartData.getButton(), dragStartData.getActor());
        }
    }

    /**
     * The mouse got moved to the given world location
     * @param x The pixel location in the world (not cells)
     * @param y The pixel location in the world (not cells)
     */
    @OnThread(Tag.Any)
    public void mouseMoved(int x, int y)
    {
        if (locator == null)
        {
            // Not fully initialised yet, so no need to handle event:
            return;
        }
        
        synchronized(this)
        {
            if (!PriorityManager.isHigherPriority(MouseEvent.MOUSE_MOVED, futureData))
            {
                return;
            }
            registerEventRecieved();
            int tx = locator.getTranslatedX(x);
            int ty = locator.getTranslatedY(y);
            futureData.mouseMoved(tx, ty, x, y);
            isDragging = false;
        }
    }

    /**
     * Called when the world starts running, to discard any
     * old mouse data that may have been accumulated while paused.
     */
    public synchronized void startedRunning()
    {
        futureData = new MouseEventData();
        // SuperGreenfoot: a button held while the scenario was paused is not held now.
        buttonsDown = 0;
        currentButtonsDown = 0;
    }

    /**
     * SuperGreenfoot: the world view lost keyboard focus, so no button counts as held
     * any more. The release that would normally clear it may go to another window and
     * never reach us. This mirrors what KeyboardManager does with held keys.
     */
    @OnThread(Tag.Any)
    public synchronized void focusLost()
    {
        buttonsDown = 0;
    }
}

