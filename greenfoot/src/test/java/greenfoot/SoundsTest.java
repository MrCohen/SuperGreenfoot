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
package greenfoot;

import greenfoot.sound.SoundLibrary;
import greenfoot.sound.SoundMixer;
import greenfoot.sound.SoundMixerTest;
import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

import java.io.File;

/**
 * Tests the Sounds registry and GreenfootSound over a mixer with no audio
 * device; mixing is driven by hand.
 */
public class SoundsTest extends TestCase
{
    private SoundMixer mixer;
    private String wav;
    private final float[] out = new float[2 * SoundMixer.BLOCK_FRAMES];

    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        mixer = SoundMixer.installForTesting(false);
        SoundLibrary.clear();
        Sounds.stopAll();
        File f = SoundMixerTest.writeWav(44100, 16, 2, 4000);   // ~0.09 s
        wav = f.getAbsolutePath();
    }

    private void mix(int blocks)
    {
        for (int i = 0; i < blocks; i++) {
            mixer.mixInto(out, SoundMixer.BLOCK_FRAMES);
        }
    }

    public void testGreenfootSoundPlaysOnceAndFinishes()
    {
        GreenfootSound s = new GreenfootSound(wav);
        assertFalse(s.isPlaying());
        s.play();
        assertTrue(s.isPlaying());
        mix(2);    // 2048 frames of 4000
        assertTrue(s.isPlaying());
        mix(3);
        assertFalse(s.isPlaying());
        assertEquals(0, mixer.getVoiceCount());
        // Can be played again from the start
        s.play();
        assertTrue(s.isPlaying());
    }

    public void testGreenfootSoundPlayWhilePlayingDoesNothing()
    {
        GreenfootSound s = new GreenfootSound(wav);
        s.play();
        s.play();
        assertEquals(1, mixer.getVoiceCount());
    }

    public void testGreenfootSoundLoopThenPlayFinishesLoop()
    {
        GreenfootSound s = new GreenfootSound(wav);
        s.playLoop();
        mix(10);   // well past one length: still playing because looping
        assertTrue(s.isPlaying());
        s.play();  // "finish the current loop and stop"
        mix(10);
        assertFalse(s.isPlaying());
    }

    public void testGreenfootSoundPauseResume()
    {
        GreenfootSound s = new GreenfootSound(wav);
        s.play();
        s.pause();
        assertTrue(s.isPaused());
        assertFalse(s.isPlaying());
        mix(10);
        assertTrue(s.isPaused());   // paused sounds do not finish
        s.play();                   // resumes
        assertTrue(s.isPlaying());
        s.stop();
        assertFalse(s.isPlaying());
        assertFalse(s.isPaused());
    }

    public void testSoundsPlayOverlaps()
    {
        Sounds.load("beep", wav);
        assertTrue(Sounds.isLoaded("beep"));
        GreenfootSound a = Sounds.play("beep");
        GreenfootSound b = Sounds.play("beep");
        GreenfootSound c = Sounds.play("beep");
        assertNotSame(a, b);
        assertEquals(3, mixer.getVoiceCount());
        assertEquals(3, Sounds.getActiveCount("beep"));
        assertTrue(Sounds.isPlaying("beep"));
        mix(5);
        assertFalse(Sounds.isPlaying("beep"));
        assertEquals(0, Sounds.getActiveCount("beep"));
        assertFalse(c.isPlaying());
    }

    public void testMaxVoicesStealsOldest()
    {
        Sounds.load("beep", wav);
        Sounds.setMaxVoices("beep", 2);
        GreenfootSound a = Sounds.play("beep");
        GreenfootSound b = Sounds.play("beep");
        GreenfootSound c = Sounds.play("beep");
        assertFalse(a.isPlaying());
        assertTrue(b.isPlaying());
        assertTrue(c.isPlaying());
        assertEquals(2, Sounds.getActiveCount("beep"));
    }

    public void testStopKeyAndStopAll()
    {
        Sounds.load("beep", wav);
        Sounds.play("beep");
        Sounds.play("beep");
        Sounds.stop("beep");
        assertFalse(Sounds.isPlaying("beep"));
        mix(1);
        assertEquals(0, mixer.getVoiceCount());

        Sounds.playLoop("beep");
        Sounds.playMusic(wav);
        assertNotNull(Sounds.getMusic());
        assertEquals(SoundCategory.MUSIC, Sounds.getMusic().getCategory());
        Sounds.stopAll();
        assertNull(Sounds.getMusic());
        mix(1);
        assertEquals(0, mixer.getVoiceCount());
    }

    public void testPlayMusicReplacesPreviousMusic()
    {
        GreenfootSound first = Sounds.playMusic(wav);
        assertTrue(first.isPlaying());
        GreenfootSound second = Sounds.playMusic(wav);
        assertFalse(first.isPlaying());
        assertTrue(second.isPlaying());
        assertSame(second, Sounds.getMusic());
        mix(20);
        assertTrue(second.isPlaying());   // loops
        Sounds.stopMusic();
        assertFalse(second.isPlaying());
    }

    public void testPauseAllResumeAll()
    {
        Sounds.load("beep", wav);
        GreenfootSound a = Sounds.play("beep");
        GreenfootSound m = Sounds.playMusic(wav);
        Sounds.pauseAll();
        assertTrue(a.isPaused());
        assertTrue(m.isPaused());
        mix(10);
        Sounds.resumeAll();
        assertTrue(a.isPlaying());
        assertTrue(m.isPlaying());
    }

    public void testFileNameAutoLoadsAndUnknownKeyIsSilent()
    {
        GreenfootSound s = Sounds.play(wav);
        assertTrue(s.isPlaying());
        assertTrue(Sounds.isLoaded(wav));

        GreenfootSound silent = Sounds.play("no-such-key");
        assertNotNull(silent);
        silent.play();
        silent.stop();
        assertFalse(silent.isPlaying());
        assertFalse(Sounds.isLoaded("no-such-key"));
    }

    public void testVolumesFlowIntoMixer()
    {
        Sounds.load("beep", wav, SoundCategory.EFFECTS, 50);
        GreenfootSound s = Sounds.play("beep");
        assertEquals(50, s.getVolume());
        Sounds.setVolume("beep", 80);
        assertEquals(80, s.getVolume());

        Sounds.setMasterVolume(70);
        assertEquals(70, Sounds.getMasterVolume());
        Sounds.setCategoryVolume(SoundCategory.EFFECTS, 30);
        assertEquals(30, Sounds.getCategoryVolume(SoundCategory.EFFECTS));
        Sounds.setMuted(SoundCategory.EFFECTS, true);
        assertTrue(Sounds.isMuted(SoundCategory.EFFECTS));
        mix(1);
        for (int i = 0; i < 20; i++) {
            assertEquals(0f, out[i], 1e-6f);
        }
        Sounds.setMuted(SoundCategory.EFFECTS, false);
        s.stop();
        GreenfootSound t = Sounds.play("beep", 100, -1.0);   // hard left
        assertEquals(-1.0, t.getPan(), 1e-9);
        mix(1);
        boolean anyLeft = false;
        for (int i = 0; i < SoundMixer.BLOCK_FRAMES; i++) {
            if (Math.abs(out[2 * i]) > 1e-6f) anyLeft = true;
            assertEquals(0f, out[2 * i + 1], 1e-6f);
        }
        assertTrue(anyLeft);
    }

    public void testMissingFileThrows()
    {
        try {
            new GreenfootSound("does-not-exist.wav");
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does-not-exist.wav"));
        }
    }

    public void testPlaybackRate()
    {
        GreenfootSound s = new GreenfootSound(wav);
        s.setPlaybackRate(4);
        s.play();
        mix(1);   // 1024 output frames = 4096 source frames > 4000
        assertFalse(s.isPlaying());
    }
}
