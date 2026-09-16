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
}
