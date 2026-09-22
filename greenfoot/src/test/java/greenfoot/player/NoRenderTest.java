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
import greenfoot.GreenfootImage;
import greenfoot.TestUtilDelegate;
import greenfoot.World;
import greenfoot.sound.SoundMixer;
import greenfoot.util.GreenfootUtil;

import junit.framework.TestCase;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A run nobody is watching should not draw anything. Checks that switching
 * rendering off stops frames being produced, that switching it on again starts
 * them, and reports what the drawing was costing.
 */
public class NoRenderTest extends TestCase
{
    /** A world with enough actors that rendering it is worth measuring. */
    public static class CrowdedWorld extends World
    {
        public static final AtomicInteger acts = new AtomicInteger();
        /** How many actors the world holds, counted on its first act. */
        public static final AtomicInteger population = new AtomicInteger();

        public CrowdedWorld()
        {
            super(800, 600, 1);
            GreenfootImage image = new GreenfootImage(40, 40);
            image.setColor(greenfoot.Color.RED);
            image.fill();
            for (int i = 0; i < 300; i++) {
                addObject(new Blob(image), (i * 37) % 800, (i * 53) % 600);
            }
        }

        @Override
        public void act()
        {
            acts.incrementAndGet();
            if (population.get() == 0) {
                population.set(numberOfObjects());
            }
        }
    }

    public static class Blob extends Actor
    {
        public static final AtomicInteger acts = new AtomicInteger();

        public Blob(GreenfootImage image)
        {
            setImage(image);
        }

        @Override
        public void act()
        {
            acts.incrementAndGet();
            setLocation((getX() + 1) % getWorld().getWidth(), getY());
        }
    }

    private PlayerSession session;

    @Override
    protected void setUp() throws Exception
    {
        // Actor's static initialiser loads the default image through whatever
        // GreenfootUtil delegate is installed at the time, once per JVM. The
        // player delegate looks for greenfoot.png at the root of the classpath,
        // which is how the runtime jar ships it but not how the build tree lays
        // it out. So install the test delegate and load Actor now, before the
        // session swaps the delegate over. Without this the test fails when it
        // happens to run first and passes when another test got there earlier.
        GreenfootUtil.initialise(new TestUtilDelegate());
        Class.forName(Actor.class.getName(), true, getClass().getClassLoader());
        SoundMixer.installForTesting(false);
    }

    @Override
    protected void tearDown()
    {
        if (session != null) {
            session.shutdown();
            session = null;
        }
    }

    public void testRenderingOffProducesNoFrames() throws Exception
    {
        File saveDir = Files.createTempDirectory("sgf-norender").toFile();
        Properties props = new Properties();
        props.setProperty("main.class", CrowdedWorld.class.getName());
        props.setProperty("project.name", "No-render test");
        props.setProperty("simulation.speed", "100"); // as fast as it will go

        AtomicInteger framesAnnounced = new AtomicInteger();
        session = PlayerSession.create(getClass().getClassLoader(), props, saveDir,
                framesAnnounced::incrementAndGet);
        session.start();
        Thread.sleep(300); // world construction

        assertTrue("rendering is on by default", session.isRenderingEnabled());

        // With rendering on: frames are produced, and one is waiting to be shown.
        int actsOn = runFor(1000);
        assertEquals("the world should be populated, or this measures nothing",
                300, CrowdedWorld.population.get());
        assertTrue("the actors should be acting: " + Blob.acts.get() + " actor acts over "
                + actsOn + " world acts", Blob.acts.get() >= 300L * actsOn / 2);
        assertTrue("frames should be announced while rendering is on",
                framesAnnounced.get() > 0);
        assertNotNull("a rendered frame should be waiting",
                session.getDelegate().takeFrame());

        // With rendering off: nothing is drawn at all.
        session.setRenderingEnabled(false);
        assertFalse(session.isRenderingEnabled());
        session.getDelegate().takeFrame(); // discard anything left over
        framesAnnounced.set(0);
        int actsOff = runFor(1000);
        assertEquals("no frame should be announced while rendering is off",
                0, framesAnnounced.get());
        assertNull("no frame should have been drawn while rendering is off",
                session.getDelegate().takeFrame());

        // And on again, so this is a switch and not a one-way door.
        session.setRenderingEnabled(true);
        framesAnnounced.set(0);
        runFor(500);
        assertTrue("frames should resume when rendering is switched back on",
                framesAnnounced.get() > 0);

        // Unpaced, acts run far faster than the 120-frames-a-second cap, so only
        // a small share of them pays for a frame. This understates what the
        // switch saves a paced run, where a repaint follows nearly every act.
        System.out.println(String.format(
            "NoRenderTest: %d actors, unpaced, 1 s each way: %d acts with rendering, "
            + "%d without (%+.1f%%)",
            CrowdedWorld.population.get(), actsOn, actsOff,
            100.0 * (actsOff - actsOn) / actsOn));
    }

    /** Run for the given milliseconds and return how many acts happened. */
    private int runFor(int millis) throws InterruptedException
    {
        CrowdedWorld.acts.set(0);
        session.run();
        Thread.sleep(millis);
        session.pause();
        Thread.sleep(100);
        return CrowdedWorld.acts.get();
    }
}
