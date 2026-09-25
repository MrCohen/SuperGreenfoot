/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2005-2023 Poul Henriksen and Michael Kolling
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
package greenfoot;

import greenfoot.core.WorldHandler;
import greenfoot.gui.input.mouse.MousePollingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A window that lives inside the world: a framed, movable panel that can hold
 * other actors. Use it for inventories, dialogs, menus, HUD panels, or simply to
 * put a frame around a picture.
 *
 * <p>A SuperWindow is an {@link Actor}, so you add it to a world with
 * {@link World#addObject(Actor, int, int)} and position it like any other actor.
 * Actors are placed <em>inside</em> the window with {@link #addObject(Actor, int, int)}
 * rather than in the world. An actor inside a window uses the window's own
 * coordinates: (0,0) is the top-left cell of the window's content area, and the
 * actor moves whenever the window moves. Everything inside the window is drawn
 * clipped to the content area, on top of the rest of the world.
 *
 * <p>Windows are always painted above ordinary actors, in order of their
 * {@link #setZ(double) z} value; clicking a window brings it to the front. A window
 * with a title bar can be dragged by its title bar, closed and minimised with its
 * buttons, and it keeps itself on screen. A window without a title bar (the
 * two-argument constructor) is a plain locked panel, which suits a HUD.
 *
 * <p>Closing a window hides it and everything in it, but keeps it in the world with
 * its position and contents intact, so {@link #open()} shows it again exactly as it
 * was. To take a window out of the world completely, use
 * {@link World#removeObject(Actor)}: its contents go with it, and adding the window
 * to another world brings them back.
 *
 * <p>Windows and actors inside windows still take part in mouse handling:
 * {@link Greenfoot#mouseClicked(Object)} works for a window and for the actors
 * inside it, and {@link MouseInfo#getWindow()} tells you which window (if any) the
 * mouse is over. Collision queries such as {@link Actor#getIntersectingObjects(Class)}
 * only ever see actors in the same window (or, for an actor in the world, other
 * actors in the world), so a game object never collides with an inventory item.
 *
 * <p>Content larger than the window can be scrolled: call
 * {@link #setContentSize(int, int)} and {@link #setScrollable(boolean)}, and the
 * mouse wheel scrolls the window.
 *
 * @author Jordan Cohen (original SuperWindow library class)
 * @author SuperGreenfoot contributors
 * @since SuperGreenfoot 0.2.0
 */
public class SuperWindow extends Actor
{
    /** The default height of the title bar, in pixels. */
    public static final int DEFAULT_TITLE_BAR_HEIGHT = 20;

    /** The default thickness of the border, in pixels. */
    public static final int DEFAULT_BORDER_THICKNESS = 2;

    /** The default colour of the content area. */
    public static final Color DEFAULT_BACKGROUND_COLOR = new Color(200, 200, 200);

    /** The default colour of the border. */
    public static final Color DEFAULT_BORDER_COLOR = new Color(255, 204, 0);

    /** The default colour of the title bar. */
    public static final Color DEFAULT_TITLE_BAR_COLOR = new Color(90, 90, 90);

    /** The default colour of the title text. */
    public static final Color DEFAULT_TITLE_COLOR = Color.WHITE;

    private static final Color CLOSE_BUTTON_COLOR = new Color(255, 70, 70);
    private static final Color MINIMIZE_BUTTON_COLOR = new Color(255, 220, 90);
    private static final Color SCROLL_TRACK_COLOR = new Color(0, 0, 0, 40);
    private static final Color SCROLL_THUMB_COLOR = new Color(0, 0, 0, 110);
    private static final int SCROLL_BAR_WIDTH = 8;

    // ---- geometry (content sizes in cells, decorations in pixels) ----

    /** Visible content area, in cells. */
    int width, height;
    /** Total content area, in cells; at least the visible size. Larger means scrollable. */
    int contentWidth, contentHeight;
    /** The content size last asked for with setContentSize (may be smaller than the visible size). */
    int requestedContentWidth, requestedContentHeight;
    /** True once setContentSize has been called; until then the content simply follows the visible size. */
    private boolean contentSizeRequested;
    /** Scroll offset in cells. */
    int scrollX, scrollY;
    private int titleBarHeight = DEFAULT_TITLE_BAR_HEIGHT;
    private int borderThickness = DEFAULT_BORDER_THICKNESS;
    private boolean titleBarVisible;
    private String title;

    // ---- appearance ----
    private Color backgroundColor = DEFAULT_BACKGROUND_COLOR;
    private Color borderColor = DEFAULT_BORDER_COLOR;
    private Color titleBarColor = DEFAULT_TITLE_BAR_COLOR;
    private Color titleColor = DEFAULT_TITLE_COLOR;
    private Font titleFont;
    private GreenfootImage background;
    private GreenfootImage frameImage;
    /** True when the frame image came from setImage (a skin) rather than redrawFrame. */
    private boolean customFrame;
    private GreenfootImage scrollBarImage;

    // ---- behaviour ----
    private boolean closable = true;
    private boolean minimizable = true;
    private boolean draggable = true;
    private boolean keepOnScreen = true;
    boolean bounded = false;
    private boolean modal = false;
    private boolean alwaysOnTop = false;
    private boolean scrollable = false;

    // ---- state ----
    private boolean open = true;
    private boolean minimized = false;
    private boolean dragging = false;
    private boolean thumbDragging = false;
    private int dragOffsetX, dragOffsetY;
    private int thumbDragStartScroll, thumbDragStartPy;
    private boolean pressedOnTitleBar, pressedOnThumb;
    private int pressPx, pressPy;
    private int closeLeft, closeTop, closeSize = -1;
    private int minLeft, minTop, minSize = -1;

    /** The actors inside this window, in the order they were added. */
    final List<Actor> contents = new ArrayList<Actor>();

    /**
     * Create a window without a title bar: a plain panel that cannot be dragged,
     * closed or minimised by the mouse. This suits HUD panels and picture frames.
     *
     * @param width  The width of the content area, in cells.
     * @param height The height of the content area, in cells.
     */
    public SuperWindow(int width, int height)
    {
        this(width, height, null);
        titleBarVisible = false;
        draggable = false;
        closable = false;
        minimizable = false;
        redrawFrame();
    }

    /**
     * Create a window with a title bar. The window can be dragged by its title bar
     * and has close and minimise buttons.
     *
     * @param width  The width of the content area, in cells.
     * @param height The height of the content area, in cells.
     * @param title  The text shown in the title bar (may be empty).
     */
    public SuperWindow(int width, int height, String title)
    {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("A window's width and height must be at least 1.");
        }
        this.width = width;
        this.height = height;
        this.contentWidth = width;
        this.contentHeight = height;
        this.requestedContentWidth = width;
        this.requestedContentHeight = height;
        this.title = title == null ? "" : title;
        this.titleBarVisible = true;
        // Actor's constructor gave us its default image through setImage; that is
        // not a skin, so the frame is drawn.
        customFrame = false;
        redrawFrame();
    }

    /**
     * Create a window sized to fit the given image, with the image as the
     * background of the content area: a picture frame. Actors can still be
     * added on top of the picture.
     *
     * @param image The picture to frame; its size (in pixels) becomes the content size.
     * @param title The title bar text, or null for no title bar.
     */
    public SuperWindow(GreenfootImage image, String title)
    {
        this(image.getWidth(), image.getHeight(), title);
        if (title == null) {
            titleBarVisible = false;
            draggable = false;
            closable = false;
            minimizable = false;
        }
        setBackground(image);
        redrawFrame();
    }

    // ==================================
    //
    // Contents
    //
    // ==================================

    /**
     * Add an actor to this window at the given position inside the window's content
     * area. (0,0) is the window's top-left content cell. If the actor is already in
     * the world, or in another window, it is moved into this window. If it is
     * already in this window, it is simply moved.
     *
     * <p>The actor joins the world when the window is in a world (now, or later when
     * the window is added), and leaves it when the window is removed.
     *
     * @param actor The actor to add.
     * @param x     The x position inside the window, in cells.
     * @param y     The y position inside the window, in cells.
     */
    public void addObject(Actor actor, int x, int y)
    {
        if (actor == null) {
            throw new NullPointerException("Cannot add null to a window.");
        }
        if (actor == this) {
            throw new IllegalArgumentException("A window cannot be added to itself.");
        }
        if (actor instanceof SuperWindow) {
            throw new IllegalArgumentException("A window cannot be placed inside another window.");
        }
        if (actor.window == this) {
            actor.setLocation(x, y);
            return;
        }
        if (actor.window != null) {
            actor.window.removeObject(actor);
        }
        else if (actor.world != null) {
            actor.world.removeObject(actor);
        }
        actor.window = this;
        contents.add(actor);
        if (world != null) {
            world.addObjectFromWindow(actor, x, y);
        }
        else {
            actor.placeInWindow(x, y);
        }
    }

    /**
     * Remove an actor from this window (and from the world, if the window is in one).
     * Nothing happens if the actor is not in this window.
     *
     * @param actor The actor to remove.
     */
    public void removeObject(Actor actor)
    {
        if (actor == null || actor.window != this) {
            return;
        }
        if (world != null && actor.world == world) {
            world.removeObject(actor); // detaches from this window too
        }
        else {
            detach(actor);
        }
    }

    /**
     * Remove several actors from this window.
     *
     * @param actors The actors to remove; actors not in this window are ignored.
     */
    public void removeObjects(Collection<? extends Actor> actors)
    {
        for (Actor a : new ArrayList<Actor>(actors)) {
            removeObject(a);
        }
    }

    /**
     * Remove every actor from this window.
     */
    public void removeAllObjects()
    {
        removeObjects(new ArrayList<Actor>(contents));
    }

    /**
     * Get all the actors inside this window of the given class (or all of them, if
     * the class is null), in the order they were added.
     *
     * @param cls The class to look for, or null for every actor.
     * @return A list of the matching actors (empty if none).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <A> List<A> getObjects(Class<A> cls)
    {
        List result = new ArrayList();
        for (Actor a : contents) {
            if (cls == null || cls.isInstance(a)) {
                result.add(a);
            }
        }
        return result;
    }

    /**
     * @return The number of actors inside this window.
     */
    public int numberOfObjects()
    {
        return contents.size();
    }

    // ==================================
    //
    // Open, close, minimise, order
    //
    // ==================================

    /**
     * Show this window (after {@link #close()}). Also restores a minimised window
     * and brings the window to the front.
     */
    public void open()
    {
        boolean wasOpen = open;
        open = true;
        if (minimized) {
            restore();
        }
        bringToFront();
        if (!wasOpen) {
            contentsChangedVisibility();
            opened();
        }
    }

    /**
     * Hide this window and everything in it. The window stays in the world, keeping
     * its position and contents, so {@link #open()} shows it again as it was. While
     * closed, the window is not drawn, receives no mouse events, and the actors
     * inside it do not act.
     */
    public void close()
    {
        if (!open) {
            return;
        }
        open = false;
        dragging = false;
        thumbDragging = false;
        pressedOnTitleBar = false;
        pressedOnThumb = false;
        contentsChangedVisibility();
        closed();
    }

    /**
     * @return true if the window is open (visible), false if it is closed.
     */
    public boolean isOpen()
    {
        return open;
    }

    /**
     * Open the window if it is closed, or close it if it is open.
     */
    public void toggle()
    {
        if (open) {
            close();
        }
        else {
            open();
        }
    }

    /**
     * Collapse the window to just its title bar. The actors inside stay put but are
     * hidden and do not act until {@link #restore()}. Does nothing for a window
     * without a title bar.
     */
    public void minimize()
    {
        if (minimized || !titleBarVisible) {
            return;
        }
        int fullHeight = frameHeightPx();
        minimized = true;
        int smallHeight = frameHeightPx();
        // Keep the title bar where it is: the centre moves up by half the difference.
        shiftCentreY(-(fullHeight - smallHeight) / 2.0);
        redrawFrame();
        contentsChangedVisibility();
    }

    /**
     * Restore a minimised window to its full size.
     */
    public void restore()
    {
        if (!minimized) {
            return;
        }
        int smallHeight = frameHeightPx();
        minimized = false;
        int fullHeight = frameHeightPx();
        shiftCentreY((fullHeight - smallHeight) / 2.0);
        redrawFrame();
        contentsChangedVisibility();
    }

    /**
     * @return true if the window is minimised to its title bar.
     */
    public boolean isMinimized()
    {
        return minimized;
    }

    /**
     * Paint this window in front of every other window in its layer. There are three
     * layers, bottom to top: normal windows, modal windows, always-on-top windows.
     * This happens automatically when the window is pressed with the mouse.
     */
    public void bringToFront()
    {
        if (world == null) {
            return;
        }
        double maxZ = Double.NEGATIVE_INFINITY;
        for (SuperWindow w : world.windows) {
            if (w != this && w.layer() == layer()) {
                maxZ = Math.max(maxZ, w.z);
            }
        }
        if (maxZ != Double.NEGATIVE_INFINITY && z <= maxZ) {
            z = maxZ + 1;
        }
    }

    /**
     * Paint this window behind every other window (in the same layer).
     */
    public void sendToBack()
    {
        if (world == null) {
            return;
        }
        double minZ = Double.POSITIVE_INFINITY;
        for (SuperWindow w : world.windows) {
            if (w != this && w.layer() == layer()) {
                minZ = Math.min(minZ, w.z);
            }
        }
        if (minZ != Double.POSITIVE_INFINITY && z >= minZ) {
            z = minZ - 1;
        }
    }

    /**
     * @return true if this window is currently the front-most open window.
     */
    public boolean isFrontWindow()
    {
        if (world == null || !open) {
            return false;
        }
        List<SuperWindow> order = world.getWindowsInPaintOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            if (order.get(i).open) {
                return order.get(i) == this;
            }
        }
        return false;
    }

    // ==================================
    //
    // Size, position and coordinates
    //
    // ==================================

    /**
     * @return The width of the visible content area, in cells.
     */
    public int getWidth()
    {
        return width;
    }

    /**
     * @return The height of the visible content area, in cells.
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * @return The width of the whole window including its border, in pixels.
     */
    public int getFrameWidth()
    {
        return frameWidthPx();
    }

    /**
     * @return The height of the whole window including its border and title bar, in
     *         pixels (only the title bar and border while minimised).
     */
    public int getFrameHeight()
    {
        return frameHeightPx();
    }

    /**
     * Change the size of the visible content area. The frame's top-left corner
     * stays where it is (the window grows or shrinks to the right and down),
     * and it is kept on screen as usual. The content area asked for with
     * {@link #setContentSize(int, int)} is remembered: the content is the larger
     * of the two, so a scrolling window that grows shows more and a shrunken one
     * scrolls again; the scroll position is clamped to fit. Actors inside stay
     * at their positions (clamped into the new area when the window is
     * bounded), and a background you have drawn on is kept and enlarged when
     * the content grows. A skin from {@code setImage} is not resized: set a new
     * one sized {@link #getFrameWidth()} by {@link #getFrameHeight()}.
     *
     * @param width  The new width of the visible content area, in cells.
     * @param height The new height of the visible content area, in cells.
     * @throws IllegalArgumentException if either is less than 1.
     * @since SuperGreenfoot 0.2.0
     */
    public void setSize(int width, int height)
    {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("A window's width and height must be at least 1.");
        }
        if (width == this.width && height == this.height) {
            return;
        }
        int oldFrameWidth = frameWidthPx();
        int oldFrameHeight = frameHeightPx();
        this.width = width;
        this.height = height;
        if (!contentSizeRequested) {
            // No scrolling content was ever asked for: the content is just the visible area.
            requestedContentWidth = width;
            requestedContentHeight = height;
        }
        this.contentWidth = Math.max(width, requestedContentWidth);
        this.contentHeight = Math.max(height, requestedContentHeight);
        int cs = cellSize();
        if (background != null
                && (background.getWidth() < contentWidth * cs || background.getHeight() < contentHeight * cs)) {
            GreenfootImage bigger = new GreenfootImage(Math.max(background.getWidth(), contentWidth * cs),
                    Math.max(background.getHeight(), contentHeight * cs));
            bigger.drawImage(background, 0, 0);
            background = bigger;
        }
        scrollX = Math.max(0, Math.min(scrollX, contentWidth - width));
        scrollY = Math.max(0, Math.min(scrollY, contentHeight - height));
        scrollBarImage = null;
        redrawFrame();
        // Anchor the top-left corner: the centre moves by half the change in size.
        shiftCentre((frameWidthPx() - oldFrameWidth) / 2.0, (frameHeightPx() - oldFrameHeight) / 2.0);
        if (world != null && keepOnScreen) {
            setLocation(preciseX, preciseY);
        }
        if (bounded) {
            for (Actor a : new ArrayList<Actor>(contents)) {
                a.setLocation(a.getX(), a.getY());    // re-clamped into the new area
            }
        }
        contentsMoved();
    }

    /**
     * Move the window so that its top-left corner (of the frame) is at the given
     * world position. {@link #setLocation(int, int)} positions the window's centre
     * instead, like any actor.
     *
     * @param x The x position of the frame's left edge, in world cells.
     * @param y The y position of the frame's top edge, in world cells.
     */
    public void setTopLeft(int x, int y)
    {
        int cs = cellSize();
        double cx = (x * cs + frameWidthPx() / 2.0 - cs / 2.0) / cs;
        double cy = (y * cs + frameHeightPx() / 2.0 - cs / 2.0) / cs;
        setLocation(cx, cy);
    }

    /**
     * @return The world x position of the frame's left edge, in cells.
     */
    public int getLeft()
    {
        return world == null ? 0 : world.toCellFloor(frameLeftPx());
    }

    /**
     * @return The world y position of the frame's top edge, in cells.
     */
    public int getTop()
    {
        return world == null ? 0 : world.toCellFloor(frameTopPx());
    }

    /**
     * Convert an x position inside this window to a world x position.
     *
     * @param localX An x position in the window's coordinates.
     * @return The corresponding x position in the world.
     */
    public int toWorldX(int localX)
    {
        int cs = cellSize();
        return world == null ? localX : world.toCellFloor(contentOriginPixelX() + localX * cs + cs / 2);
    }

    /**
     * Convert a y position inside this window to a world y position.
     *
     * @param localY A y position in the window's coordinates.
     * @return The corresponding y position in the world.
     */
    public int toWorldY(int localY)
    {
        int cs = cellSize();
        return world == null ? localY : world.toCellFloor(contentOriginPixelY() + localY * cs + cs / 2);
    }

    /**
     * Convert a world x position to a position inside this window.
     *
     * @param worldX An x position in the world (for example from {@link MouseInfo#getX()}).
     * @return The corresponding x position in the window's coordinates.
     */
    public int toLocalX(int worldX)
    {
        int cs = cellSize();
        return world == null ? worldX : world.toCellFloor(worldX * cs + cs / 2 - contentOriginPixelX());
    }

    /**
     * Convert a world y position to a position inside this window.
     *
     * @param worldY A y position in the world (for example from {@link MouseInfo#getY()}).
     * @return The corresponding y position in the window's coordinates.
     */
    public int toLocalY(int worldY)
    {
        int cs = cellSize();
        return world == null ? worldY : world.toCellFloor(worldY * cs + cs / 2 - contentOriginPixelY());
    }

    /**
     * Whether the given world position is inside the window's content area (not the
     * border or title bar). Useful with {@link MouseInfo#getX()} and {@link MouseInfo#getY()}.
     *
     * @param worldX An x position in the world.
     * @param worldY A y position in the world.
     * @return true if that position lies within the visible content area.
     */
    public boolean containsWorldPosition(int worldX, int worldY)
    {
        int cs = cellSize();
        return world != null && open && !minimized
                && contentContainsPixel(worldX * cs + cs / 2, worldY * cs + cs / 2);
    }

    /**
     * Move the window, keeping it on screen if {@link #setKeepOnScreen(boolean)} is
     * enabled. As for any actor, the position is the centre of the window; see
     * {@link #setTopLeft(int, int)} to position the corner.
     */
    @Override
    public void setLocation(int x, int y)
    {
        if (world != null && keepOnScreen) {
            super.setLocation(clampCentreX(x), clampCentreY(y));
        }
        else {
            super.setLocation(x, y);
        }
    }

    /**
     * Move the window to a precise position, keeping it on screen if
     * {@link #setKeepOnScreen(boolean)} is enabled.
     */
    @Override
    public void setLocation(double x, double y)
    {
        if (world != null && keepOnScreen) {
            super.setLocation(clampCentreX(x), clampCentreY(y));
        }
        else {
            super.setLocation(x, y);
        }
    }

    // ==================================
    //
    // Scrolling
    //
    // ==================================

    /**
     * Set the size of the window's content area. When it is larger than the visible
     * area ({@link #getWidth()} by {@link #getHeight()}), the content can be
     * scrolled with {@link #setScroll(int, int)}, or with the mouse wheel and the
     * scroll bar if {@link #setScrollable(boolean)} is enabled. The content is never
     * smaller than the visible area.
     *
     * @param contentWidth  The content width, in cells.
     * @param contentHeight The content height, in cells.
     */
    public void setContentSize(int contentWidth, int contentHeight)
    {
        requestedContentWidth = contentWidth;
        requestedContentHeight = contentHeight;
        contentSizeRequested = true;
        applyContentSize();
    }

    /** Work out the content size from the requested one and the visible size. */
    private void applyContentSize()
    {
        this.contentWidth = Math.max(width, requestedContentWidth);
        this.contentHeight = Math.max(height, requestedContentHeight);
        setScroll(scrollX, scrollY);
        redrawFrame();
    }

    /**
     * @return The width of the whole content area, in cells.
     */
    public int getContentWidth()
    {
        return contentWidth;
    }

    /**
     * @return The height of the whole content area, in cells.
     */
    public int getContentHeight()
    {
        return contentHeight;
    }

    /**
     * Scroll the content so that the given content position is at the top-left of
     * the visible area. Values are clamped so the visible area stays within the content.
     *
     * @param x The content x position to show at the left edge, in cells.
     * @param y The content y position to show at the top edge, in cells.
     */
    public void setScroll(int x, int y)
    {
        int newX = Math.max(0, Math.min(x, contentWidth - width));
        int newY = Math.max(0, Math.min(y, contentHeight - height));
        if (newX != scrollX || newY != scrollY) {
            scrollX = newX;
            scrollY = newY;
            scrollBarImage = null;
            contentsMoved();
        }
    }

    /**
     * Scroll the content by the given amount.
     *
     * @param dx Cells to scroll right (negative for left).
     * @param dy Cells to scroll down (negative for up).
     */
    public void scrollBy(int dx, int dy)
    {
        setScroll(scrollX + dx, scrollY + dy);
    }

    /**
     * @return The current horizontal scroll position, in cells.
     */
    public int getScrollX()
    {
        return scrollX;
    }

    /**
     * @return The current vertical scroll position, in cells.
     */
    public int getScrollY()
    {
        return scrollY;
    }

    /**
     * Let the user scroll the window with the mouse wheel and a scroll bar. Only
     * matters when the content is taller than the visible area.
     *
     * @param scrollable true to enable user scrolling.
     */
    public void setScrollable(boolean scrollable)
    {
        this.scrollable = scrollable;
        scrollBarImage = null;
    }

    /**
     * @return true if the user can scroll this window.
     */
    public boolean isScrollable()
    {
        return scrollable;
    }

    // ==================================
    //
    // Appearance
    //
    // ==================================

    /**
     * Get the image drawn behind the actors in the content area, so you can draw on
     * it (like {@link World#getBackground()}). It is the size of the whole content
     * area and starts fully transparent, showing the background colour.
     *
     * @return The background image of the content area.
     */
    public GreenfootImage getBackground()
    {
        if (background == null) {
            int cs = cellSize();
            background = new GreenfootImage(contentWidth * cs, contentHeight * cs);
        }
        return background;
    }

    /**
     * Set the image drawn behind the actors in the content area. It is drawn at the
     * top-left of the content and clipped to the visible area.
     *
     * @param image The new background image (null for none).
     */
    public void setBackground(GreenfootImage image)
    {
        background = image;
    }

    /**
     * Set the colour of the content area, drawn behind the background image. A
     * transparent colour (alpha 0) makes a see-through panel.
     *
     * @param color The new background colour.
     */
    public void setBackgroundColor(Color color)
    {
        backgroundColor = color;
        redrawFrame();
    }

    /**
     * @return The colour of the content area.
     */
    public Color getBackgroundColor()
    {
        return backgroundColor;
    }

    /**
     * Set the colour of the border.
     *
     * @param color The new border colour.
     */
    public void setBorderColor(Color color)
    {
        borderColor = color;
        redrawFrame();
    }

    /**
     * @return The colour of the border.
     */
    public Color getBorderColor()
    {
        return borderColor;
    }

    /**
     * Set the thickness of the border. Zero removes it.
     *
     * @param thickness The border thickness, in pixels (0 or more).
     */
    public void setBorderThickness(int thickness)
    {
        borderThickness = Math.max(0, thickness);
        redrawFrame();
        contentsMoved();
    }

    /**
     * @return The thickness of the border, in pixels.
     */
    public int getBorderThickness()
    {
        return borderThickness;
    }

    /**
     * Set the colour of the title bar.
     *
     * @param color The new title bar colour.
     */
    public void setTitleBarColor(Color color)
    {
        titleBarColor = color;
        redrawFrame();
    }

    /**
     * @return The colour of the title bar.
     */
    public Color getTitleBarColor()
    {
        return titleBarColor;
    }

    /**
     * Set the colour of the title text.
     *
     * @param color The new title colour.
     */
    public void setTitleColor(Color color)
    {
        titleColor = color;
        redrawFrame();
    }

    /**
     * @return The colour of the title text.
     */
    public Color getTitleColor()
    {
        return titleColor;
    }

    /**
     * Set the font of the title text. By default a bold font that fits the title bar.
     *
     * @param font The new title font.
     */
    public void setTitleFont(Font font)
    {
        titleFont = font;
        redrawFrame();
    }

    /**
     * Set the height of the title bar.
     *
     * @param height The title bar height, in pixels (at least 8).
     */
    public void setTitleBarHeight(int height)
    {
        titleBarHeight = Math.max(8, height);
        redrawFrame();
        contentsMoved();
    }

    /**
     * @return The height of the title bar, in pixels.
     */
    public int getTitleBarHeight()
    {
        return titleBarHeight;
    }

    /**
     * Show or hide the title bar. Without a title bar the window cannot be dragged,
     * closed or minimised with the mouse.
     *
     * @param visible true to show the title bar.
     */
    public void setTitleBarVisible(boolean visible)
    {
        if (visible == titleBarVisible) {
            return;
        }
        if (!visible && minimized) {
            restore();
        }
        int before = frameHeightPx();
        titleBarVisible = visible;
        int after = frameHeightPx();
        // Keep the content where it is: the frame grows or shrinks at the top.
        shiftCentreY(-(after - before) / 2.0);
        redrawFrame();
        contentsMoved();
    }

    /**
     * @return true if the title bar is shown.
     */
    public boolean isTitleBarVisible()
    {
        return titleBarVisible;
    }

    /**
     * Set the title text.
     *
     * @param title The new title.
     */
    public void setTitle(String title)
    {
        this.title = title == null ? "" : title;
        redrawFrame();
    }

    /**
     * @return The title text.
     */
    public String getTitle()
    {
        return title;
    }

    // ==================================
    //
    // Behaviour flags
    //
    // ==================================

    /**
     * Show or hide the close button.
     *
     * @param closable true to let the user close the window.
     */
    public void setClosable(boolean closable)
    {
        this.closable = closable;
        redrawFrame();
    }

    /**
     * @return true if the window has a close button.
     */
    public boolean isClosable()
    {
        return closable;
    }

    /**
     * Show or hide the minimise button.
     *
     * @param minimizable true to let the user minimise the window.
     */
    public void setMinimizable(boolean minimizable)
    {
        this.minimizable = minimizable;
        redrawFrame();
    }

    /**
     * @return true if the window has a minimise button.
     */
    public boolean isMinimizable()
    {
        return minimizable;
    }

    /**
     * Allow or prevent dragging the window by its title bar.
     *
     * @param draggable true to allow dragging; false locks the window in place.
     */
    public void setDraggable(boolean draggable)
    {
        this.draggable = draggable;
        if (!draggable) {
            dragging = false;
        }
    }

    /**
     * @return true if the user can drag the window.
     */
    public boolean isDraggable()
    {
        return draggable;
    }

    /**
     * Keep the whole window inside the world when it is moved (the default). With
     * this off, a window can be moved partly off screen.
     *
     * @param keepOnScreen true to keep the window on screen.
     */
    public void setKeepOnScreen(boolean keepOnScreen)
    {
        this.keepOnScreen = keepOnScreen;
        if (keepOnScreen && world != null) {
            setLocation(getPreciseX(), getPreciseY());
        }
    }

    /**
     * @return true if the window is kept on screen.
     */
    public boolean isKeepOnScreen()
    {
        return keepOnScreen;
    }

    /**
     * Keep the actors inside this window within its content area (like a bounded
     * world). Off by default: actors may be placed outside the visible area, where
     * they are clipped.
     *
     * @param bounded true to keep actors inside the content area.
     */
    public void setBounded(boolean bounded)
    {
        this.bounded = bounded;
    }

    /**
     * @return true if actors inside this window are kept within its content area.
     */
    public boolean isBounded()
    {
        return bounded;
    }

    /**
     * Make this window modal: while it is open, mouse events outside it are reported
     * as being on this window, so nothing behind it can be clicked, and it stays
     * above every normal window. Use it for a pause menu or a "you died" screen.
     *
     * @param modal true to make the window modal.
     */
    public void setModal(boolean modal)
    {
        this.modal = modal;
    }

    /**
     * @return true if this window is modal.
     */
    public boolean isModal()
    {
        return modal;
    }

    /**
     * Keep this window above all normal and modal windows, whatever their z. Use it
     * for tooltips and notifications.
     *
     * @param alwaysOnTop true to keep the window on top.
     */
    public void setAlwaysOnTop(boolean alwaysOnTop)
    {
        this.alwaysOnTop = alwaysOnTop;
    }

    /**
     * @return true if this window stays above normal windows.
     */
    public boolean isAlwaysOnTop()
    {
        return alwaysOnTop;
    }

    // ==================================
    //
    // Mouse
    //
    // ==================================

    /**
     * Whether the mouse is currently over this window (its frame or its contents)
     * and no other window is in front of it at that point.
     *
     * @return true if the mouse is over this window.
     */
    public boolean isMouseOver()
    {
        if (world == null || !open) {
            return false;
        }
        MouseInfo info = WorldHandler.getInstance().getMouseManager().getMouseInfo();
        return info != null && world.getWindowAtPixel(info.getPx(), info.getPy()) == this;
    }

    /**
     * @return true while the user is dragging this window by its title bar.
     */
    public boolean isBeingDragged()
    {
        return dragging;
    }

    // ==================================
    //
    // Hooks for subclasses
    //
    // ==================================

    /**
     * Called when the user clicks the close button. By default this calls
     * {@link #close()}; override it to do something else (for example to ask for
     * confirmation, or to remove the window from the world instead).
     */
    protected void closeButtonClicked()
    {
        close();
    }

    /**
     * Called when the user clicks the minimise button. By default this minimises the
     * window, or restores it if it is already minimised.
     */
    protected void minimizeButtonClicked()
    {
        if (minimized) {
            restore();
        }
        else {
            minimize();
        }
    }

    /**
     * Called after the window has been opened with {@link #open()} (not when it is
     * first added to the world). Override to refresh the window's contents.
     */
    protected void opened()
    {
    }

    /**
     * Called after the window has been closed with {@link #close()} or its close
     * button.
     */
    protected void closed()
    {
    }

    // ==================================
    //
    // Package-private: geometry used by Actor, World and the renderer
    //
    // ==================================

    /** The world's cell size, or 1 before the window is in a world. */
    int cellSize()
    {
        return world == null ? 1 : world.cellSize;
    }

    int titleBarPx()
    {
        return titleBarVisible ? titleBarHeight : 0;
    }

    int frameWidthPx()
    {
        return width * cellSize() + 2 * borderThickness;
    }

    int frameHeightPx()
    {
        return (minimized ? 0 : height * cellSize()) + 2 * borderThickness + titleBarPx();
    }

    /** World pixel x of the frame's left edge, matching where the renderer paints the image. */
    int frameLeftPx()
    {
        int cs = cellSize();
        return (int) Math.floor(x * cs + cs / 2.0 - frameImage.getWidth() / 2.0);
    }

    /** World pixel y of the frame's top edge. */
    int frameTopPx()
    {
        int cs = cellSize();
        return (int) Math.floor(y * cs + cs / 2.0 - frameImage.getHeight() / 2.0);
    }

    /** World pixel x of local content cell 0 (after scrolling). */
    int contentOriginPixelX()
    {
        return frameLeftPx() + borderThickness - scrollX * cellSize();
    }

    /** World pixel y of local content cell 0 (after scrolling). */
    int contentOriginPixelY()
    {
        return frameTopPx() + borderThickness + titleBarPx() - scrollY * cellSize();
    }

    /** World pixel x of the visible content area's left edge. */
    int contentLeftPx()
    {
        return frameLeftPx() + borderThickness;
    }

    /** World pixel y of the visible content area's top edge. */
    int contentTopPx()
    {
        return frameTopPx() + borderThickness + titleBarPx();
    }

    int contentWidthPx()
    {
        return width * cellSize();
    }

    int contentHeightPx()
    {
        return height * cellSize();
    }

    boolean contentContainsPixel(int px, int py)
    {
        int left = contentLeftPx();
        int top = contentTopPx();
        return px >= left && px < left + contentWidthPx() && py >= top && py < top + contentHeightPx();
    }

    boolean frameContainsPixel(int px, int py)
    {
        int left = frameLeftPx();
        int top = frameTopPx();
        return px >= left && px < left + frameImage.getWidth() && py >= top && py < top + frameImage.getHeight();
    }

    /** True while the contents are visible: open and not minimised. */
    boolean isContentActive()
    {
        return open && !minimized;
    }

    @Override
    boolean isActive()
    {
        return open;
    }

    GreenfootImage getFrameImage()
    {
        return frameImage;
    }

    GreenfootImage getBackgroundNoInit()
    {
        return background;
    }

    /** The scroll bar drawn over the right edge of the content, or null if none is needed. */
    GreenfootImage getScrollBarImage()
    {
        if (!scrollable || contentHeight <= height || minimized) {
            return null;
        }
        if (scrollBarImage == null) {
            int h = contentHeightPx();
            GreenfootImage img = new GreenfootImage(SCROLL_BAR_WIDTH, h);
            img.setColor(SCROLL_TRACK_COLOR);
            img.fill();
            img.setColor(SCROLL_THUMB_COLOR);
            img.fillRect(1, thumbTop(), SCROLL_BAR_WIDTH - 2, thumbHeight());
            scrollBarImage = img;
        }
        return scrollBarImage;
    }

    private int thumbHeight()
    {
        int h = contentHeightPx();
        return Math.max(12, (int) Math.round((double) h * height / contentHeight));
    }

    private int thumbTop()
    {
        int h = contentHeightPx();
        int travel = h - thumbHeight();
        int maxScroll = contentHeight - height;
        return maxScroll <= 0 ? 0 : (int) Math.round((double) travel * scrollY / maxScroll);
    }

    /**
     * Use your own picture for the whole window frame instead of the one the window
     * draws for itself (a "skin"). The picture is drawn centred on the window, and
     * should be the size of {@link #getFrameWidth()} by {@link #getFrameHeight()} so
     * that the content area, title bar and buttons line up with it.
     *
     * <p>Once you have set a picture, the window keeps it: changing the title,
     * colours or border, or adding the window to a world, no longer redraws the
     * frame, so the window's own border, title bar, title text and buttons are not
     * drawn over your picture (the title bar still drags and the buttons still work
     * where they would normally be). Pass null to go back to the frame the window
     * draws for itself.
     *
     * @param image The picture for the frame, or null for the normal drawn frame.
     */
    @Override
    public void setImage(GreenfootImage image)
    {
        if (image == null) {
            customFrame = false;
            redrawFrame();
            return;
        }
        customFrame = true;
        applyFrameImage(image);
    }

    /** Show the given frame image, remembering it so geometry can use its size. */
    private void applyFrameImage(GreenfootImage image)
    {
        super.setImage(image);
        frameImage = image;
    }

    /**
     * Rebuild the frame image: background colour, border, title bar with title and
     * buttons. The button positions are always updated, but a skin set with
     * {@link #setImage(GreenfootImage)} is kept rather than replaced.
     */
    void redrawFrame()
    {
        int fw = frameWidthPx();
        int fh = frameHeightPx();
        GreenfootImage img = new GreenfootImage(fw, fh);
        if (backgroundColor != null && backgroundColor.getAlpha() > 0) {
            img.setColor(backgroundColor);
            img.fill();
        }
        if (borderThickness > 0 && borderColor != null) {
            img.setColor(borderColor);
            for (int i = 0; i < borderThickness; i++) {
                img.drawRect(i, i, fw - 1 - 2 * i, fh - 1 - 2 * i);
            }
        }
        closeSize = -1;
        minSize = -1;
        if (titleBarVisible) {
            int b = borderThickness;
            img.setColor(titleBarColor);
            img.fillRect(b, b, fw - 2 * b, titleBarHeight);

            int buttonSize = Math.max(6, titleBarHeight - 8);
            int buttonTop = b + (titleBarHeight - buttonSize) / 2;
            int nextRight = fw - b - 5;
            if (closable) {
                closeSize = buttonSize;
                closeLeft = nextRight - buttonSize;
                closeTop = buttonTop;
                img.setColor(CLOSE_BUTTON_COLOR);
                img.fillOval(closeLeft, closeTop, buttonSize, buttonSize);
                nextRight = closeLeft - 5;
            }
            if (minimizable) {
                minSize = buttonSize;
                minLeft = nextRight - buttonSize;
                minTop = buttonTop;
                img.setColor(MINIMIZE_BUTTON_COLOR);
                img.fillOval(minLeft, minTop, buttonSize, buttonSize);
                nextRight = minLeft - 5;
            }
            if (title != null && title.length() > 0) {
                Font f = titleFont != null ? titleFont : new Font("SansSerif", true, false, Math.max(8, titleBarHeight - 8));
                img.setFont(f);
                img.setColor(titleColor);
                int size = f.getSize();
                int baseline = b + (titleBarHeight + size) / 2 - 1;
                img.drawString(title, b + 6, baseline);
            }
        }
        if (!customFrame || frameImage == null) {
            applyFrameImage(img);
        }
    }

    /** Shift the centre vertically by a number of pixels without the on-screen clamp fighting it. */
    private void shiftCentreY(double pixels)
    {
        shiftCentre(0, pixels);
    }

    /** Shift the centre by a number of pixels each way without the on-screen clamp fighting it. */
    private void shiftCentre(double dxPixels, double dyPixels)
    {
        if (world == null || (dxPixels == 0 && dyPixels == 0)) {
            return;
        }
        int cs = cellSize();
        super.setLocation(preciseX + dxPixels / cs, preciseY + dyPixels / cs);
        contentsMoved();
    }

    private double clampCentreX(double cx)
    {
        int cs = cellSize();
        double half = frameImage.getWidth() / 2.0;
        double px = cx * cs + cs / 2.0;
        double max = world.width * cs - half;
        px = Math.max(half, Math.min(px, max));
        if (frameImage.getWidth() > world.width * cs) {
            px = half;
        }
        return (px - cs / 2.0) / cs;
    }

    private double clampCentreY(double cy)
    {
        int cs = cellSize();
        double half = frameImage.getHeight() / 2.0;
        double py = cy * cs + cs / 2.0;
        double max = world.height * cs - half;
        py = Math.max(half, Math.min(py, max));
        if (frameImage.getHeight() > world.height * cs) {
            py = half;
        }
        return (py - cs / 2.0) / cs;
    }

    /** The window moved: every actor inside has new world bounds. */
    @Override
    void movedInWorld()
    {
        contentsMoved();
    }

    void contentsMoved()
    {
        if (world == null) {
            return;
        }
        for (Actor a : contents) {
            a.containerMoved();
        }
    }

    private void contentsChangedVisibility()
    {
        // Bounds may be stale after a minimise/restore shift; refresh them.
        contentsMoved();
    }

    /** Called by World when the window has just been added to a world. */
    void attachedToWorld()
    {
        redrawFrame();
        if (keepOnScreen) {
            setLocation(preciseX, preciseY);
        }
        for (Actor a : new ArrayList<Actor>(contents)) {
            int cellX = a.x, cellY = a.y;
            double exactX = a.preciseX, exactY = a.preciseY;
            world.addObjectFromWindow(a, cellX, cellY);
            if (a.window == this && a.x == cellX && a.y == cellY) {
                // Joining the world places the actor at a whole cell; put back the
                // precise position it had (unless its own code moved it meanwhile).
                a.preciseX = exactX;
                a.preciseY = exactY;
            }
        }
    }

    /** Called by World just before the window is removed from a world. */
    void detachingFromWorld()
    {
        dragging = false;
        thumbDragging = false;
        pressedOnTitleBar = false;
        pressedOnThumb = false;
        for (Actor a : new ArrayList<Actor>(contents)) {
            world.removeObjectKeepingWindow(a);
        }
    }

    /** Take an actor out of this window without touching the world. */
    void detach(Actor actor)
    {
        contents.remove(actor);
        actor.window = null;
    }

    private boolean onTitleBar(int lx, int ly)
    {
        return titleBarVisible && ly >= borderThickness && ly < borderThickness + titleBarHeight
                && lx >= borderThickness && lx < frameImage.getWidth() - borderThickness;
    }

    private boolean onCloseButton(int lx, int ly)
    {
        return closeSize > 0 && lx >= closeLeft - 2 && lx < closeLeft + closeSize + 2
                && ly >= closeTop - 2 && ly < closeTop + closeSize + 2;
    }

    private boolean onMinimizeButton(int lx, int ly)
    {
        return minSize > 0 && lx >= minLeft - 2 && lx < minLeft + minSize + 2
                && ly >= minTop - 2 && ly < minTop + minSize + 2;
    }

    private boolean onScrollBar(int px, int py)
    {
        if (getScrollBarImage() == null) {
            return false;
        }
        int right = contentLeftPx() + contentWidthPx();
        return px >= right - SCROLL_BAR_WIDTH && px < right && py >= contentTopPx() && py < contentTopPx() + contentHeightPx();
    }

    private boolean onScrollThumb(int px, int py)
    {
        if (!onScrollBar(px, py)) {
            return false;
        }
        int ty = contentTopPx() + thumbTop();
        return py >= ty && py < ty + thumbHeight();
    }

    /**
     * Handle this act's mouse events for the window: bring to front, buttons, title
     * bar dragging, wheel scrolling and the scroll bar. Called by the simulation
     * once per act, before the world and the actors act.
     */
    void handleInput(MousePollingManager mm)
    {
        if (world == null || !open) {
            return;
        }
        MouseInfo info = mm.getMouseInfo();
        if (info == null) {
            return;
        }
        int px = info.getPx();
        int py = info.getPy();
        int lx = px - frameLeftPx();
        int ly = py - frameTopPx();
        Actor target = info.getActor();
        boolean onWindow = target == this || (target != null && target.window == this);

        boolean pressed = mm.isMousePressed(null);
        if (pressed) {
            // Every press starts afresh, wherever it lands: a flag left over from an
            // earlier press must not let a later drag elsewhere move this window or
            // its scroll bar.
            pressedOnTitleBar = false;
            pressedOnThumb = false;
        }
        if (onWindow && pressed) {
            bringToFront();
            pressPx = px;
            pressPy = py;
            pressedOnTitleBar = target == this && onTitleBar(lx, ly)
                    && !onCloseButton(lx, ly) && !onMinimizeButton(lx, ly);
            pressedOnThumb = onScrollThumb(px, py);
            if (scrollable && !pressedOnThumb && onScrollBar(px, py)) {
                // Click in the track: page towards the click.
                int ty = contentTopPx() + thumbTop();
                scrollBy(0, py < ty ? -height : height);
            }
        }

        if (titleBarVisible && mm.isMouseClicked(this)) {
            if (closable && onCloseButton(lx, ly)) {
                closeButtonClicked();
                return;
            }
            if (minimizable && onMinimizeButton(lx, ly)) {
                minimizeButtonClicked();
                return;
            }
        }

        if (draggable && titleBarVisible) {
            if (!dragging && pressedOnTitleBar && mm.isMouseDragged(this)) {
                dragging = true;
                int cs = cellSize();
                dragOffsetX = (x * cs + cs / 2) - pressPx;
                dragOffsetY = (y * cs + cs / 2) - pressPy;
            }
            if (dragging) {
                if (mm.isMouseDragged(this)) {
                    int cs = cellSize();
                    setLocation((px + dragOffsetX - cs / 2.0) / cs, (py + dragOffsetY - cs / 2.0) / cs);
                }
                if (mm.isMouseDragEnded(this)) {
                    dragging = false;
                }
            }
        }

        if (scrollable) {
            if (onWindow && mm.isMouseScrolled(null) && info.getScrollAmount() != 0) {
                int cs = cellSize();
                int cells = (int) Math.round(info.getScrollAmount() / (double) cs);
                if (cells == 0) {
                    cells = info.getScrollAmount() > 0 ? 1 : -1;
                }
                scrollBy(0, cells);
            }
            if (!thumbDragging && pressedOnThumb && mm.isMouseDragged(null)) {
                thumbDragging = true;
                thumbDragStartScroll = scrollY;
                thumbDragStartPy = pressPy;
            }
            if (thumbDragging) {
                if (mm.isMouseDragged(null)) {
                    int travel = contentHeightPx() - thumbHeight();
                    int maxScroll = contentHeight - height;
                    if (travel > 0) {
                        int delta = (int) Math.round((double) (py - thumbDragStartPy) * maxScroll / travel);
                        setScroll(scrollX, thumbDragStartScroll + delta);
                    }
                }
                if (mm.isMouseDragEnded(null)) {
                    thumbDragging = false;
                }
            }
        }

        if (mm.isMouseDragEnded(null) || mm.isMouseReleased(null)) {
            // The button is up: whatever the press was on, it is over.
            pressedOnTitleBar = false;
            pressedOnThumb = false;
        }
    }

    /** 0 for a normal window, 1 for a modal one, 2 for always-on-top. */
    int layer()
    {
        return alwaysOnTop ? 2 : (modal ? 1 : 0);
    }

    /** Sort key among windows: layer, then z, then creation order. */
    static int compareOrder(SuperWindow a, SuperWindow b)
    {
        if (a.layer() != b.layer()) {
            return Integer.compare(a.layer(), b.layer());
        }
        int c = Double.compare(a.z, b.z);
        if (c != 0) {
            return c;
        }
        return Integer.compare(a.getSequenceNumber(), b.getSequenceNumber());
    }
}
