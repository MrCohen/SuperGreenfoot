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

import greenfoot.util.GreenfootUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Opens sound files (WAV, AIFF, AU and, via JLayer, MP3) and delivers their
 * audio as stereo float frames at the mixer's sample rate, converting sample
 * size, channel count and sample rate as needed.
 *
 * <p>Java Sound is used only to get to signed 16-bit PCM at the file's own
 * rate and channel count (a conversion every JDK supports); the channel and
 * rate conversion to the mix format is done here in float, so behaviour is
 * identical on every platform and easy to port.
 */
public final class AudioDecoder
{
    /** The mixer's sample rate in Hz. Everything is resampled to this. */
    public static final float MIX_RATE = 44100f;

    /** The mixer's output line format: 44.1 kHz, 16-bit signed, stereo, little-endian. */
    public static final AudioFormat MIX_FORMAT = new AudioFormat(MIX_RATE, 16, 2, true, false);

    private AudioDecoder()
    {
    }

    /**
     * Open a sound URL as a reader that produces mix-format frames.
     */
    public static PcmReader open(URL url) throws IOException, UnsupportedAudioFileException
    {
        AudioInputStream src;
        if (isMp3(url)) {
            if (!GreenfootUtil.isMp3LibAvailable()) {
                throw new UnsupportedAudioFileException("MP3 support is not available: " + url);
            }
            Mp3AudioInputStream mp3 = new Mp3AudioInputStream(url);
            mp3.open();
            src = new AudioInputStream(new AdapterInputStream(mp3), mp3.getFormat(), AudioSystem.NOT_SPECIFIED);
        }
        else {
            src = AudioSystem.getAudioInputStream(url);
        }
        return new PcmReader(toPcm16(src));
    }

    /**
     * Convert any stream to signed 16-bit little-endian PCM, keeping its sample
     * rate and channel count.
     */
    static AudioInputStream toPcm16(AudioInputStream src)
    {
        AudioFormat f = src.getFormat();
        boolean ok = f.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                && f.getSampleSizeInBits() == 16
                && !f.isBigEndian()
                && (f.getChannels() == 1 || f.getChannels() == 2);
        if (ok) {
            return src;
        }
        int channels = f.getChannels() >= 2 ? 2 : 1;
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                channels, channels * 2, f.getSampleRate(), false);
        return AudioSystem.getAudioInputStream(target, src);
    }

    static boolean isMp3(URL url)
    {
        return url.toString().toLowerCase().endsWith(".mp3");
    }

    /**
     * Adapts a GreenfootAudioInputStream (which is not an InputStream) for
     * javax.sound.sampled.AudioInputStream.
     */
    private static final class AdapterInputStream extends InputStream
    {
        private final GreenfootAudioInputStream in;

        AdapterInputStream(GreenfootAudioInputStream in)
        {
            this.in = in;
        }

        @Override
        public int read() throws IOException
        {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            return in.read(b, off, len);
        }

        @Override
        public int available() throws IOException
        {
            return in.available();
        }

        @Override
        public void close() throws IOException
        {
            in.close();
        }
    }

    /**
     * Reads signed 16-bit PCM (mono or stereo, any rate) and produces stereo
     * float frames at {@link #MIX_RATE} using linear interpolation.
     */
    public static final class PcmReader implements AutoCloseable
    {
        private final AudioInputStream in;
        private final int channels;
        /** Source frames advanced per output frame. */
        private final double step;
        private final byte[] bytes = new byte[16384];
        /** Buffered source frames, already converted to stereo float. */
        private float[] src = new float[2 * 8192];
        private int srcFrames = 0;
        /** Read position in source frames, relative to src[0]. */
        private double pos = 0;
        private boolean eof = false;

        PcmReader(AudioInputStream in)
        {
            this.in = in;
            this.channels = in.getFormat().getChannels();
            this.step = in.getFormat().getSampleRate() / MIX_RATE;
        }

        /** Total source frames if known, else -1. */
        public long getSourceFrameLength()
        {
            long n = in.getFrameLength();
            return n == AudioSystem.NOT_SPECIFIED ? -1 : n;
        }

        /** Estimated output frames if the source length is known, else -1. */
        public long getEstimatedFrames()
        {
            long n = getSourceFrameLength();
            return n < 0 ? -1 : (long) Math.ceil(n / step);
        }

        /**
         * Read up to {@code frames} stereo frames into dst at the given frame offset.
         * @return frames produced; 0 at end of stream.
         */
        public int read(float[] dst, int dstFrameOffset, int frames) throws IOException
        {
            int produced = 0;
            int di = dstFrameOffset * 2;
            while (produced < frames) {
                int i = (int) pos;
                if (i + 1 >= srcFrames) {
                    if (eof) {
                        // Allow the final frame to be emitted (interpolating against itself)
                        if (i < srcFrames && pos < srcFrames) {
                            dst[di++] = src[2 * i];
                            dst[di++] = src[2 * i + 1];
                            produced++;
                            pos += step;
                            continue;
                        }
                        break;
                    }
                    refill(i);
                    continue;
                }
                float t = (float) (pos - i);
                int s0 = 2 * i;
                int s1 = s0 + 2;
                dst[di++] = src[s0] + (src[s1] - src[s0]) * t;
                dst[di++] = src[s0 + 1] + (src[s1 + 1] - src[s0 + 1]) * t;
                produced++;
                pos += step;
            }
            return produced;
        }

        /** Keep source frames from index {@code keepFrom}, then read more from the stream. */
        private void refill(int keepFrom) throws IOException
        {
            int remaining = Math.max(0, srcFrames - keepFrom);
            if (keepFrom > 0 && remaining > 0) {
                System.arraycopy(src, 2 * keepFrom, src, 0, 2 * remaining);
            }
            pos -= keepFrom;
            srcFrames = remaining;
            if (pos < 0) {
                pos = 0;
            }

            int bytesPerFrame = channels * 2;
            int n = in.read(bytes, 0, bytes.length - (bytes.length % bytesPerFrame));
            if (n <= 0) {
                eof = true;
                return;
            }
            int newFrames = n / bytesPerFrame;
            if ((srcFrames + newFrames) * 2 > src.length) {
                float[] bigger = new float[Math.max(src.length * 2, (srcFrames + newFrames) * 2)];
                System.arraycopy(src, 0, bigger, 0, srcFrames * 2);
                src = bigger;
            }
            int si = srcFrames * 2;
            int bi = 0;
            if (channels == 1) {
                for (int k = 0; k < newFrames; k++) {
                    float v = ((short) ((bytes[bi + 1] << 8) | (bytes[bi] & 0xff))) / 32768f;
                    src[si++] = v;
                    src[si++] = v;
                    bi += 2;
                }
            }
            else {
                for (int k = 0; k < newFrames; k++) {
                    src[si++] = ((short) ((bytes[bi + 1] << 8) | (bytes[bi] & 0xff))) / 32768f;
                    src[si++] = ((short) ((bytes[bi + 3] << 8) | (bytes[bi + 2] & 0xff))) / 32768f;
                    bi += 4;
                }
            }
            srcFrames += newFrames;
        }

        @Override
        public void close()
        {
            try {
                in.close();
            }
            catch (IOException e) {
                // ignore
            }
        }
    }
}
