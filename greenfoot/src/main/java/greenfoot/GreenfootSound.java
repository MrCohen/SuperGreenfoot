/*
 This file is part of the Greenfoot program.
 Copyright (C) 2005-2009,2010,2011,2013,2014,2015,2016  Poul Henriksen and Michael Kolling
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

import greenfoot.sound.Sound;
import greenfoot.sound.SoundFactory;
import greenfoot.sound.SoundLibrary;
import greenfoot.sound.SoundMixer;
import greenfoot.sound.Voice;

import java.io.IOException;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Represents audio that can be played in Greenfoot. A GreenfootSound loads the audio from a file.
 * The sound cannot be played several times simultaneously, but can be played several times sequentially.
 * To play the same sound several times at once, create several GreenfootSound objects for the same file,
 * or use {@link Sounds#play(String)}, which does that for you.
 *
 * <p>Most files of the following formats are supported: AIFF, AU, WAV, MP3 and MIDI.
 *
 * <p>SuperGreenfoot: sounds are played through a software mixer, so any number of GreenfootSounds
 * can play at the same time, each with its own volume, stereo pan, playback rate and
 * {@link SoundCategory}. Small files are decoded once and shared; large files are streamed.
 *
 * @author Poul Henriksen
 * @version 2.4
 */
@OnThread(Tag.Any)
public class GreenfootSound
{
    private final String filename;
    /** Mixer-backed sound data, or null for MIDI. */
    private final SoundLibrary.Entry entry;
    /** Legacy player used for MIDI files only. */
    private final Sound legacy;

    private Voice voice;
    private int volume = 100;
    private double pan = 0;
    private double playbackRate = 1;
    private SoundCategory category = SoundCategory.EFFECTS;

    /**
     * Creates a new sound from the given file.
     *
     * @param filename Typically the name of a file in the sounds directory in
     *            the project directory.
     * @throws IllegalArgumentException If the sound cannot be found or loaded.
     */
    public GreenfootSound(String filename)
    {
        this.filename = filename;
        if (SoundLibrary.isMidi(filename)) {
            this.entry = null;
            this.legacy = SoundFactory.getInstance().createSound(filename, false);
        }
        else {
            this.entry = SoundLibrary.get(filename);
            this.legacy = null;
        }
    }

    /** Package-private: a sound that plays nothing (used when a Sounds key is unknown). */
    GreenfootSound(String name, boolean silent)
    {
        this.filename = name;
        this.entry = null;
        this.legacy = null;
    }

    /**
     * Start playing this sound. If it is playing already, it will do
     * nothing. If the sound is currently looping, it will finish the current
     * loop and stop. If the sound is currently paused, it will resume playback
     * from the point where it was paused. The sound will be played once.
     */
    public void play()
    {
        if (legacy != null) {
            legacy.play();
            return;
        }
        if (entry == null) {
            return;
        }
        if (voice != null && !voice.isFinished()) {
            if (voice.isPaused()) {
                voice.resume();
            }
            voice.setLoop(false);
            return;
        }
        startVoice(false);
    }

    /**
     * Play this sound repeatedly in a loop. If called on an already looping
     * sound, it will do nothing. If the sound is already playing once, it will
     * start looping instead. If the sound is currently paused, it will resume
     * playing from the point where it was paused.
     */
    public void playLoop()
    {
        if (legacy != null) {
            legacy.loop();
            return;
        }
        if (entry == null) {
            return;
        }
        if (voice != null && !voice.isFinished()) {
            if (voice.isPaused()) {
                voice.resume();
            }
            voice.setLoop(true);
            return;
        }
        startVoice(true);
    }

    /**
     * Stop playing this sound if it is currently playing. If the sound is
     * played again later, it will start playing from the beginning.
     */
    public void stop()
    {
        if (legacy != null) {
            legacy.stop();
            return;
        }
        if (voice != null) {
            voice.stop();
            voice = null;
        }
    }

    /**
     * Pauses the current sound if it is currently playing. If the sound is
     * played again later, it will resume from the point where it was paused.
     * <p>
     * Make sure that this is really the method you want. If possible, you
     * should always use {@link #stop()}, because the resources can be released
     * after calling {@link #stop()}. The resources for the sound will not be
     * released while it is paused.
     */
    public void pause()
    {
        if (legacy != null) {
            legacy.pause();
            return;
        }
        if (voice != null) {
            voice.pause();
        }
    }

    /**
     * True if the sound is currently playing.
     *
     * @return True if the sound is currently playing, false otherwise.
     */
    public boolean isPlaying()
    {
        if (legacy != null) {
            return legacy.isPlaying();
        }
        return voice != null && voice.isPlaying();
    }

    /**
     * True if the sound is currently paused.
     *
     * @return True if the sound is currently paused, false otherwise.
     * @since SuperGreenfoot 1.0
     */
    public boolean isPaused()
    {
        if (legacy != null) {
            return legacy.isPaused();
        }
        return voice != null && voice.isPaused();
    }

    /**
     * Get the current volume of the sound, between 0 (off) and 100 (loudest.)
     *
     * @return A number between 0-100 represents the current sound volume.
     */
    public int getVolume()
    {
        if (legacy != null) {
            return legacy.getVolume();
        }
        return volume;
    }

    /**
     * Set the current volume of the sound between 0 (off) and 100 (loudest.)
     *
     * @param level the level to set the sound volume to.
     */
    public void setVolume(int level)
    {
        if (legacy != null) {
            legacy.setVolume(level);
            return;
        }
        volume = Math.max(0, Math.min(100, level));
        if (voice != null) {
            voice.setGain(SoundMixer.levelToGain(volume));
        }
    }

    /**
     * Set the stereo position of this sound: -1 is fully left, 0 is centre
     * (the default) and 1 is fully right.
     *
     * @param pan The stereo position, from -1.0 to 1.0.
     * @since SuperGreenfoot 1.0
     */
    public void setPan(double pan)
    {
        this.pan = Math.max(-1, Math.min(1, pan));
        if (voice != null) {
            voice.setPan((float) this.pan);
        }
    }

    /**
     * @return The stereo position, from -1.0 (left) to 1.0 (right).
     * @since SuperGreenfoot 1.0
     */
    public double getPan()
    {
        return pan;
    }

    /**
     * Set the playback rate. 1.0 is normal speed; 2.0 plays twice as fast and
     * an octave higher; 0.5 plays at half speed and an octave lower. Slight
     * random variation (for example 0.9 to 1.1) makes repeated effects sound
     * less mechanical.
     *
     * @param rate The playback rate, from 0.05 to 16.
     * @since SuperGreenfoot 1.0
     */
    public void setPlaybackRate(double rate)
    {
        this.playbackRate = Math.max(0.05, Math.min(16, rate));
        if (voice != null) {
            voice.setRate((float) this.playbackRate);
        }
    }

    /**
     * @return The playback rate (1.0 is normal).
     * @since SuperGreenfoot 1.0
     */
    public double getPlaybackRate()
    {
        return playbackRate;
    }

    /**
     * Set the category of this sound, which selects which category volume and
     * mute setting in {@link Sounds} apply to it. The default is
     * {@link SoundCategory#EFFECTS}.
     *
     * @param category The category; must not be null.
     * @since SuperGreenfoot 1.0
     */
    public void setCategory(SoundCategory category)
    {
        if (category == null) {
            throw new NullPointerException("category");
        }
        this.category = category;
        if (voice != null) {
            voice.setCategory(category);
        }
    }

    /**
     * @return The category of this sound.
     * @since SuperGreenfoot 1.0
     */
    public SoundCategory getCategory()
    {
        return category;
    }

    /**
     * The name of the file this sound was loaded from.
     * @since SuperGreenfoot 1.0
     */
    public String getFilename()
    {
        return filename;
    }

    private void startVoice(boolean loop)
    {
        try {
            Voice v = new Voice(entry.newSource(), category, loop);
            v.setGain(SoundMixer.levelToGain(volume));
            v.setPan((float) pan);
            v.setRate((float) playbackRate);
            voice = v;
            SoundMixer.getInstance().add(v);
        }
        catch (IOException e) {
            System.err.println("Could not play sound " + filename + ": " + e.getMessage());
        }
    }

    /**
     * Returns a string representation of this sound containing the name of the
     * file and whether it is currently playing or not.
     */
    public String toString()
    {
        String s = super.toString() + " file: " + filename + " ";
        if (entry != null || legacy != null) {
            s += ". Is playing: " + isPlaying();
        }
        else {
            s += ". Not found.";
        }
        return s;
    }
}
