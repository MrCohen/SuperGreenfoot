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

import java.io.IOException;

/**
 * One playing instance of a sound: a sample source plus volume, pan, playback
 * rate, loop flag and play state. Control methods may be called from any
 * thread; {@link #mix} is called by the mixer thread only.
 */
public final class Voice
{
    public enum State { PLAYING, PAUSED, STOPPED }

    private final SampleSource source;
    private volatile SoundCategory category;
    /** Amplitude gain 0..1 (already mapped from the 0..100 level). */
    private volatile float gain = 1f;
    /** -1 (left) .. 0 (centre) .. 1 (right). */
    private volatile float pan = 0f;
    /** Playback rate; 1 = normal, 2 = double speed and an octave up. */
    private volatile float rate = 1f;
    private volatile boolean loop;
    private volatile State state = State.PLAYING;
    /** Held by the mixer while the scenario is paused; independent of the user's pause(). */
    private volatile boolean held = false;
    /** Set by the mixer thread once the source is exhausted (and not looping). */
    private volatile boolean finished = false;

    // Rate-conversion state, mixer thread only
    private float[] rb = new float[2 * 4096];
    private int rbFrames = 0;
    private double rpos = 0;
    private boolean srcEof = false;

    public Voice(SampleSource source, SoundCategory category, boolean loop)
    {
        this.source = source;
        this.category = category;
        this.loop = loop;
    }

    public SoundCategory getCategory()
    {
        return category;
    }

    public void setCategory(SoundCategory category)
    {
        this.category = category;
    }

    public void setGain(float gain)
    {
        this.gain = Math.max(0f, Math.min(1f, gain));
    }

    public float getGain()
    {
        return gain;
    }

    public void setPan(float pan)
    {
        this.pan = Math.max(-1f, Math.min(1f, pan));
    }

    public float getPan()
    {
        return pan;
    }

    public void setRate(float rate)
    {
        this.rate = Math.max(0.05f, Math.min(16f, rate));
    }

    public float getRate()
    {
        return rate;
    }

    public void setLoop(boolean loop)
    {
        this.loop = loop;
    }

    public boolean isLoop()
    {
        return loop;
    }

    public State getState()
    {
        return state;
    }

    public boolean isPlaying()
    {
        return state == State.PLAYING && !finished;
    }

    public boolean isPaused()
    {
        return state == State.PAUSED && !finished;
    }

    /** True once the voice has stopped or run out of data; it will never play again. */
    public boolean isFinished()
    {
        return finished || state == State.STOPPED;
    }

    public void pause()
    {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    public void resume()
    {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    public void stop()
    {
        state = State.STOPPED;
    }

    /** Hold (true) or release (false) this voice for a scenario pause. */
    void setHeld(boolean held)
    {
        this.held = held;
    }

    boolean isHeld()
    {
        return held;
    }

    /** Called by the mixer once the voice is removed. */
    void close()
    {
        finished = true;
        source.close();
    }

    /**
     * Mix up to {@code frames} stereo frames of this voice into {@code out}
     * (added, not replaced) at the given extra gain (master * category).
     *
     * @return false if the voice has finished and should be removed.
     */
    boolean mix(float[] out, int frames, float extraGain)
    {
        if (state != State.PLAYING || finished || held) {
            return !isFinished();
        }
        float g = gain * extraGain;
        // Constant-power pan
        double a = (pan + 1) * Math.PI / 4;
        float gl = (float) (g * Math.cos(a));
        float gr = (float) (g * Math.sin(a));

        try {
            int produced = (rate == 1f) ? mixDirect(out, frames, gl, gr) : mixResampled(out, frames, gl, gr);
            if (produced < frames && srcEof && !loop) {
                finished = true;
                return false;
            }
        }
        catch (IOException e) {
            finished = true;
            return false;
        }
        return true;
    }

    /** Fill {@code dst} with up to n frames from the source, looping if enabled. */
    private int pull(float[] dst, int dstFrameOffset, int n) throws IOException
    {
        int total = 0;
        while (total < n) {
            int got = source.read(dst, dstFrameOffset + total, n - total);
            if (got <= 0) {
                if (loop) {
                    source.reset();
                    got = source.read(dst, dstFrameOffset + total, n - total);
                    if (got <= 0) {
                        srcEof = true;   // empty source; give up
                        break;
                    }
                }
                else {
                    srcEof = true;
                    break;
                }
            }
            total += got;
        }
        return total;
    }

    private float[] tmp = new float[0];

    private int mixDirect(float[] out, int frames, float gl, float gr) throws IOException
    {
        if (tmp.length < frames * 2) {
            tmp = new float[frames * 2];
        }
        // If we have leftover resampling buffer (rate was changed back to 1), drain it first
        int produced = 0;
        while (produced < frames && rbFrames > 0) {
            int i = (int) rpos;
            if (i >= rbFrames) {
                rbFrames = 0;
                rpos = 0;
                break;
            }
            out[2 * produced] += rb[2 * i] * gl;
            out[2 * produced + 1] += rb[2 * i + 1] * gr;
            produced++;
            rpos += 1;
        }
        if (rbFrames > 0 && (int) rpos >= rbFrames) {
            rbFrames = 0;
            rpos = 0;
        }
        int n = pull(tmp, 0, frames - produced);
        for (int i = 0; i < n; i++) {
            out[2 * (produced + i)] += tmp[2 * i] * gl;
            out[2 * (produced + i) + 1] += tmp[2 * i + 1] * gr;
        }
        return produced + n;
    }

    private int mixResampled(float[] out, int frames, float gl, float gr) throws IOException
    {
        double step = rate;
        int produced = 0;
        while (produced < frames) {
            int i = (int) rpos;
            if (i + 1 >= rbFrames) {
                // keep the last frame for interpolation, refill the rest
                int keep = Math.max(0, rbFrames - i);
                if (keep > 0 && i > 0) {
                    System.arraycopy(rb, 2 * i, rb, 0, 2 * keep);
                }
                rpos -= i;
                rbFrames = keep;
                int want = rb.length / 2 - rbFrames;
                int got = pull(rb, rbFrames, want);
                rbFrames += got;
                if (got == 0) {
                    if (rbFrames > 0 && (int) rpos < rbFrames) {
                        // emit the trailing frame
                        int k = (int) rpos;
                        out[2 * produced] += rb[2 * k] * gl;
                        out[2 * produced + 1] += rb[2 * k + 1] * gr;
                        produced++;
                        rpos += step;
                        continue;
                    }
                    break;
                }
                continue;
            }
            float t = (float) (rpos - i);
            int s0 = 2 * i;
            float l = rb[s0] + (rb[s0 + 2] - rb[s0]) * t;
            float r = rb[s0 + 1] + (rb[s0 + 3] - rb[s0 + 1]) * t;
            out[2 * produced] += l * gl;
            out[2 * produced + 1] += r * gr;
            produced++;
            rpos += step;
        }
        return produced;
    }
}
