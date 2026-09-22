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

import greenfoot.World;
import greenfoot.core.Simulation;
import greenfoot.sound.SoundMixer;
import junit.framework.TestCase;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures the act rate of an empty world at the default speed. Greenfoot
 * users expect speed 50 to be 60 acts a second; this checks that the
 * simulation thread's pacing actually delivers that, headless, on the
 * machine running the tests.
 */
public class SimulationPacingTest extends TestCase
{
    /** An empty world that records the time of every act. */
    public static class EmptyWorld extends World
    {
        public static final AtomicInteger acts = new AtomicInteger();
        public static final long[] actTimes = new long[100_000];

        public EmptyWorld()
        {
            super(40, 30, 1);
        }

        @Override
        public void act()
        {
            int n = acts.getAndIncrement();
            if (n < actTimes.length) {
                actTimes[n] = System.nanoTime();
            }
        }
    }

    public void testSpeed50IsSixtyActsPerSecond() throws Exception
    {
        SoundMixer.installForTesting(false);
        File saveDir = Files.createTempDirectory("sgf-pacing").toFile();
        Properties props = new Properties();
        props.setProperty("main.class", EmptyWorld.class.getName());
        props.setProperty("project.name", "Pacing test");
        props.setProperty("simulation.speed", "50");

        PlayerSession session = PlayerSession.create(getClass().getClassLoader(), props, saveDir, () -> {});
        EmptyWorld.acts.set(0);
        session.start();
        Thread.sleep(300); // world construction
        assertEquals(50, session.getSpeed());

        // Warm up, then measure a window of acts.
        session.run();
        Thread.sleep(1000);
        int from = EmptyWorld.acts.get();
        long t0 = System.nanoTime();
        Thread.sleep(4000);
        int to = EmptyWorld.acts.get();
        long t1 = System.nanoTime();
        session.pause();
        Thread.sleep(100);

        int ran = to - from;
        double secs = (t1 - t0) / 1e9;
        double rate = ran / secs;

        // Per-act intervals inside the window, for the jitter report.
        long[] gaps = new long[Math.max(0, ran - 1)];
        for (int i = 0; i < gaps.length; i++) {
            gaps[i] = EmptyWorld.actTimes[from + i + 1] - EmptyWorld.actTimes[from + i];
        }
        Arrays.sort(gaps);
        String report = String.format(
            "speed 50: %d acts in %.3f s = %.2f acts/s; act interval median %.3f ms, p99 %.3f ms, max %.3f ms",
            ran, secs, rate,
            gaps.length > 0 ? gaps[gaps.length / 2] / 1e6 : 0,
            gaps.length > 0 ? gaps[gaps.length * 99 / 100] / 1e6 : 0,
            gaps.length > 0 ? gaps[gaps.length - 1] / 1e6 : 0);
        System.out.println("SimulationPacingTest: " + report);

        session.shutdown();
        assertTrue(report, rate > 59.0 && rate < 61.0);
    }
}
