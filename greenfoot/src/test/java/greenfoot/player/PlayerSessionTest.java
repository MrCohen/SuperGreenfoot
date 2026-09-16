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
package greenfoot.player;

import greenfoot.Actor;
import greenfoot.Color;
import greenfoot.Greenfoot;
import greenfoot.GreenfootImage;
import greenfoot.World;
import greenfoot.core.WorldHandler;
import greenfoot.sound.SoundMixer;
import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives the standalone player core headlessly: the world is constructed on
 * the simulation thread, acts run, frames are rendered, and input reaches
 * the scenario, all without a window or JavaFX.
 */
public class PlayerSessionTest extends TestCase
{
    /** A world for the test: counts acts, records keys, draws a red actor. */
    public static class TestWorld extends World
    {
        public static final AtomicInteger acts = new AtomicInteger();
        public static volatile String lastKey;
        public static volatile boolean constructedOnSimThread;

        public TestWorld()
        {
            super(40, 30, 1);
            constructedOnSimThread = Thread.currentThread().getName().contains("Simulation");
            Dot d = new Dot();
            addObject(d, 10, 10);
        }

        @Override
        public void act()
        {
            acts.incrementAndGet();
            String k = Greenfoot.getKey();
            if (k != null) {
                lastKey = k;
            }
            if (Greenfoot.isKeyDown("right")) {
                getObjects(Dot.class).get(0).move(1);
            }
        }
    }

    public static class Dot extends Actor
    {
        public Dot()
        {
            GreenfootImage img = new GreenfootImage(4, 4);
            img.setColor(Color.RED);
            img.fill();
            setImage(img);
        }
    }

    public void testHeadlessSessionRunsActsAndRendersFrames() throws Exception
    {
        SoundMixer.installForTesting(false);
        File saveDir = Files.createTempDirectory("sgf-player").toFile();
        Properties props = new Properties();
        props.setProperty("main.class", TestWorld.class.getName());
        props.setProperty("project.name", "Player test");
        props.setProperty("simulation.speed", "100");

        CountDownLatch frame = new CountDownLatch(1);
        PlayerSession session = PlayerSession.create(getClass().getClassLoader(), props, saveDir, frame::countDown);
        assertEquals("Player test", session.getTitle());
        TestWorld.acts.set(0);
        session.start();

        // World constructed on the simulation thread, then acts advance
        for (int i = 0; i < 20; i++) {
            session.act();
            Thread.sleep(20);
        }
        assertTrue("world should be constructed", WorldHandler.getInstance().getWorld() instanceof TestWorld);
        assertTrue("world constructed on simulation thread", TestWorld.constructedOnSimThread);
        assertTrue("acts ran: " + TestWorld.acts.get(), TestWorld.acts.get() >= 5);

        // A frame was rendered with the red actor in it
        assertTrue(frame.await(3, TimeUnit.SECONDS));
        BufferedImage img = session.getDelegate().takeFrame();
        assertNotNull(img);
        assertEquals(40, img.getWidth());
        assertEquals(30, img.getHeight());
        assertEquals(0xFFFF0000, img.getRGB(10, 10));
        session.getDelegate().recycle(img);

        // Keyboard input by name reaches the scenario
        WorldHandler.getInstance().getKeyboardManager().keyPressed("right");
        WorldHandler.getInstance().getKeyboardManager().keyReleased("right");
        for (int i = 0; i < 5; i++) {
            session.act();
            Thread.sleep(20);
        }
        assertEquals("right", TestWorld.lastKey);

        // Reset makes a new world
        int before = TestWorld.acts.get();
        session.reset();
        Thread.sleep(200);
        assertTrue(WorldHandler.getInstance().getWorld() instanceof TestWorld);
        session.act();
        Thread.sleep(50);
        assertTrue(TestWorld.acts.get() > before);

        session.shutdown();
    }
}
