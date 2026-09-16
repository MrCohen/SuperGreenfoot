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
package greenfoot.sound;

import greenfoot.SoundCategory;
import greenfoot.event.SimulationListener;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * The SuperGreenfoot software mixer. All playing {@link Voice}s are summed on
 * one thread into a single {@link SourceDataLine}, so any number of sounds can
 * overlap, the same clip can play many times at once, and master and
 * per-category volume apply instantly to everything.
 *
 * <p>The output line is opened lazily on the first play and never closed and
 * reopened, which sidesteps the platform bugs documented in {@link AudioLine}.
 * If no output device is available the mixer still runs (silently) so that
 * play state and timing behave normally.
 */
public final class SoundMixer
{
    /** Frames mixed per block; 1024 at 44.1 kHz is about 23 ms. */
    public static final int BLOCK_FRAMES = 1024;
    /** Blocks buffered on the line (latency about 3 blocks, 70 ms). */
    private static final int LINE_BLOCKS = 3;

    private static SoundMixer instance;

    /** All live voices. Guarded by itself. */
    private final List<Voice> voices = new ArrayList<Voice>();

    private volatile float masterGain = 1f;
    private final float[] categoryGain = new float[SoundCategory.values().length];
    private final boolean[] categoryMuted = new boolean[SoundCategory.values().length];

    private final boolean useOutput;
    private Thread thread;
    private volatile boolean running = false;
    private SourceDataLine line;

    private final SimulationListener simulationListener = new SimulationListener() {
        @Override
        @OnThread(Tag.Simulation)
        public void simulationChangedSync(SyncEvent e)
        {
            if (e == SyncEvent.STARTED) {
                releaseHeld();
            }
        }

        @Override
        @OnThread(Tag.Any)
        public void simulationChangedAsync(AsyncEvent e)
        {
            if (e == AsyncEvent.STOPPED) {
                holdPlaying();
            }
            else if (e == AsyncEvent.DISABLED) {
                stopAll();
            }
        }
    };

    /**
     * @param useOutput false to run without an audio device and without an output
     *                  thread; the caller drives {@link #mixInto} (tests, headless tools).
     */
    SoundMixer(boolean useOutput)
    {
        this.useOutput = useOutput;
        Arrays.fill(categoryGain, 1f);
    }

    /** The shared mixer, created on first use. */
    public static synchronized SoundMixer getInstance()
    {
        if (instance == null) {
            instance = new SoundMixer(true);
        }
        return instance;
    }

    /** Replace the shared mixer (tests only). */
    public static synchronized SoundMixer installForTesting(boolean useOutput)
    {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new SoundMixer(useOutput);
        return instance;
    }

    /** Listener that pauses all sound while the scenario is paused and stops it when the VM is discarded. */
    public SimulationListener getSimulationListener()
    {
        return simulationListener;
    }

    // ---- volume ----

    /** Map a 0..100 level to an amplitude gain (perceptually roughly linear). */
    public static float levelToGain(int level)
    {
        int l = Math.max(0, Math.min(100, level));
        float f = l / 100f;
        return f * f;
    }

    public void setMasterLevel(int level)
    {
        masterGain = levelToGain(level);
        masterLevel = Math.max(0, Math.min(100, level));
    }

    private volatile int masterLevel = 100;
    private final int[] categoryLevel = new int[SoundCategory.values().length];
    {
        Arrays.fill(categoryLevel, 100);
    }

    public int getMasterLevel()
    {
        return masterLevel;
    }

    public void setCategoryLevel(SoundCategory c, int level)
    {
        categoryGain[c.ordinal()] = levelToGain(level);
        categoryLevel[c.ordinal()] = Math.max(0, Math.min(100, level));
    }

    public int getCategoryLevel(SoundCategory c)
    {
        return categoryLevel[c.ordinal()];
    }

    public void setCategoryMuted(SoundCategory c, boolean muted)
    {
        categoryMuted[c.ordinal()] = muted;
    }

    public boolean isCategoryMuted(SoundCategory c)
    {
        return categoryMuted[c.ordinal()];
    }

    // ---- voices ----

    /** Register a voice for mixing and make sure the output is running. */
    public void add(Voice v)
    {
        synchronized (voices) {
            voices.add(v);
        }
        ensureStarted();
    }

    /** Number of voices currently registered (playing, paused, or awaiting removal). */
    public int getVoiceCount()
    {
        synchronized (voices) {
            return voices.size();
        }
    }

    public void stopAll()
    {
        synchronized (voices) {
            for (Voice v : voices) {
                v.stop();
            }
        }
    }

    /**
     * Hold every voice that is currently playing (scenario paused). Voices
     * started while the scenario is paused are not held, so sounds triggered
     * from a world constructor or an interactive method call play immediately,
     * as in Greenfoot.
     */
    public void holdPlaying()
    {
        synchronized (voices) {
            for (Voice v : voices) {
                if (v.getState() == Voice.State.PLAYING && !v.isHeld()) {
                    v.setHeld(true);
                }
            }
        }
    }

    /** Release all held voices (scenario resumed). */
    public void releaseHeld()
    {
        synchronized (voices) {
            for (Voice v : voices) {
                v.setHeld(false);
            }
        }
    }

    /**
     * Mix one block: {@code out} is overwritten with the sum of all playing
     * voices. Finished and stopped voices are removed. Safe to call directly
     * in tests; the output thread calls it continuously.
     */
    public void mixInto(float[] out, int frames)
    {
        Arrays.fill(out, 0, frames * 2, 0f);
        Voice[] snapshot;
        synchronized (voices) {
            snapshot = voices.toArray(new Voice[0]);
        }
        for (Voice v : snapshot) {
            boolean keep;
            if (v.getState() == Voice.State.STOPPED) {
                keep = false;
            }
            else if (v.isHeld() || v.getState() != Voice.State.PLAYING) {
                keep = !v.isFinished();
            }
            else {
                int c = v.getCategory().ordinal();
                float gain = categoryMuted[c] ? 0f : masterGain * categoryGain[c];
                keep = v.mix(out, frames, gain);
            }
            if (!keep) {
                synchronized (voices) {
                    voices.remove(v);
                }
                v.close();
            }
        }
    }

    // ---- output thread ----

    private synchronized void ensureStarted()
    {
        if (running || !useOutput) {
            // Without output there is no thread: the owner (tests, headless
            // tools) drives mixInto() itself.
            return;
        }
        running = true;
        thread = new Thread(this::run, "SuperGreenfoot-SoundMixer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void openLine()
    {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioDecoder.MIX_FORMAT);
            javax.sound.sampled.Mixer preferred = SoundUtils.loadMixer(false);
            SourceDataLine l;
            if (preferred != null && preferred.isLineSupported(info)) {
                l = (SourceDataLine) preferred.getLine(info);
            }
            else {
                l = AudioSystem.getSourceDataLine(AudioDecoder.MIX_FORMAT);
            }
            l.open(AudioDecoder.MIX_FORMAT, BLOCK_FRAMES * 4 * LINE_BLOCKS);
            l.start();
            line = l;
        }
        catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            System.err.println("SuperGreenfoot: no audio output available (" + e.getMessage() + "); sound will be silent.");
            line = null;
        }
    }

    private void run()
    {
        openLine();
        float[] mix = new float[BLOCK_FRAMES * 2];
        byte[] bytes = new byte[BLOCK_FRAMES * 4];
        long silentSince = -1;
        while (running) {
            mixInto(mix, BLOCK_FRAMES);
            for (int i = 0; i < BLOCK_FRAMES * 2; i++) {
                float v = mix[i];
                if (v > 1f) v = 1f;
                if (v < -1f) v = -1f;
                int s = (int) (v * 32767f);
                bytes[2 * i] = (byte) s;
                bytes[2 * i + 1] = (byte) (s >> 8);
            }
            if (line != null) {
                // Blocks until the line has room: this paces the loop.
                line.write(bytes, 0, bytes.length);
            }
            else {
                // Silent mode: pace by wall clock instead.
                try {
                    Thread.sleep((long) (BLOCK_FRAMES * 1000.0 / AudioDecoder.MIX_RATE));
                }
                catch (InterruptedException e) {
                    break;
                }
            }
        }
        if (line != null) {
            try {
                line.stop();
                line.close();
            }
            catch (Exception e) {
                // ignore
            }
            line = null;
        }
    }

    /** Stop everything and end the output thread. */
    public synchronized void shutdown()
    {
        stopAll();
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
