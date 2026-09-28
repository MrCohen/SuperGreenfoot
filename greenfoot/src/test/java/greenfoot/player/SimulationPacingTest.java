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

    @Override
    protected void setUp() throws Exception
    {
        // The engine's default actor image is loaded once, by whichever delegate is installed when
        // Actor first loads. Load it through the test delegate (as the IDE would) before a headless
        // session installs the player delegate, whose logo resource is not on the test classpath.
        greenfoot.util.GreenfootUtil.initialise(new greenfoot.TestUtilDelegate());
        Class.forName("greenfoot.Actor", true, getClass().getClassLoader());
    }

    /**
     * How fine the OS timer is for this process: the median of fifty
     * {@code Thread.sleep(1)} calls, in milliseconds. About 1 ms on macOS, Linux and
     * a Windows desktop; about 16 ms when Windows hands the process its coarse
     * 15.6 ms tick.
     */
    static double sleepGranularityMs() throws InterruptedException
    {
        long[] d = new long[50];
        for (int i = 0; i < d.length; i++) {
            long t = System.nanoTime();
            Thread.sleep(1);
            d[i] = System.nanoTime() - t;
        }
        Arrays.sort(d);
        return d[d.length / 2] / 1e6;
    }

    public void testSpeed50IsSixtyActsPerSecond() throws Exception
    {
        // Measured before the session starts, so it cannot disturb the pacing.
        double granularity = sleepGranularityMs();
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
        System.out.printf("SimulationPacingTest: Thread.sleep(1) median %.2f ms%n", granularity);

        // The rate is exact by construction: acts are scheduled against absolute
        // deadlines, so a late wake-up is made up by the next act.
        assertTrue(report, rate > 55.0 && rate < 65.0);

        // The median interval is the pacing itself (16.667 ms); a busy machine can
        // stall a few acts, which moves the overall rate but hardly the median. The
        // old pacing gave about 18.8 ms, and speed 51 gives about 14.7 ms.
        //
        // That only holds when the OS timer is fine. Under Windows' coarse 15.6 ms
        // tick every gap is quantised to the tick (median about 16.0 ms, p99 about
        // 31 ms), which says nothing about the pacer. Seen 2026-09-28 in about one
        // run in three for Gradle's test worker on a Windows 11 laptop, which
        // power-throttles windowless background processes and then ignores their
        // timer-resolution requests; the same test under plain java gave a
        // 16.667 ms median every time, as did the IDE and the exported player.
        // Widening the window instead would let upstream's original 16.24 ms bug
        // through, so the check is skipped only when the timer is coarse.
        if (granularity >= 10.0) {
            System.out.println("SimulationPacingTest: coarse OS timer, median check skipped: " + report);
            return;
        }
        double median = gaps.length > 0 ? gaps[gaps.length / 2] / 1e6 : 0;
        assertTrue(report, median > 16.2 && median < 17.2);
    }
}
