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
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Headless tests of the software mixer: decoding, voice mixing maths, looping,
 * pan, rate, pause/hold and category volumes. No audio device is used.
 */
public class SoundMixerTest extends TestCase
{
    private static final float EPS = 1e-4f;

    /** A clip of n frames with constant left=0.5, right=-0.25. */
    private static PcmClip constantClip(int n)
    {
        float[] f = new float[n * 2];
        for (int i = 0; i < n; i++) {
            f[2 * i] = 0.5f;
            f[2 * i + 1] = -0.25f;
        }
        return PcmClip.fromFloats(f, n);
    }

    /** A clip whose left channel is a ramp 0,1,2,...(scaled) for testing rate. */
    private static PcmClip rampClip(int n)
    {
        float[] f = new float[n * 2];
        for (int i = 0; i < n; i++) {
            f[2 * i] = i / 1000f;
            f[2 * i + 1] = 0;
        }
        return PcmClip.fromFloats(f, n);
    }

    private SoundMixer mixer;

    @Override
    protected void setUp()
    {
        mixer = new SoundMixer(false);
    }

    public void testSingleVoiceFullVolumeCentre()
    {
        Voice v = new Voice(constantClip(100).newSource(), SoundCategory.EFFECTS, false);
        mixer.add(v);
        float[] out = new float[2 * 50];
        mixer.mixInto(out, 50);
        // Centre pan: both channels scaled by cos(45deg) = 0.7071
        assertEquals(0.5f * 0.70710677f, out[0], EPS);
        assertEquals(-0.25f * 0.70710677f, out[1], EPS);
        assertEquals(0.5f * 0.70710677f, out[98], EPS);
        assertTrue(v.isPlaying());
        assertEquals(1, mixer.getVoiceCount());
    }

    public void testVoiceFinishesAndIsRemoved()
    {
        Voice v = new Voice(constantClip(30).newSource(), SoundCategory.EFFECTS, false);
        mixer.add(v);
        float[] out = new float[2 * 50];
        mixer.mixInto(out, 50);
        // Frames 0..29 have signal, 30..49 are silent
        assertTrue(Math.abs(out[2 * 29]) > 0.1f);
        assertEquals(0f, out[2 * 30], EPS);
        assertTrue(v.isFinished());
        assertFalse(v.isPlaying());
        assertEquals(0, mixer.getVoiceCount());
    }

    public void testLoopWrapsSeamlessly()
    {
        Voice v = new Voice(rampClip(10).newSource(), SoundCategory.EFFECTS, true);
        mixer.add(v);
        float[] out = new float[2 * 25];
        mixer.mixInto(out, 25);
        float k = 0.70710677f;
        assertEquals(9 / 1000f * k, out[2 * 9], EPS);
        assertEquals(0f, out[2 * 10], EPS);          // wrapped to frame 0
        assertEquals(1 / 1000f * k, out[2 * 11], EPS);
        assertEquals(4 / 1000f * k, out[2 * 24], EPS);
        assertTrue(v.isPlaying());
        v.setLoop(false);
        mixer.mixInto(out, 25);   // frames 25..29 then end
        assertEquals(5 / 1000f * k, out[0], EPS);
        assertEquals(0f, out[2 * 5], EPS);
        assertTrue(v.isFinished());
    }

    public void testGainPanAndMasterAndCategory()
    {
        Voice v = new Voice(constantClip(100).newSource(), SoundCategory.MUSIC, false);
        v.setGain(SoundMixer.levelToGain(50));   // 0.25
        v.setPan(1f);                            // hard right: left gain 0, right gain 1
        mixer.setMasterLevel(100);
        mixer.setCategoryLevel(SoundCategory.MUSIC, 100);
        mixer.add(v);
        float[] out = new float[2 * 10];
        mixer.mixInto(out, 10);
        assertEquals(0f, out[0], EPS);
        assertEquals(-0.25f * 0.25f, out[1], EPS);

        mixer.setCategoryLevel(SoundCategory.MUSIC, 50);   // x0.25
        mixer.mixInto(out, 10);
        assertEquals(-0.25f * 0.25f * 0.25f, out[1], EPS);

        mixer.setCategoryMuted(SoundCategory.MUSIC, true);
        mixer.mixInto(out, 10);
        assertEquals(0f, out[1], EPS);
        assertTrue(v.isPlaying());   // muted still advances and stays playing

        mixer.setCategoryMuted(SoundCategory.MUSIC, false);
        mixer.setCategoryLevel(SoundCategory.EFFECTS, 0);   // other category, no effect
        mixer.mixInto(out, 10);
        assertEquals(-0.25f * 0.25f * 0.25f, out[1], EPS);
    }

    public void testTwoVoicesSum()
    {
        mixer.add(new Voice(constantClip(100).newSource(), SoundCategory.EFFECTS, false));
        mixer.add(new Voice(constantClip(100).newSource(), SoundCategory.EFFECTS, false));
        float[] out = new float[2 * 4];
        mixer.mixInto(out, 4);
        assertEquals(2 * 0.5f * 0.70710677f, out[0], EPS);
    }

    public void testPauseResumeAndHold()
    {
        Voice v = new Voice(rampClip(100).newSource(), SoundCategory.EFFECTS, false);
        mixer.add(v);
        float[] out = new float[2 * 10];
        mixer.mixInto(out, 10);            // consumed frames 0..9
        v.pause();
        assertTrue(v.isPaused());
        mixer.mixInto(out, 10);
        assertEquals(0f, out[0], EPS);     // silent while paused
        assertEquals(1, mixer.getVoiceCount());
        v.resume();
        mixer.mixInto(out, 10);
        assertEquals(10 / 1000f * 0.70710677f, out[0], EPS);   // continues from frame 10

        // Scenario hold is independent of user pause
        mixer.holdPlaying();
        mixer.mixInto(out, 10);
        assertEquals(0f, out[0], EPS);
        assertTrue(v.isPlaying());         // still "playing" from the user's point of view
        mixer.releaseHeld();
        mixer.mixInto(out, 10);
        assertEquals(20 / 1000f * 0.70710677f, out[0], EPS);

        // Voices started while held are not held
        mixer.holdPlaying();
        Voice w = new Voice(constantClip(100).newSource(), SoundCategory.EFFECTS, false);
        mixer.add(w);
        mixer.mixInto(out, 10);
        assertEquals(0.5f * 0.70710677f, out[0], EPS);
    }

    public void testStopRemovesVoice()
    {
        Voice v = new Voice(constantClip(100).newSource(), SoundCategory.EFFECTS, true);
        mixer.add(v);
        v.stop();
        float[] out = new float[2 * 10];
        mixer.mixInto(out, 10);
        assertEquals(0f, out[0], EPS);
        assertEquals(0, mixer.getVoiceCount());
        assertTrue(v.isFinished());
    }

    public void testRateDoubleConsumesTwiceAsFast()
    {
        Voice v = new Voice(rampClip(100).newSource(), SoundCategory.EFFECTS, false);
        v.setRate(2f);
        mixer.add(v);
        float[] out = new float[2 * 10];
        mixer.mixInto(out, 10);
        float k = 0.70710677f;
        assertEquals(0f, out[0], EPS);
        assertEquals(2 / 1000f * k, out[2], EPS);
        assertEquals(18 / 1000f * k, out[18], EPS);
        mixer.mixInto(out, 10);
        assertEquals(20 / 1000f * k, out[0], EPS);
    }

    public void testRateHalfInterpolates()
    {
        Voice v = new Voice(rampClip(100).newSource(), SoundCategory.EFFECTS, false);
        v.setRate(0.5f);
        mixer.add(v);
        float[] out = new float[2 * 10];
        mixer.mixInto(out, 10);
        float k = 0.70710677f;
        assertEquals(0f, out[0], EPS);
        assertEquals(0.5f / 1000f * k, out[2], EPS);
        assertEquals(1 / 1000f * k, out[4], EPS);
        assertEquals(4.5f / 1000f * k, out[18], EPS);
    }

    public void testLevelToGain()
    {
        assertEquals(0f, SoundMixer.levelToGain(0), EPS);
        assertEquals(0.25f, SoundMixer.levelToGain(50), EPS);
        assertEquals(1f, SoundMixer.levelToGain(100), EPS);
        assertEquals(1f, SoundMixer.levelToGain(150), EPS);
        assertEquals(0f, SoundMixer.levelToGain(-5), EPS);
    }

    // ---- decoding ----

    /** Write a WAV file with the given format and a ramp signal; returns the file. */
    public static File writeWav(float sampleRate, int bits, int channels, int frames) throws IOException
    {
        AudioFormat fmt = new AudioFormat(sampleRate, bits, channels, true, false);
        int bytesPerSample = bits / 8;
        byte[] data = new byte[frames * channels * bytesPerSample];
        int bi = 0;
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                // left = ramp, right = negative ramp; 8-bit uses signed bytes
                double v = (c == 0 ? 1 : -1) * (i / (double) frames) * 0.9;
                if (bits == 16) {
                    short s = (short) Math.round(v * 32767);
                    data[bi++] = (byte) s;
                    data[bi++] = (byte) (s >> 8);
                }
                else {
                    data[bi++] = (byte) Math.round(v * 127);
                }
            }
        }
        AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(data), fmt, frames);
        File f = File.createTempFile("sgf-test-" + (int) sampleRate + "-" + bits + "-" + channels, ".wav");
        f.deleteOnExit();
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, f);
        return f;
    }

    public void testDecodeStereo16bit44100IsIdentity() throws Exception
    {
        File f = writeWav(44100, 16, 2, 1000);
        PcmClip clip = PcmClip.decode(f.toURI().toURL());
        assertEquals(1000, clip.getFrames());
        float[] out = new float[2 * 1000];
        clip.newSource().read(out, 0, 1000);
        assertEquals(0f, out[0], 1e-3f);
        assertEquals(0.9f * 500 / 1000f, out[2 * 500], 1e-3f);
        assertEquals(-0.9f * 500 / 1000f, out[2 * 500 + 1], 1e-3f);
    }

    public void testDecodeMono8bit22050Resamples() throws Exception
    {
        File f = writeWav(22050, 8, 1, 1000);
        PcmClip clip = PcmClip.decode(f.toURI().toURL());
        // 22050 -> 44100 doubles the frame count (within a frame or two)
        assertTrue("frames " + clip.getFrames(), Math.abs(clip.getFrames() - 2000) <= 2);
        float[] out = new float[2 * clip.getFrames()];
        clip.newSource().read(out, 0, clip.getFrames());
        // Mono duplicated to both channels; value at the middle is about 0.45
        assertEquals(out[2 * 1000], out[2 * 1000 + 1], 1e-4f);
        assertEquals(0.9f * 0.5f, out[2 * 1000], 0.02f);
        // Monotonic ramp on the left channel
        for (int i = 10; i < clip.getFrames() - 10; i += 50) {
            assertTrue(out[2 * i] <= out[2 * (i + 10)] + 1e-3f);
        }
    }

    public void testStreamSourceLoopsByReopening() throws Exception
    {
        File f = writeWav(44100, 16, 2, 500);
        StreamSource s = new StreamSource(f.toURI().toURL());
        float[] buf = new float[2 * 600];
        int n = s.read(buf, 0, 600);
        assertEquals(500, n);
        assertEquals(0, s.read(buf, 0, 10));
        s.reset();
        assertEquals(10, s.read(buf, 0, 10));
        s.close();
    }
}
