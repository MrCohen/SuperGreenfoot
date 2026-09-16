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
import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

/**
 * Tests for the SuperGreenfoot precise position and rotation API on Actor.
 */
public class PrecisionTest extends TestCase
{
    private static final double EPS = 1e-9;

    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        Simulation.initialize();
    }

    /** An actor that counts calls to the int setLocation, to check override behaviour. */
    private static class CountingActor extends TestObject
    {
        int intSetLocationCalls = 0;
        int addedToWorldCalls = 0;

        CountingActor()
        {
            super(4, 4);
        }

        @Override
        public void setLocation(int x, int y)
        {
            intSetLocationCalls++;
            super.setLocation(x, y);
        }

        @Override
        protected void addedToWorld(World world)
        {
            addedToWorldCalls++;
        }
    }

    public void testPreciseLocationRoundsToCell()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 0, 0);

        o.setLocation(2.6, 3.4);
        assertEquals(2.6, o.getPreciseX(), EPS);
        assertEquals(3.4, o.getPreciseY(), EPS);
        assertEquals(3, o.getX());
        assertEquals(3, o.getY());

        // Exactly half rounds away from zero
        o.setLocation(2.5, 3.5);
        assertEquals(3, o.getX());
        assertEquals(4, o.getY());
    }

    public void testIntApiSnapsPreciseLocation()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 5, 5);
        assertEquals(5.0, o.getPreciseX(), EPS);

        o.setLocation(2.6, 3.4);
        o.setLocation(7, 8);
        assertEquals(7.0, o.getPreciseX(), EPS);
        assertEquals(8.0, o.getPreciseY(), EPS);

        // move(int) keeps the upstream integer behaviour, including snapping
        o.setLocation(2.6, 3.4);
        o.setRotation(0);
        o.move(1);
        assertEquals(4, o.getX());
        assertEquals(4.0, o.getPreciseX(), EPS);
    }

    public void testFractionalMoveAccumulates()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 10, 10);
        o.setRotation(0);
        for (int i = 0; i < 10; i++) {
            o.move(0.3);
        }
        assertEquals(13.0, o.getPreciseX(), 1e-6);
        assertEquals(13, o.getX());
        assertEquals(10, o.getY());

        // Moving along a precise angle
        o.setLocation(10.0, 10.0);
        o.setRotation(90.0);
        o.move(2.5);
        assertEquals(10.0, o.getPreciseX(), 1e-6);
        assertEquals(12.5, o.getPreciseY(), 1e-6);
        assertEquals(13, o.getY());
    }

    public void testBoundedWorldClampsPreciseLocation()
    {
        World world = WorldCreator.createWorld(10, 10, 10);
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 2, 2);

        o.setLocation(-3.5, 20.0);
        assertEquals(0.0, o.getPreciseX(), EPS);
        assertEquals(9.0, o.getPreciseY(), EPS);
        assertEquals(0, o.getX());
        assertEquals(9, o.getY());

        // Within range but fractional near the edge stays inside
        o.setLocation(8.9, 0.2);
        assertEquals(9, o.getX());
        assertEquals(0, o.getY());
    }

    public void testUnboundedWorldNegativeRounding()
    {
        World world = new World(10, 10, 1, false) {};
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 0, 0);
        o.setLocation(-2.5, -0.4);
        assertEquals(-3, o.getX());
        assertEquals(0, o.getY());
        o.setLocation(-2.4, -0.6);
        assertEquals(-2, o.getX());
        assertEquals(-1, o.getY());
    }

    public void testPreciseRotationNormalisationAndRounding()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 0, 0);

        o.setRotation(370.5);
        assertEquals(10.5, o.getPreciseRotation(), EPS);
        assertEquals(11, o.getRotation());

        o.turn(-0.5);
        assertEquals(10.0, o.getPreciseRotation(), EPS);
        assertEquals(10, o.getRotation());

        o.setRotation(359.7);
        assertEquals(0, o.getRotation());
        assertEquals(359.7, o.getPreciseRotation(), EPS);

        o.setRotation(-90.25);
        assertEquals(269.75, o.getPreciseRotation(), EPS);
        assertEquals(270, o.getRotation());

        // int API snaps the precise rotation
        o.setRotation(45);
        assertEquals(45.0, o.getPreciseRotation(), EPS);
        o.turn(1);
        assertEquals(46.0, o.getPreciseRotation(), EPS);
    }

    public void testSmallTurnsAccumulate()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject o = new TestObject(4, 4);
        world.addObject(o, 0, 0);
        o.setRotation(0);
        for (int i = 0; i < 100; i++) {
            o.turn(0.25);
        }
        assertEquals(25.0, o.getPreciseRotation(), 1e-6);
        assertEquals(25, o.getRotation());
    }

    public void testTurnTowardsAndDistance()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject a = new TestObject(4, 4);
        TestObject b = new TestObject(4, 4);
        world.addObject(a, 10, 10);
        world.addObject(b, 10, 10);
        b.setLocation(13.0, 14.0);

        assertEquals(5.0, a.distanceTo(b), EPS);
        assertEquals(5.0, b.distanceTo(a), EPS);

        a.turnTowards(b);
        assertEquals(Math.toDegrees(Math.atan2(4, 3)), a.getPreciseRotation(), 1e-9);
        assertEquals(53, a.getRotation());

        a.turnTowards(10.0, 0.0);
        assertEquals(270.0, a.getPreciseRotation(), EPS);
    }

    public void testImageRotationLock()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        TestObject o = new TestObject(20, 10);
        world.addObject(o, 50, 50);

        o.setRotation(90);
        Rect r = o.getBoundingRect();
        assertEquals(10, r.getWidth());
        assertEquals(20, r.getHeight());

        // Lock the image upright while the movement rotation stays at 90
        o.setImageRotation(0);
        assertTrue(o.isImageRotationLocked());
        assertEquals(90, o.getRotation());
        assertEquals(0, o.getImageRotation());
        r = o.getBoundingRect();
        assertEquals(20, r.getWidth());
        assertEquals(10, r.getHeight());

        // Turning the actor does not turn the locked image
        o.turn(45);
        assertEquals(135, o.getRotation());
        assertEquals(0, o.getImageRotation());
        assertEquals(0.0, o.getPreciseImageRotation(), EPS);

        // move() still follows the movement rotation (135 degrees: up-left)
        o.setLocation(50.0, 50.0);
        o.move(10.0);
        assertTrue(o.getPreciseX() < 50);
        assertTrue(o.getPreciseY() > 50);

        // Unlocking snaps the image back to the actor's rotation
        o.setImageRotationLocked(false);
        assertEquals(135, o.getImageRotation());
        assertEquals(135.0, o.getPreciseImageRotation(), EPS);
    }

    public void testSubclassIntOverrideStillCalledForIntApi()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        CountingActor o = new CountingActor();
        world.addObject(o, 1, 1);
        int afterAdd = o.intSetLocationCalls;
        assertTrue(afterAdd >= 1); // addToWorld calls setLocation, as upstream does

        o.setLocation(5, 5);
        assertEquals(afterAdd + 1, o.intSetLocationCalls);

        // The precise API is a separate path (documented) and does not go via the int override
        o.setLocation(5.5, 5.5);
        assertEquals(afterAdd + 1, o.intSetLocationCalls);
        assertEquals(6, o.getX());
    }

    public void testZChangeDoesNotReAddToWorld()
    {
        World world = WorldCreator.createWorld(100, 100, 1);
        CountingActor o = new CountingActor();
        world.addObject(o, 1, 1);
        assertEquals(1, o.addedToWorldCalls);
        o.setZ(5);
        o.setZ(-5);
        assertEquals(1, o.addedToWorldCalls);
        assertEquals(-5.0, o.getZ(), EPS);
        assertSame(world, o.getWorld());
    }

    public void testNeutralRound()
    {
        assertEquals(3, Actor.neutralRound(2.5));
        assertEquals(-3, Actor.neutralRound(-2.5));
        assertEquals(2, Actor.neutralRound(2.4));
        assertEquals(-2, Actor.neutralRound(-2.4));
        assertEquals(0, Actor.neutralRound(0.0));
        assertEquals(0, Actor.neutralRound(-0.4));
        assertEquals(-1, Actor.neutralRound(-0.5));
    }
}
