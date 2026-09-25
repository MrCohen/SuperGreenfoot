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

import greenfoot.collision.ibsp.Rect;
import greenfoot.core.Simulation;
import greenfoot.core.WorldHandler;
import greenfoot.gui.WorldRenderer;
import greenfoot.gui.input.mouse.MousePollingManager;
import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Headless tests for SuperWindow: containment, coordinates, world transfer,
 * painting, clipping, picking, collision filtering, scrolling and the engine-driven
 * mouse behaviour (bring to front, buttons, dragging, modal, wheel).
 */
public class SuperWindowTest extends TestCase
{
    /** A 10x10 actor with a solid red image, so painted pixels can be checked. */
    private static class Item extends TestObject
    {
        final String name;
        int acts = 0;

        Item(String name)
        {
            super(10, 10);
            this.name = name;
            getImage().setColor(Color.RED);
            getImage().fill();
        }

        @Override
        public void act()
        {
            acts++;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private static class Ground extends TestObject
    {
        Ground()
        {
            super(10, 10);
            getImage().setColor(Color.BLUE);
            getImage().fill();
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        Simulation.initialize();
    }

    private static World newWorld()
    {
        return WorldCreator.createWorld(200, 200, 1);
    }

    private static BufferedImage render(World world)
    {
        BufferedImage img = new BufferedImage(world.getWidthInPixels(), world.getHeightInPixels(),
                BufferedImage.TYPE_INT_ARGB);
        new WorldRenderer().renderWorld(world, img);
        return img;
    }

    private static int argb(BufferedImage img, int x, int y)
    {
        return img.getRGB(x, y);
    }

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private static String paintedOrder(World world, Actor... actors)
    {
        render(world);
        List<Actor> sorted = new ArrayList<Actor>();
        for (Actor a : actors) {
            sorted.add(a);
        }
        sorted.sort((p, q) -> Integer.compare(ActorVisitor.getLastPaintSeqNum(p), ActorVisitor.getLastPaintSeqNum(q)));
        StringBuilder sb = new StringBuilder();
        for (Actor a : sorted) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(a.toString());
        }
        return sb.toString();
    }

    /** A titled window whose frame is exactly placed: content origin at world (100, 100). */
    private static SuperWindow placedWindow(World world, int w, int h)
    {
        SuperWindow win = new SuperWindow(w, h, "Test");
        world.addObject(win, 0, 0);
        win.setTopLeft(100 - win.getBorderThickness(), 100 - win.getBorderThickness() - win.getTitleBarHeight());
        assertEquals(100, win.toWorldX(0));
        assertEquals(100, win.toWorldY(0));
        return win;
    }

    // ---- containment and coordinates ----

    public void testChildAddedBeforeWorldJoinsWorldWithWindow()
    {
        World world = newWorld();
        SuperWindow win = new SuperWindow(80, 60, "Inventory");
        Item item = new Item("item");
        win.addObject(item, 10, 20);

        assertNull(item.getWorld());
        assertSame(win, item.getParentWindow());
        assertEquals(10, item.getX());
        assertEquals(20, item.getY());
        assertEquals(1, win.numberOfObjects());

        world.addObject(win, 100, 100);
        assertSame(world, item.getWorld());
        assertSame(world, win.getWorld());
        assertEquals(10, item.getX());
        assertEquals(20, item.getY());
        assertTrue(world.getObjects(Item.class).contains(item));
        assertEquals(2, world.numberOfObjects());
    }

    public void testChildWorldBoundsFollowWindow()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 80, 60);
        Item item = new Item("item");
        win.addObject(item, 10, 20);

        // Local (10,20) with a 10x10 image: world pixel centre (110,120), bounds from 105.
        Rect r = ActorVisitor.getBoundingRect(item);
        assertEquals(105, r.getX());
        assertEquals(115, r.getY());

        win.setLocation(win.getX() + 10, win.getY() + 5);
        r = ActorVisitor.getBoundingRect(item);
        assertEquals(115, r.getX());
        assertEquals(120, r.getY());
        // Local coordinates are unchanged
        assertEquals(10, item.getX());
        assertEquals(20, item.getY());
        assertEquals(120, win.toWorldX(10));
        assertEquals(10, win.toLocalX(120));
    }

    public void testTransferBetweenWorldsKeepsContents()
    {
        World world1 = newWorld();
        SuperWindow win = new SuperWindow(80, 60, "Bag");
        Item a = new Item("a"), b = new Item("b");
        win.addObject(a, 5, 5);
        world1.addObject(win, 100, 100);
        win.addObject(b, 15, 5);
        assertEquals(3, world1.numberOfObjects());

        World world2 = newWorld();
        world2.addObject(win, 60, 60);
        assertEquals(0, world1.numberOfObjects());
        assertEquals(3, world2.numberOfObjects());
        assertSame(world2, a.getWorld());
        assertSame(world2, b.getWorld());
        assertSame(win, a.getParentWindow());
        assertEquals(5, a.getX());
        assertEquals(15, b.getX());
        assertEquals(2, win.numberOfObjects());
        assertEquals(1, world2.getWindows().size());
        assertEquals(0, world1.getWindows().size());
    }

    public void testRemovingWindowRemovesContentsButKeepsThem()
    {
        World world = newWorld();
        SuperWindow win = new SuperWindow(80, 60, "Bag");
        Item a = new Item("a");
        win.addObject(a, 5, 5);
        world.addObject(win, 100, 100);
        world.removeObject(win);
        assertNull(a.getWorld());
        assertSame(win, a.getParentWindow());
        assertEquals(5, a.getX());
        assertEquals(1, win.numberOfObjects());
        assertEquals(0, world.numberOfObjects());
    }

    public void testRemoveObjectFromWindowAndWorld()
    {
        World world = newWorld();
        SuperWindow win = new SuperWindow(80, 60, "Bag");
        Item a = new Item("a"), b = new Item("b");
        world.addObject(win, 100, 100);
        win.addObject(a, 5, 5);
        win.addObject(b, 5, 5);

        win.removeObject(a);
        assertNull(a.getWorld());
        assertNull(a.getParentWindow());
        assertEquals(1, win.numberOfObjects());

        // Removing straight from the world also takes it out of the window
        world.removeObject(b);
        assertNull(b.getParentWindow());
        assertEquals(0, win.numberOfObjects());
        assertEquals(1, world.numberOfObjects());
    }

    public void testAddingToWorldDirectlyLeavesWindow()
    {
        World world = newWorld();
        SuperWindow win = new SuperWindow(80, 60, "Bag");
        Item a = new Item("a");
        world.addObject(win, 100, 100);
        win.addObject(a, 5, 5);

        world.addObject(a, 30, 40);
        assertNull(a.getParentWindow());
        assertEquals(30, a.getX());
        assertEquals(0, win.numberOfObjects());
        assertEquals(2, world.numberOfObjects());
    }

    public void testMovingBetweenWindows()
    {
        World world = newWorld();
        SuperWindow w1 = new SuperWindow(80, 60, "One");
        SuperWindow w2 = new SuperWindow(80, 60, "Two");
        world.addObject(w1, 50, 50);
        world.addObject(w2, 150, 150);
        Item a = new Item("a");
        w1.addObject(a, 5, 5);
        w2.addObject(a, 7, 8);
        assertSame(w2, a.getParentWindow());
        assertEquals(0, w1.numberOfObjects());
        assertEquals(1, w2.numberOfObjects());
        assertEquals(7, a.getX());
        assertEquals(3, world.numberOfObjects());
    }

    public void testBoundedWindowClampsContents()
    {
        World world = newWorld();
        SuperWindow win = new SuperWindow(50, 40, "Bag");
        win.setBounded(true);
        world.addObject(win, 100, 100);
        Item a = new Item("a");
        win.addObject(a, 500, -3);
        assertEquals(49, a.getX());
        assertEquals(0, a.getY());
        a.setLocation(-10, 100);
        assertEquals(0, a.getX());
        assertEquals(39, a.getY());
        assertTrue(a.isAtEdge());
    }

    public void testWindowCannotContainWindow()
    {
        SuperWindow outer = new SuperWindow(80, 60, "Outer");
        SuperWindow inner = new SuperWindow(20, 20, "Inner");
        try {
            outer.addObject(inner, 0, 0);
            fail("nested windows should be rejected");
        }
        catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- painting ----

    public void testWindowsPaintAboveActorsInZOrder()
    {
        World world = newWorld();
        Ground g1 = new Ground(), g2 = new Ground();
        SuperWindow w1 = new SuperWindow(40, 40, "One");
        SuperWindow w2 = new SuperWindow(40, 40, "Two");
        Item i1 = new Item("i1"), i2 = new Item("i2");
        world.addObject(w1, 60, 60);
        world.addObject(g1, 10, 10);
        world.addObject(w2, 120, 120);
        world.addObject(g2, 20, 20);
        w1.addObject(i1, 5, 5);
        w2.addObject(i2, 5, 5);

        render(world);
        int seqG1 = ActorVisitor.getLastPaintSeqNum(g1);
        int seqG2 = ActorVisitor.getLastPaintSeqNum(g2);
        int seqW1 = ActorVisitor.getLastPaintSeqNum(w1);
        int seqI1 = ActorVisitor.getLastPaintSeqNum(i1);
        int seqW2 = ActorVisitor.getLastPaintSeqNum(w2);
        int seqI2 = ActorVisitor.getLastPaintSeqNum(i2);
        assertTrue(seqG1 < seqG2);
        assertTrue(seqG2 < seqW1);
        assertTrue(seqW1 < seqI1);
        assertTrue(seqI1 < seqW2);
        assertTrue(seqW2 < seqI2);

        w1.bringToFront();
        render(world);
        assertTrue(ActorVisitor.getLastPaintSeqNum(w2) < ActorVisitor.getLastPaintSeqNum(w1));
        assertTrue(ActorVisitor.getLastPaintSeqNum(i2) < ActorVisitor.getLastPaintSeqNum(w1));
        assertTrue(w1.isFrontWindow());
        assertFalse(w2.isFrontWindow());

        w2.setAlwaysOnTop(true);
        render(world);
        assertTrue(ActorVisitor.getLastPaintSeqNum(w1) < ActorVisitor.getLastPaintSeqNum(w2));
        assertTrue(w2.isFrontWindow());
    }

    public void testModalWindowsStayAboveNormalOnes()
    {
        World world = newWorld();
        SuperWindow normal = new SuperWindow(40, 40, "Normal");
        SuperWindow modal = new SuperWindow(40, 40, "Modal");
        SuperWindow tip = new SuperWindow(40, 40, "Tip");
        modal.setModal(true);
        tip.setAlwaysOnTop(true);
        world.addObject(tip, 60, 60);
        world.addObject(modal, 60, 60);
        world.addObject(normal, 60, 60);
        render(world);
        assertTrue(ActorVisitor.getLastPaintSeqNum(normal) < ActorVisitor.getLastPaintSeqNum(modal));
        assertTrue(ActorVisitor.getLastPaintSeqNum(modal) < ActorVisitor.getLastPaintSeqNum(tip));
        // Bringing a normal window to the front does not lift it above the modal one
        normal.bringToFront();
        normal.open();
        render(world);
        assertTrue(ActorVisitor.getLastPaintSeqNum(normal) < ActorVisitor.getLastPaintSeqNum(modal));
        assertEquals("[Normal, Modal, Tip]", names(world.getWindows()));
    }

    private static String names(List<SuperWindow> windows)
    {
        List<String> out = new ArrayList<String>();
        for (SuperWindow w : windows) {
            out.add(w.getTitle());
        }
        return out.toString();
    }

    public void testContentsAreClippedToTheWindow()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        win.setBackgroundColor(new Color(0, 0, 0, 0));
        Item item = new Item("item");
        // Centre at local (-2, 5): the left part of the 10x10 image lies outside the content.
        win.addObject(item, -2, 5);

        BufferedImage img = render(world);
        // Inside the content area (world x 100..103 is item pixels 7..9 of the image)
        assertEquals(RED, argb(img, 100, 105));
        assertEquals(RED, argb(img, 102, 105));
        // Just left of the content area: not painted (the border is there instead)
        assertTrue(argb(img, 97, 105) != RED);
        assertTrue(argb(img, 96, 105) != RED);
    }

    public void testClosedWindowIsNotPaintedAndContentsDoNotAct()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        Item item = new Item("item");
        win.addObject(item, 10, 10);

        BufferedImage img = render(world);
        assertEquals(RED, argb(img, 110, 110));
        assertTrue(ActorVisitor.isActing(item));

        win.close();
        assertFalse(win.isOpen());
        img = render(world);
        assertTrue(argb(img, 110, 110) != RED);
        assertFalse(ActorVisitor.isActing(item));
        assertTrue(ActorVisitor.isActing(win));
        assertTrue(WorldVisitor.getObjectsAtPixel(world, 110, 110).isEmpty());

        win.open();
        assertTrue(win.isOpen());
        img = render(world);
        assertEquals(RED, argb(img, 110, 110));
        assertTrue(ActorVisitor.isActing(item));
    }

    public void testMinimizeKeepsTitleBarInPlace()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        Item item = new Item("item");
        win.addObject(item, 10, 10);
        int top = win.getTop();
        int frameHeight = win.getFrameHeight();

        win.minimize();
        assertTrue(win.isMinimized());
        assertEquals(top, win.getTop());
        assertEquals(win.getTitleBarHeight() + 2 * win.getBorderThickness(), win.getFrameHeight());
        assertFalse(ActorVisitor.isActing(item));
        BufferedImage img = render(world);
        assertTrue(argb(img, 110, 110) != RED);

        win.restore();
        assertFalse(win.isMinimized());
        assertEquals(top, win.getTop());
        assertEquals(frameHeight, win.getFrameHeight());
        assertTrue(ActorVisitor.isActing(item));
        img = render(world);
        assertEquals(RED, argb(img, 110, 110));
    }

    // ---- picking ----

    public void testPickingSeesWindowContentsOnlyInsideContentArea()
    {
        World world = newWorld();
        Ground ground = new Ground();
        world.addObject(ground, 110, 110);
        SuperWindow win = placedWindow(world, 40, 30);
        Item item = new Item("item");
        win.addObject(item, -2, 5);
        render(world);

        // Inside the content: the item (topmost), the ground actor is underneath
        Collection<Actor> at = WorldVisitor.getObjectsAtPixel(world, 101, 105);
        assertTrue(at.contains(item));
        assertSame(item, last(at));
        // Under the window frame, over the ground actor: the window wins
        at = WorldVisitor.getObjectsAtPixel(world, 110, 110);
        assertSame(win, last(at));
        assertTrue(at.contains(ground));
        // Over the clipped-away part of the item (on the window's border): the item is not hit
        at = WorldVisitor.getObjectsAtPixel(world, 98, 105);
        assertFalse(at.contains(item));
        assertSame(win, last(at));
        // Outside the frame altogether: nothing
        assertTrue(WorldVisitor.getObjectsAtPixel(world, 97, 105).isEmpty());
    }

    private static Actor last(Collection<Actor> actors)
    {
        Actor result = null;
        for (Actor a : actors) {
            result = a;
        }
        return result;
    }

    // ---- collision filtering ----

    public void testCollisionQueriesStayWithinWindow()
    {
        World world = newWorld();
        Ground ground = new Ground();
        world.addObject(ground, 110, 110);
        SuperWindow win = placedWindow(world, 40, 30);
        Item item = new Item("item"), sibling = new Item("sibling");
        win.addObject(item, 10, 10);       // world centre (110,110): overlaps ground
        win.addObject(sibling, 14, 10);    // overlaps item

        TestObject groundT = ground;
        // The window frame itself is not a collision partner unless asked for by class
        assertTrue(groundT.getIntersectingObjectsP(null).isEmpty());
        assertTrue(groundT.getIntersectingObjectsP(SuperWindow.class).contains(win));
        assertTrue(groundT.isTouchingP(SuperWindow.class));
        assertNull(groundT.getOneIntersectingObjectP(Item.class));
        assertFalse(groundT.isTouchingP(Item.class));
        assertTrue(groundT.getObjectsInRangeP(50, Item.class).isEmpty());
        assertTrue(groundT.getNeighboursP(5, true, Item.class).isEmpty());

        List<?> seen = item.getIntersectingObjectsP(null);
        assertEquals(1, seen.size());
        assertSame(sibling, seen.get(0));
        assertSame(sibling, item.getOneIntersectingObjectP(Item.class));
        assertTrue(item.isTouchingP(Item.class));
        assertFalse(item.isTouchingP(Ground.class));
        assertTrue(item.getObjectsAtP(4, 0, Item.class).contains(sibling));

        // The world-level query at that cell sees both, but not the window frame
        List<Actor> there = world.getObjectsAt(110, 110, null);
        assertTrue(there.contains(ground));
        assertTrue(there.contains(item));
        assertFalse(there.contains(win));
        assertTrue(world.getObjectsAt(110, 110, SuperWindow.class).contains(win));

        // Geometry itself is unfiltered
        assertTrue(item.intersectsP(ground));

        // Nothing in a closed window is found
        win.close();
        assertFalse(world.getObjectsAt(110, 110, null).contains(item));
        assertTrue(item.getIntersectingObjectsP(null).isEmpty());
    }

    // ---- position helpers ----

    public void testKeepOnScreenAndTopLeft()
    {
        World world = newWorld();
        SuperWindow win = new SuperWindow(50, 40, "Bag");
        world.addObject(win, -100, -100);
        assertEquals(0, win.getLeft());
        assertEquals(0, win.getTop());
        win.setLocation(1000, 1000);
        assertEquals(200 - win.getFrameWidth(), win.getLeft());
        assertEquals(200 - win.getFrameHeight(), win.getTop());

        win.setKeepOnScreen(false);
        win.setTopLeft(-20, -30);
        assertEquals(-20, win.getLeft());
        assertEquals(-30, win.getTop());
    }

    public void testWindowAtAndContains()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        assertSame(win, world.getWindowAt(110, 110));
        assertSame(win, world.getWindowAt(99, 90)); // on the title bar
        assertNull(world.getWindowAt(10, 10));
        assertTrue(win.containsWorldPosition(110, 110));
        assertFalse(win.containsWorldPosition(99, 90));
    }

    // ---- scrolling ----

    public void testScrollMovesContents()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        win.setContentSize(40, 100);
        win.setScrollable(true);
        Item item = new Item("item");
        win.addObject(item, 10, 50);
        assertEquals(50, item.getY());
        // Off the visible area: not painted
        BufferedImage img = render(world);
        assertTrue(argb(img, 110, 150) != RED);

        win.setScroll(0, 40);
        assertEquals(40, win.getScrollY());
        assertEquals(50, item.getY());
        assertEquals(110, win.toWorldY(50));
        Rect r = ActorVisitor.getBoundingRect(item);
        assertEquals(105, r.getY());
        img = render(world);
        assertEquals(RED, argb(img, 110, 110));

        // Clamped to the content
        win.setScroll(0, 1000);
        assertEquals(70, win.getScrollY());
        win.scrollBy(0, -1000);
        assertEquals(0, win.getScrollY());
    }

    // ---- engine-driven mouse behaviour ----

    private MousePollingManager mouse()
    {
        return WorldHandler.getInstance().getMouseManager();
    }

    /** Deliver the queued mouse events for a new act, then let the windows handle them. */
    private void act(World world)
    {
        render(world);
        mouse().newActStarted();
        WorldVisitor.processWindows(world, mouse());
    }

    public void testPressBringsWindowToFront()
    {
        World world = newWorld();
        SuperWindow w1 = new SuperWindow(60, 60, "One");
        SuperWindow w2 = new SuperWindow(60, 60, "Two");
        world.addObject(w1, 100, 100);
        world.addObject(w2, 120, 120);
        render(world);
        assertTrue(w2.isFrontWindow());

        // Press on a part of w1 not covered by w2
        mouse().mousePressed(75, 100, 1);
        act(world);
        assertTrue(w1.isFrontWindow());
        MouseInfo info = Greenfoot.getMouseInfo();
        assertSame(w1, info.getActor());
        assertSame(w1, info.getWindow());
    }

    public void testDragByTitleBarMovesWindowAndContents()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 80, 30);
        Item item = new Item("item");
        win.addObject(item, 10, 10);
        int titleX = 105;
        int titleY = win.getTop() + win.getBorderThickness() + 3;

        mouse().mousePressed(titleX, titleY, 1);
        act(world);
        assertFalse(win.isBeingDragged());
        mouse().mouseDragged(titleX + 10, titleY + 10, 1);
        act(world);
        assertTrue(win.isBeingDragged());
        assertEquals(110, win.toWorldX(0));
        assertEquals(110, win.toWorldY(0));
        assertEquals(10, item.getX());
        Rect r = ActorVisitor.getBoundingRect(item);
        assertEquals(115, r.getX());
        mouse().mouseReleased(titleX + 10, titleY + 10, 1);
        act(world);
        assertFalse(win.isBeingDragged());
        assertEquals(110, win.toWorldX(0));
    }

    public void testDragOnContentDoesNotMoveWindow()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        mouse().mousePressed(110, 110, 1);
        act(world);
        mouse().mouseDragged(130, 130, 1);
        act(world);
        assertFalse(win.isBeingDragged());
        assertEquals(100, win.toWorldX(0));
    }

    public void testLockedWindowDoesNotDrag()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 80, 30);
        win.setDraggable(false);
        int titleY = win.getTop() + win.getBorderThickness() + 3;
        mouse().mousePressed(105, titleY, 1);
        act(world);
        mouse().mouseDragged(125, titleY, 1);
        act(world);
        assertFalse(win.isBeingDragged());
        assertEquals(100, win.toWorldX(0));
    }

    public void testCloseAndMinimizeButtons()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 60, 30);
        int b = win.getBorderThickness();
        int size = win.getTitleBarHeight() - 8;
        int right = win.getLeft() + win.getFrameWidth() - b - 5;
        int closeX = right - size / 2;
        int minX = right - size - 5 - size / 2;
        int y = win.getTop() + b + win.getTitleBarHeight() / 2;

        mouse().mousePressed(minX, y, 1);
        act(world);
        mouse().mouseClicked(minX, y, 1, 1);
        act(world);
        assertTrue(win.isMinimized());
        assertTrue(win.isOpen());

        mouse().mousePressed(minX, y, 1);
        act(world);
        mouse().mouseClicked(minX, y, 1, 1);
        act(world);
        assertFalse(win.isMinimized());

        mouse().mousePressed(closeX, y, 1);
        act(world);
        mouse().mouseClicked(closeX, y, 1, 1);
        act(world);
        assertFalse(win.isOpen());
        // Still in the world, ready to reopen
        assertSame(world, win.getWorld());
    }

    public void testModalWindowSwallowsOutsideEvents()
    {
        World world = newWorld();
        Ground ground = new Ground();
        world.addObject(ground, 20, 20);
        SuperWindow win = placedWindow(world, 40, 30);
        win.setModal(true);
        render(world);

        mouse().mouseClicked(20, 20, 1, 1);
        act(world);
        assertFalse(Greenfoot.mouseClicked(ground));
        assertTrue(Greenfoot.mouseClicked(win));
        assertTrue(Greenfoot.mouseClicked(null));
        assertSame(win, Greenfoot.getMouseInfo().getWindow());

        win.close();
        render(world);
        mouse().mouseClicked(20, 20, 1, 1);
        act(world);
        assertTrue(Greenfoot.mouseClicked(ground));
        assertNull(Greenfoot.getMouseInfo().getWindow());
    }

    public void testMouseWheelScrollsWindow()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        win.setContentSize(40, 100);
        win.setScrollable(true);
        render(world);

        mouse().mouseScrolled(110, 110, 12);
        act(world);
        assertTrue(Greenfoot.mouseScrolled(null));
        assertTrue(Greenfoot.mouseScrolled(win));
        assertEquals(12, Greenfoot.getMouseInfo().getScrollAmount());
        assertEquals(12, win.getScrollY());

        // Several wheel events in one act add up
        mouse().mouseScrolled(110, 110, -5);
        mouse().mouseScrolled(110, 110, -3);
        act(world);
        assertEquals(-8, Greenfoot.getMouseInfo().getScrollAmount());
        assertEquals(4, win.getScrollY());

        // Not scrollable: the wheel is reported but ignored by the window
        win.setScrollable(false);
        mouse().mouseScrolled(110, 110, 10);
        act(world);
        assertTrue(Greenfoot.mouseScrolled(win));
        assertEquals(4, win.getScrollY());

        // Wheel away from the window does nothing to it
        win.setScrollable(true);
        mouse().mouseScrolled(10, 10, 10);
        act(world);
        assertFalse(Greenfoot.mouseScrolled(win));
        assertTrue(Greenfoot.mouseScrolled(null));
        assertEquals(4, win.getScrollY());

        // A quiet act reports no scrolling
        act(world);
        assertFalse(Greenfoot.mouseScrolled(null));
        assertEquals(0, Greenfoot.getMouseInfo().getScrollAmount());
    }

    public void testHooksAndMouseOver()
    {
        World world = newWorld();
        final List<String> log = new ArrayList<String>();
        SuperWindow win = new SuperWindow(40, 30, "Hooks") {
            @Override
            protected void opened() { log.add("opened"); }
            @Override
            protected void closed() { log.add("closed"); }
            @Override
            protected void closeButtonClicked() { log.add("closeButton"); super.closeButtonClicked(); }
        };
        world.addObject(win, 100, 100);
        assertTrue(log.isEmpty());
        win.close();
        win.open();
        assertEquals("[closed, opened]", log.toString());

        render(world);
        mouse().mouseMoved(100, 100);
        act(world);
        assertTrue(win.isMouseOver());
        mouse().mouseMoved(5, 5);
        act(world);
        assertFalse(win.isMouseOver());
    }

    // ---- audit fixes (0.2.0) ----

    public void testEarlierThumbPressDoesNotScrollOnLaterWorldDrag()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        win.setContentSize(40, 100);
        win.setScrollable(true);
        render(world);
        // The thumb sits at the top of the 8-pixel bar on the content's right edge.
        int thumbX = 136, thumbY = 105;

        mouse().mousePressed(thumbX, thumbY, 1);
        act(world);
        mouse().mouseDragged(thumbX, thumbY + 6, 1);
        act(world);
        int scrolled = win.getScrollY();
        assertTrue(scrolled > 0);
        mouse().mouseReleased(thumbX, thumbY + 6, 1);
        act(world);

        // A later press and drag on the bare world must leave the window alone.
        mouse().mousePressed(10, 10, 1);
        act(world);
        mouse().mouseDragged(10, 60, 1);
        act(world);
        assertEquals(scrolled, win.getScrollY());
        mouse().mouseReleased(10, 60, 1);
        act(world);
        assertEquals(scrolled, win.getScrollY());
    }

    public void testEarlierTitleBarPressDoesNotDragOnLaterWorldDrag()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 80, 30);
        int titleY = win.getTop() + win.getBorderThickness() + 3;
        // Press on the title bar and release without dragging.
        mouse().mousePressed(105, titleY, 1);
        act(world);
        mouse().mouseClicked(105, titleY, 1, 1);
        act(world);

        mouse().mousePressed(10, 10, 1);
        act(world);
        mouse().mouseDragged(10, 40, 1);
        act(world);
        assertFalse(win.isBeingDragged());
        assertEquals(100, win.toWorldX(0));
        assertEquals(100, win.toWorldY(0));
    }

    public void testGetObjectsAtSkipsContentsScrolledOutOfView()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        win.setContentSize(40, 100);
        Item item = new Item("item");
        win.addObject(item, 10, 50);     // world (110, 150): below the visible content
        assertFalse(world.getObjectsAt(110, 150, null).contains(item));
        assertFalse(world.getObjectsAt(110, 150, Item.class).contains(item));

        win.setScroll(0, 40);            // now at world (110, 110), inside the content
        assertTrue(world.getObjectsAt(110, 110, Item.class).contains(item));

        // Placed partly outside the content area: only the visible part counts.
        Item edge = new Item("edge");
        win.addObject(edge, -2, 45);     // world centre (98, 105): x 93..102
        assertFalse(world.getObjectsAt(98, 105, null).contains(edge));
        assertTrue(world.getObjectsAt(101, 105, null).contains(edge));
    }

    public void testGetObjectsAtSkipsContentsCoveredByAnotherWindow()
    {
        World world = newWorld();
        Ground ground = new Ground();
        world.addObject(ground, 110, 110);
        SuperWindow back = placedWindow(world, 40, 30);
        Item hidden = new Item("hidden");
        back.addObject(hidden, 10, 10);  // world (110, 110)
        SuperWindow front = new SuperWindow(40, 40, "Front");
        world.addObject(front, 115, 115);
        Item shown = new Item("shown");
        front.addObject(shown, front.toLocalX(110), front.toLocalY(110));
        front.bringToFront();

        List<Actor> there = world.getObjectsAt(110, 110, null);
        assertFalse(there.contains(hidden));
        assertTrue(there.contains(shown));
        assertTrue(there.contains(ground));

        back.bringToFront();
        there = world.getObjectsAt(110, 110, null);
        assertTrue(there.contains(hidden));
        assertFalse(there.contains(shown));
    }

    public void testSetImageNullReturnsToDrawnFrame()
    {
        World world = newWorld();
        SuperWindow win = placedWindow(world, 40, 30);
        int fw = win.getFrameWidth(), fh = win.getFrameHeight();
        win.setImage((GreenfootImage) null);
        assertNotNull(win.getImage());
        assertEquals(fw, win.getImage().getWidth());
        assertEquals(fh, win.getImage().getHeight());
        render(world);
        win.setLocation(win.getX() + 5, win.getY() + 5);
        win.setTopLeft(20, 20);
        assertEquals(20, win.getLeft());
        assertTrue(win.containsWorldPosition(30, 50));
    }

    /** A window with its own picture as the frame. */
    private static class SkinnedWindow extends SuperWindow
    {
        final GreenfootImage skin;

        SkinnedWindow()
        {
            super(40, 30, "Skin");
            skin = new GreenfootImage(getFrameWidth(), getFrameHeight());
            skin.setColor(Color.GREEN);
            skin.fill();
            setImage(skin);
        }
    }

    public void testSkinSurvivesBeingAddedToWorldAndRedraws()
    {
        World world = newWorld();
        SkinnedWindow win = new SkinnedWindow();
        world.addObject(win, 100, 100);
        assertSame(win.skin, win.getImage());
        win.setTitle("Renamed");
        win.setBorderColor(Color.BLACK);
        assertSame(win.skin, win.getImage());

        World other = newWorld();
        other.addObject(win, 50, 50);
        assertSame(win.skin, win.getImage());

        // setImage(null) goes back to the drawn frame, which redraws follow again
        win.setImage((GreenfootImage) null);
        assertNotSame(win.skin, win.getImage());
        GreenfootImage drawn = win.getImage();
        win.setTitle("Again");
        assertNotSame(drawn, win.getImage());

        // A frame drawn by the window itself is not a skin
        SuperWindow plain = new SuperWindow(40, 30, "Plain");
        GreenfootImage before = plain.getImage();
        world.addObject(plain, 100, 100);
        assertNotSame(before, plain.getImage());
    }

    public void testPrecisePositionsSurviveMoveBetweenWorlds()
    {
        World world1 = newWorld();
        SuperWindow win = new SuperWindow(80, 60, "Bag");
        world1.addObject(win, 100, 100);
        Item a = new Item("a");
        win.addObject(a, 5, 5);
        a.setLocation(5.3, 7.6);
        assertEquals(5.3, a.getPreciseX(), 1e-9);

        World world2 = newWorld();
        world2.addObject(win, 60, 60);
        assertEquals(5.3, a.getPreciseX(), 1e-9);
        assertEquals(7.6, a.getPreciseY(), 1e-9);
        assertEquals(5, a.getX());
        assertEquals(8, a.getY());

        // And after the window has been out of every world for a while
        world2.removeObject(win);
        world1.addObject(win, 100, 100);
        assertEquals(5.3, a.getPreciseX(), 1e-9);
        assertEquals(7.6, a.getPreciseY(), 1e-9);
    }

    public void testRequestedContentSizeIsRemembered()
    {
        SuperWindow win = new SuperWindow(40, 30, "Grow");
        assertEquals(40, win.requestedContentWidth);
        assertEquals(30, win.requestedContentHeight);
        win.setContentSize(20, 100);
        assertEquals(40, win.getContentWidth());
        assertEquals(100, win.getContentHeight());
        assertEquals(20, win.requestedContentWidth);
        assertEquals(100, win.requestedContentHeight);
    }
}
