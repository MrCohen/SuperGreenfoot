/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2012,2018  Poul Henriksen and Michael Kolling 
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
package greenfoot.util;

/**
 * Timer to do high precision sleeps.
 *
 * <p>SuperGreenfoot: rewritten to sleep until a deadline without overshooting
 * it. The original measured the cost of a 1 ns {@code Thread.sleep} and then
 * trusted one long sleep for almost the whole delay. On macOS a timed sleep
 * of 16 ms wakes up about 4 ms late (the kernel coalesces timers, and the
 * overshoot grows with the length of the sleep), and on Windows the timer
 * ticks in 1 ms steps, so a 60 acts-a-second scenario ran at 52-53 on a Mac
 * and 61 on a PC. This version sleeps in shrinking steps, always asking for
 * less than the time left, and spins for the last fraction of a millisecond.
 * The spin costs up to one millisecond of one core per act, so at most about
 * 6% of a core at 60 acts a second, usually less.
 * 
 * @author Poul Henriksen
 */
public class HDTimer
{
    /** Below this much remaining time we spin instead of sleeping. */
    private static final long SPIN_TAIL_NANOS = 1_000_000L;

    /** Fraction of the remaining time to ask the OS for in each step. */
    private static final double SLEEP_FRACTION = 0.7;

    /**
     * Sleep for the specified amount of time.
     * 
     * @param nanos
     *            Time to wait in nanoseconds.
     * @throws InterruptedException
     *             if another thread has interrupted the current thread
     */
    public static void sleep(long nanos)
        throws InterruptedException
    {
        sleepUntil(System.nanoTime() + nanos);
    }

    /**
     * Sleep until {@code System.nanoTime()} reaches the given deadline. Returns
     * at once if the deadline has already passed.
     *
     * <p>Keep this on {@code Thread.sleep}. Measured on Windows 11 with JDK 21
     * (2026-09-28, a Surface Pro 7): with this method the act interval at speed 50
     * has a median of exactly 16.667 ms in the IDE, in the exported player and in
     * a plain JVM, with a p99 under 20 ms and 1-5% of a core. Two things that look
     * like improvements make it worse there:
     * <ul>
     * <li>{@code LockSupport.parkNanos} wakes on the 15.6 ms system tick, giving a
     *     16.0 ms median and a 30 ms p99;</li>
     * <li>{@code -XX:+ForceTimeHighResolution} turns {@code Thread.sleep(1)} from
     *     1.3 ms into 16 ms, with the same result.</li>
     * </ul>
     * Sleeping in 1 ms steps and spinning the last 2 ms brings the p99 to 16.8 ms
     * but costs 10-15% of a core, too much for a student laptop.
     *
     * @param deadline  The time to wake up, in {@code System.nanoTime()} terms.
     * @throws InterruptedException
     *             if another thread has interrupted the current thread
     */
    public static void sleepUntil(long deadline)
        throws InterruptedException
    {
        long remaining;
        // First, timed sleeps for a fraction of what is left. Each one may wake
        // late by a good part of what was asked for, so never ask for all of it.
        while ((remaining = deadline - System.nanoTime()) > SPIN_TAIL_NANOS) {
            if (Thread.interrupted()) {
                throw new InterruptedException("HDTimer.sleepUntil interrupted in sleep.");
            }
            long chunk = (long) (remaining * SLEEP_FRACTION);
            Thread.sleep(chunk / 1_000_000L, (int) (chunk % 1_000_000L));
        }

        // Then busy-wait for the last fraction of a millisecond.
        // (deadline - now > 0 rather than now < deadline: nanoTime may wrap around.)
        while (deadline - System.nanoTime() > 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException("HDTimer.sleepUntil interrupted in busy loop.");
            }
        }
    }
}
