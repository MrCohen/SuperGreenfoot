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

import greenfoot.core.Simulation;
import greenfoot.gui.WorldRenderer;
import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for SuperGreenfoot per-actor z ordering, y-sorting and their interaction
 * with class paint order and act order.
 */
public class ZOrderTest extends TestCase
{
    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        Simulation.initialize();
    }

    /** Two distinct actor classes so class paint order can be tested. */
    private static class Alpha extends TestObject
    {
        final String name;
        Alpha(String name) { super(4, 4); this.name = name; }
        @Override public String toString() { return name; }
    }

    private static class Beta extends TestObject
    {
        final String name;
        Beta(String name) { super(4, 4); this.name = name; }
        @Override public String toString() { return name; }
    }

    private static List<String> names(Iterable<Actor> actors)
    {
        List<String> out = new ArrayList<String>();
        for (Actor a : actors) {
            out.add(a.toString());
        }
        return out;
    }

    private static String join(Iterable<Actor> actors)
    {
        return String.join(",", names(actors));
    }

    /** Paint the world and return actor names in the order they were painted. */
    private static String paintedOrder(World world, Actor... actors)
    {
        BufferedImage img = new BufferedImage(world.getWidthInPixels(), world.getHeightInPixels(),
                BufferedImage.TYPE_INT_ARGB);
        new WorldRenderer().renderWorld(world, img);
        List<Actor> sorted = new ArrayList<Actor>();
        for (Actor a : actors) {
            sorted.add(a);
        }
        sorted.sort((p, q) -> Integer.compare(ActorVisitor.getLastPaintSeqNum(p), ActorVisitor.getLastPaintSeqNum(q)));
        return join(sorted);
    }

    /** An actor whose image size can be chosen, for testing the sort anchor. */
    private static class Prop extends Actor
    {
        final String name;

        Prop(String name, int width, int height)
        {
            this.name = name;
            setImage(new GreenfootImage(width, height));
        }

        void resize(int width, int height)
        {
            setImage(new GreenfootImage(width, height));
        }

        @Override public String toString() { return name; }
    }

    public void testDefaultIsInsertionOrder()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a = new Alpha("a"), b = new Alpha("b"), c = new Alpha("c");
        world.addObject(a, 1, 1);
        world.addObject(b, 2, 2);
        world.addObject(c, 3, 3);
        assertEquals("a,b,c", join(world.getObjectsInFinalPaintOrder()));
        assertFalse(world.isPaintSortNeeded());
        assertEquals("a,b,c", paintedOrder(world, a, b, c));
    }

    public void testZOrdersWithinClassAndTiesKeepInsertion()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a = new Alpha("a"), b = new Alpha("b"), c = new Alpha("c"), d = new Alpha("d");
        world.addObject(a, 1, 1);
        world.addObject(b, 2, 2);
        world.addObject(c, 3, 3);
        world.addObject(d, 4, 4);
        a.setZ(10);
        c.setZ(-1);
        // z: a=10, b=0, c=-1, d=0  ->  c, b, d, a
        assertEquals("c,b,d,a", join(world.getObjectsInFinalPaintOrder()));
        assertEquals("c,b,d,a", paintedOrder(world, a, b, c, d));

        // Changing z later re-sorts on the next paint
        a.setZ(0);
        assertEquals("c,a,b,d", join(world.getObjectsInFinalPaintOrder()));
    }

    public void testZSetBeforeAddingIsHonoured()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a = new Alpha("a"), b = new Alpha("b");
        b.setZ(-3);
        world.addObject(a, 1, 1);
        world.addObject(b, 2, 2);
        assertEquals("b,a", join(world.getObjectsInFinalPaintOrder()));
    }

    public void testClassPaintOrderTakesPrecedenceOverZ()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a1 = new Alpha("a1"), a2 = new Alpha("a2");
        Beta b1 = new Beta("b1"), b2 = new Beta("b2");
        world.addObject(b1, 1, 1);
        world.addObject(a1, 2, 2);
        world.addObject(b2, 3, 3);
        world.addObject(a2, 4, 4);
        // Alpha painted on top of Beta
        world.setPaintOrder(Alpha.class, Beta.class);
        assertEquals("b1,b2,a1,a2", join(world.getObjectsInFinalPaintOrder()));

        b2.setZ(100);   // still below every Alpha
        a1.setZ(5);     // above a2
        assertEquals("b1,b2,a2,a1", join(world.getObjectsInFinalPaintOrder()));

        // Global z order ignores class order entirely
        world.setGlobalZOrder(true);
        assertEquals("b1,a2,a1,b2", join(world.getObjectsInFinalPaintOrder()));
        world.setGlobalZOrder(false);
        assertEquals("b1,b2,a2,a1", join(world.getObjectsInFinalPaintOrder()));
    }

    public void testZSortByY()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a = new Alpha("a"), b = new Alpha("b"), c = new Alpha("c");
        world.addObject(a, 1, 30);
        world.addObject(b, 2, 10);
        world.addObject(c, 3, 20);
        world.setZSortByY(true);
        assertEquals("b,c,a", join(world.getObjectsInFinalPaintOrder()));

        // Precise y is used, and z breaks ties
        c.setLocation(3.0, 10.0);
        c.setZ(-1);
        assertEquals("c,b,a", join(world.getObjectsInFinalPaintOrder()));
        c.setZ(1);
        assertEquals("b,c,a", join(world.getObjectsInFinalPaintOrder()));

        world.setZSortByY(false);
        // z still in use (c has z=1): a=0,b=0,c=1
        assertEquals("a,b,c", join(world.getObjectsInFinalPaintOrder()));
    }

    public void testZDoesNotAffectActOrder()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a = new Alpha("a"), b = new Alpha("b"), c = new Alpha("c");
        world.addObject(a, 1, 1);
        world.addObject(b, 2, 2);
        world.addObject(c, 3, 3);
        a.setZ(50);
        assertEquals("b,c,a", join(world.getObjectsInFinalPaintOrder()));
        assertEquals("a,b,c", join(WorldVisitor.getObjectsListInActOrder(world)));

        world.setActOrder(Beta.class, Alpha.class);
        world.setPaintOrder(Alpha.class, Beta.class);
        b.setZ(-1);
        assertEquals("b,c,a", join(world.getObjectsInFinalPaintOrder()));
        assertEquals("a,b,c", join(WorldVisitor.getObjectsListInActOrder(world)));
    }

    public void testRemovedActorLeavesPaintOrder()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Alpha a = new Alpha("a"), b = new Alpha("b");
        world.addObject(a, 1, 1);
        world.addObject(b, 2, 2);
        a.setZ(1);
        assertEquals("b,a", join(world.getObjectsInFinalPaintOrder()));
        world.removeObject(a);
        assertEquals("b", join(world.getObjectsInFinalPaintOrder()));
    }

    // ----------------------------------------------------------------------------
    // SuperGreenfoot: the sort anchor. Greenfoot draws an actor centred on its
    // position, so y-sorting by that position only looks right if a tall picture is
    // padded with empty rows until its feet reach the middle. ZSortAnchor.BOTTOM and
    // Actor.setZSortOffset let the picture be exactly as tall as what it draws.
    // ----------------------------------------------------------------------------

    /** With a bottom anchor a tall thing stands where its feet are, not where its middle is. */
    public void testBottomAnchorSortsByTheFootOfTheImage()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop tree = new Prop("tree", 20, 40);
        Prop rock = new Prop("rock", 8, 8);
        world.addObject(tree, 10, 20);
        world.addObject(rock, 30, 25);
        world.setZSortByY(true);

        // By the middle of the image: the tree is higher up, so it paints behind.
        assertEquals(ZSortAnchor.CENTER, world.getZSortAnchor());
        assertEquals("tree,rock", join(world.getObjectsInFinalPaintOrder()));

        // By the foot of the image: the tree's trunk (20 + 40/2 = 40) is below the
        // rock's base (25 + 8/2 = 29), so the tree paints in front.
        world.setZSortAnchor(ZSortAnchor.BOTTOM);
        assertEquals("rock,tree", join(world.getObjectsInFinalPaintOrder()));
        assertEquals("rock,tree", paintedOrder(world, tree, rock));
    }

    /** The height is read at every sort, so changing the image is enough on its own. */
    public void testBottomAnchorFollowsAChangedImage()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop tree = new Prop("tree", 20, 40);
        Prop rock = new Prop("rock", 8, 8);
        world.addObject(tree, 10, 20);
        world.addObject(rock, 30, 25);
        world.setZSortByY(true);
        world.setZSortAnchor(ZSortAnchor.BOTTOM);
        assertEquals("rock,tree", join(world.getObjectsInFinalPaintOrder()));

        // The tree is felled and becomes a stump: 20 + 10/2 = 25, now behind the rock.
        tree.resize(20, 10);
        assertEquals("tree,rock", join(world.getObjectsInFinalPaintOrder()));
    }

    /** An offset moves the ground line when the feet are not at the picture's edge. */
    public void testSortOffsetShiftsTheGroundLine()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop tree = new Prop("tree", 20, 40);
        Prop rock = new Prop("rock", 8, 8);
        world.addObject(tree, 10, 20);
        world.addObject(rock, 30, 25);
        world.setZSortByY(true);
        world.setZSortAnchor(ZSortAnchor.BOTTOM);
        assertEquals("rock,tree", join(world.getObjectsInFinalPaintOrder()));

        // The trunk really ends 16 px above the picture's edge: 20 + 20 - 16 = 24.
        tree.setZSortOffset(-16);
        assertEquals(-16.0, tree.getZSortOffset(), 0.0);
        assertEquals("tree,rock", join(world.getObjectsInFinalPaintOrder()));
    }

    /** An offset works on its own, without changing the world's anchor. */
    public void testSortOffsetWorksWithTheCentreAnchor()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop tree = new Prop("tree", 20, 40);
        Prop rock = new Prop("rock", 8, 8);
        world.addObject(tree, 10, 20);
        world.addObject(rock, 30, 25);
        world.setZSortByY(true);
        assertEquals("tree,rock", join(world.getObjectsInFinalPaintOrder()));

        tree.setZSortOffset(20);   // 20 + 20 = 40, below the rock's 25
        assertEquals("rock,tree", join(world.getObjectsInFinalPaintOrder()));
    }

    /** An offset set before the actor joins the world still counts, as z does. */
    public void testSortOffsetSetBeforeAddingIsHonoured()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop a = new Prop("a", 8, 8);
        Prop b = new Prop("b", 8, 8);
        b.setZSortOffset(-20);
        world.addObject(a, 10, 20);
        world.addObject(b, 30, 25);
        world.setZSortByY(true);
        // b: 25 - 20 = 5, above a's 20.
        assertEquals("b,a", join(world.getObjectsInFinalPaintOrder()));
    }

    /** An offset that is not a number would make the sort inconsistent, so it is refused. */
    public void testZSortOffsetRejectsNaNAndInfinity()
    {
        Prop a = new Prop("a", 8, 8);
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        {
            try
            {
                a.setZSortOffset(bad);
                fail("accepted " + bad);
            }
            catch (IllegalArgumentException e)
            {
                // expected
            }
        }
        assertEquals(0.0, a.getZSortOffset(), 0.0);
    }

    /** Image measurements are pixels, so they mean the same thing at any cell size. */
    public void testAnchorAndOffsetAreInPixelsAtAnyCellSize()
    {
        World world = WorldCreator.createWorld(50, 50, 10);
        Prop tall = new Prop("tall", 20, 40);
        Prop flat = new Prop("flat", 8, 8);
        world.addObject(tall, 1, 2);
        world.addObject(flat, 3, 3);
        world.setZSortByY(true);

        // By cell: row 2 is above row 3.
        assertEquals("tall,flat", join(world.getObjectsInFinalPaintOrder()));

        // By the foot, in pixels: tall is 2*10 + 20 = 40, flat is 3*10 + 4 = 34.
        world.setZSortAnchor(ZSortAnchor.BOTTOM);
        assertEquals("flat,tall", join(world.getObjectsInFinalPaintOrder()));
    }

    /** The anchor is about painting only: it never moves an actor. */
    public void testAnchorAndOffsetDoNotMoveTheActor()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop tree = new Prop("tree", 20, 40);
        world.addObject(tree, 10, 20);
        world.setZSortByY(true);
        world.setZSortAnchor(ZSortAnchor.BOTTOM);
        tree.setZSortOffset(-16);

        assertEquals(10, tree.getX());
        assertEquals(20, tree.getY());
        assertEquals(20.0, tree.getPreciseY(), 0.0);
        assertSame(tree, world.getObjectsAt(10, 20, Prop.class).get(0));
    }

    /** Without y-sorting the anchor does nothing at all. */
    public void testAnchorDoesNothingWithoutYSorting()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop tree = new Prop("tree", 20, 40);
        Prop rock = new Prop("rock", 8, 8);
        world.addObject(tree, 10, 20);
        world.addObject(rock, 30, 25);
        world.setZSortAnchor(ZSortAnchor.BOTTOM);
        tree.setZSortOffset(-16);

        assertEquals("tree,rock", join(world.getObjectsInFinalPaintOrder()));
    }

    /** An actor with no image at all sorts by its position, and does not blow up. */
    public void testBottomAnchorWithNoImage()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        Prop ghost = new Prop("ghost", 8, 8);
        Prop rock = new Prop("rock", 8, 8);
        ghost.setImage((GreenfootImage) null);
        world.addObject(ghost, 10, 30);
        world.addObject(rock, 30, 25);
        world.setZSortByY(true);
        world.setZSortAnchor(ZSortAnchor.BOTTOM);

        // ghost: 30 + nothing = 30, rock: 25 + 4 = 29.
        assertEquals("rock,ghost", join(world.getObjectsInFinalPaintOrder()));
    }

    /** There is no "no anchor": asking for null is a mistake worth saying out loud. */
    public void testNullAnchorRejected()
    {
        World world = WorldCreator.createWorld(50, 50, 1);
        try
        {
            world.setZSortAnchor(null);
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected)
        {
            assertEquals(ZSortAnchor.CENTER, world.getZSortAnchor());
        }
    }
}
