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
 * It costs about 2% of one core at 60 acts a second.
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
        while (System.nanoTime() < deadline) {
            if (Thread.interrupted()) {
                throw new InterruptedException("HDTimer.sleepUntil interrupted in busy loop.");
            }
        }
    }
}
