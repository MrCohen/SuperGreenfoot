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

import java.io.IOException;
import java.net.URL;

import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * A fully decoded sound held in memory as 16-bit interleaved stereo samples at
 * the mixer rate. One PcmClip is shared by any number of simultaneously
 * playing voices; each voice has its own {@link ClipSource} position.
 */
public final class PcmClip
{
    /** Interleaved stereo samples: [L0, R0, L1, R1, ...]. */
    private final short[] data;
    private final int frames;

    public PcmClip(short[] interleavedStereo)
    {
        this.data = interleavedStereo;
        this.frames = interleavedStereo.length / 2;
    }

    /** Build a clip from float frames (values clamped to -1..1). */
    public static PcmClip fromFloats(float[] interleavedStereo, int frames)
    {
        short[] s = new short[frames * 2];
        for (int i = 0; i < frames * 2; i++) {
            float v = interleavedStereo[i];
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            s[i] = (short) Math.round(v * 32767f);
        }
        return new PcmClip(s);
    }

    /** Decode a whole file into memory. */
    public static PcmClip decode(URL url) throws IOException, UnsupportedAudioFileException
    {
        try (AudioDecoder.PcmReader reader = AudioDecoder.open(url)) {
            long est = reader.getEstimatedFrames();
            int cap = est > 0 && est < Integer.MAX_VALUE / 4 ? (int) est + 16 : 44100 * 4;
            float[] buf = new float[cap * 2];
            int total = 0;
            while (true) {
                if ((total + 4096) * 2 > buf.length) {
                    float[] bigger = new float[buf.length * 2];
                    System.arraycopy(buf, 0, bigger, 0, total * 2);
                    buf = bigger;
                }
                int n = reader.read(buf, total, 4096);
                if (n <= 0) {
                    break;
                }
                total += n;
            }
            return fromFloats(buf, total);
        }
    }

    public int getFrames()
    {
        return frames;
    }

    /** Duration in seconds at the mix rate. */
    public double getSeconds()
    {
        return frames / (double) AudioDecoder.MIX_RATE;
    }

    /** Memory used by the samples, in bytes. */
    public long getBytes()
    {
        return data.length * 2L;
    }

    public SampleSource newSource()
    {
        return new ClipSource();
    }

    /** A read position over the shared clip data. */
    private final class ClipSource implements SampleSource
    {
        private int pos = 0;

        @Override
        public int read(float[] dst, int dstFrameOffset, int n)
        {
            int avail = frames - pos;
            if (avail <= 0) {
                return 0;
            }
            int count = Math.min(n, avail);
            int si = pos * 2;
            int di = dstFrameOffset * 2;
            for (int i = 0; i < count * 2; i++) {
                dst[di + i] = data[si + i] / 32768f;
            }
            pos += count;
            return count;
        }

        @Override
        public void reset()
        {
            pos = 0;
        }

        @Override
        public void close()
        {
        }
    }
}
