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

import greenfoot.sound.SoundMixer;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A scenario-wide sound manager. Sounds are registered once under a short
 * key, then played from anywhere (any world, any actor) without passing
 * objects around. Playing a key that is already playing starts another copy,
 * so effects overlap naturally.
 *
 * <pre>
 *   // in the first world's constructor (or a static initialiser):
 *   Sounds.load("jump", "jump.wav");
 *   Sounds.load("theme", "theme.mp3", SoundCategory.MUSIC);
 *
 *   // anywhere:
 *   Sounds.play("jump");
 *   Sounds.playMusic("theme");
 *   Sounds.setCategoryVolume(SoundCategory.MUSIC, 40);
 * </pre>
 *
 * <p>Keys that were never loaded can still be played if they are file names
 * (they are loaded on first use), so {@code Sounds.play("jump.wav")} works
 * without any setup. Playing an unknown key that is not a file name prints a
 * warning and returns a silent sound rather than crashing the scenario.
 *
 * <p>Volume has three levels that multiply together: the individual sound
 * (0-100), its {@link SoundCategory} (0-100, plus mute), and the master
 * volume (0-100). Sounds played while the scenario is paused are held and
 * resume when it runs again.
 *
 * @since SuperGreenfoot 1.0
 */
@OnThread(Tag.Any)
public final class Sounds
{
    /** Default cap on simultaneous copies of one key; the oldest is stopped when exceeded. */
    public static final int DEFAULT_MAX_VOICES = 8;

    @OnThread(Tag.Any)
    private static final class Entry
    {
        final String filename;
        SoundCategory category;
        int volume;
        int maxVoices = DEFAULT_MAX_VOICES;
        final List<GreenfootSound> active = new ArrayList<GreenfootSound>();

        Entry(String filename, SoundCategory category, int volume)
        {
            this.filename = filename;
            this.category = category;
            this.volume = volume;
        }

        void prune()
        {
            for (Iterator<GreenfootSound> it = active.iterator(); it.hasNext();) {
                GreenfootSound s = it.next();
                if (!s.isPlaying() && !s.isPaused()) {
                    it.remove();
                }
            }
        }
    }

    private static final Map<String, Entry> entries = new HashMap<String, Entry>();
    private static final Set<String> warned = new HashSet<String>();
    private static GreenfootSound currentMusic;
    private static final List<GreenfootSound> pausedByPauseAll = new ArrayList<GreenfootSound>();

    private Sounds()
    {
    }

    // ---- loading ----

    /**
     * Register a sound file under a key, in the {@link SoundCategory#EFFECTS} category.
     * The file is decoded now so that the first play is instant.
     *
     * @param key A short name to play the sound by, e.g. "jump".
     * @param filename A file in the scenario's sounds folder, e.g. "jump.wav".
     * @throws IllegalArgumentException If the file cannot be found or decoded.
     */
    public static void load(String key, String filename)
    {
        load(key, filename, SoundCategory.EFFECTS, 100);
    }

    /**
     * Register a sound file under a key in the given category.
     *
     * @see #load(String, String)
     */
    public static void load(String key, String filename, SoundCategory category)
    {
        load(key, filename, category, 100);
    }

    /**
     * Register a sound file under a key with a category and a base volume (0-100)
     * that every play of this key starts at.
     *
     * @see #load(String, String)
     */
    public static synchronized void load(String key, String filename, SoundCategory category, int volume)
    {
        if (key == null || filename == null || category == null) {
            throw new NullPointerException("key, filename and category must not be null");
        }
        // Decode (and validate) now; throws IllegalArgumentException if missing.
        new GreenfootSound(filename);
        Entry e = entries.get(key);
        if (e != null) {
            stop(key);
        }
        entries.put(key, new Entry(filename, category, clamp(volume)));
    }

    /**
     * @return true if the key has been loaded (or auto-loaded from a file name).
     */
    public static synchronized boolean isLoaded(String key)
    {
        return entries.containsKey(key);
    }

    // ---- playing ----

    /**
     * Play the sound once. If it is already playing, another copy starts, so
     * rapid repeats overlap. Up to {@link #setMaxVoices(String, int)} copies
     * play at once; beyond that the oldest is stopped.
     *
     * @param key A loaded key, or a file name to load on first use.
     * @return The playing sound, so you can stop or adjust it. Never null.
     */
    public static GreenfootSound play(String key)
    {
        return start(key, -1, 0, false);
    }

    /**
     * Play the sound once at the given volume (0-100).
     * @see #play(String)
     */
    public static GreenfootSound play(String key, int volume)
    {
        return start(key, clamp(volume), 0, false);
    }

    /**
     * Play the sound once at the given volume (0-100) and stereo pan
     * (-1.0 left, 0 centre, 1.0 right).
     * @see #play(String)
     */
    public static GreenfootSound play(String key, int volume, double pan)
    {
        return start(key, clamp(volume), pan, false);
    }

    /**
     * Play the sound repeatedly until stopped.
     * @see #play(String)
     */
    public static GreenfootSound playLoop(String key)
    {
        return start(key, -1, 0, true);
    }

    /**
     * Play background music: the sound loops in the {@link SoundCategory#MUSIC}
     * category, and any music started earlier with this method is stopped first.
     * If the key is not loaded it is loaded from the file name.
     *
     * @param key A loaded key or a file name, e.g. "theme.mp3".
     * @return The playing music.
     */
    public static synchronized GreenfootSound playMusic(String key)
    {
        stopMusic();
        Entry e = resolve(key);
        if (e == null) {
            return silent(key);
        }
        e.category = SoundCategory.MUSIC;
        GreenfootSound s = start(key, -1, 0, true);
        currentMusic = s;
        return s;
    }

    /** Stop the music started with {@link #playMusic(String)}, if any. */
    public static synchronized void stopMusic()
    {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic = null;
        }
    }

    /**
     * @return The music currently started with {@link #playMusic(String)}, or null.
     */
    public static synchronized GreenfootSound getMusic()
    {
        return currentMusic;
    }

    /** Stop every playing copy of the key. */
    public static synchronized void stop(String key)
    {
        Entry e = entries.get(key);
        if (e == null) {
            return;
        }
        for (GreenfootSound s : e.active) {
            s.stop();
        }
        e.active.clear();
    }

    /** Stop every sound started through this class, including music. */
    public static synchronized void stopAll()
    {
        for (Entry e : entries.values()) {
            for (GreenfootSound s : e.active) {
                s.stop();
            }
            e.active.clear();
        }
        currentMusic = null;
        pausedByPauseAll.clear();
    }

    /** Pause every sound started through this class; {@link #resumeAll()} continues them. */
    public static synchronized void pauseAll()
    {
        for (Entry e : entries.values()) {
            e.prune();
            for (GreenfootSound s : e.active) {
                if (s.isPlaying()) {
                    s.pause();
                    pausedByPauseAll.add(s);
                }
            }
        }
    }

    /** Resume the sounds paused by {@link #pauseAll()}. */
    public static synchronized void resumeAll()
    {
        for (GreenfootSound s : pausedByPauseAll) {
            if (s.isPaused()) {
                s.play();
            }
        }
        pausedByPauseAll.clear();
    }

    /** @return true if any copy of the key is currently playing. */
    public static synchronized boolean isPlaying(String key)
    {
        Entry e = entries.get(key);
        if (e == null) {
            return false;
        }
        e.prune();
        for (GreenfootSound s : e.active) {
            if (s.isPlaying()) {
                return true;
            }
        }
        return false;
    }

    /** @return the number of copies of the key currently playing or paused. */
    public static synchronized int getActiveCount(String key)
    {
        Entry e = entries.get(key);
        if (e == null) {
            return 0;
        }
        e.prune();
        return e.active.size();
    }

    // ---- per-key settings ----

    /**
     * Limit how many copies of a key may play at once (default 8). When the
     * limit is reached, the oldest copy is stopped to make room.
     */
    public static synchronized void setMaxVoices(String key, int max)
    {
        Entry e = resolve(key);
        if (e != null) {
            e.maxVoices = Math.max(1, max);
        }
    }

    /**
     * Set the base volume (0-100) for a key: the volume future plays start at,
     * and the volume of copies already playing.
     */
    public static synchronized void setVolume(String key, int volume)
    {
        Entry e = resolve(key);
        if (e != null) {
            e.volume = clamp(volume);
            for (GreenfootSound s : e.active) {
                s.setVolume(e.volume);
            }
        }
    }

    /** Set the category of a key (applies to future plays and current copies). */
    public static synchronized void setCategory(String key, SoundCategory category)
    {
        Entry e = resolve(key);
        if (e != null && category != null) {
            e.category = category;
            for (GreenfootSound s : e.active) {
                s.setCategory(category);
            }
        }
    }

    // ---- global volume ----

    /** Set the master volume (0-100) applied to all sounds. */
    public static void setMasterVolume(int volume)
    {
        SoundMixer.getInstance().setMasterLevel(clamp(volume));
    }

    /** @return the master volume (0-100). */
    public static int getMasterVolume()
    {
        return SoundMixer.getInstance().getMasterLevel();
    }

    /** Set the volume (0-100) of one category, e.g. turn music down to 40. */
    public static void setCategoryVolume(SoundCategory category, int volume)
    {
        SoundMixer.getInstance().setCategoryLevel(category, clamp(volume));
    }

    /** @return the volume (0-100) of the category. */
    public static int getCategoryVolume(SoundCategory category)
    {
        return SoundMixer.getInstance().getCategoryLevel(category);
    }

    /** Mute or unmute a whole category without changing its volume. */
    public static void setMuted(SoundCategory category, boolean muted)
    {
        SoundMixer.getInstance().setCategoryMuted(category, muted);
    }

    /** @return true if the category is muted. */
    public static boolean isMuted(SoundCategory category)
    {
        return SoundMixer.getInstance().isCategoryMuted(category);
    }

    // ---- internals ----

    private static int clamp(int v)
    {
        return Math.max(0, Math.min(100, v));
    }

    /** Find the entry, auto-loading file names; null (after a warning) for unknown keys. */
    private static synchronized Entry resolve(String key)
    {
        Entry e = entries.get(key);
        if (e != null) {
            return e;
        }
        if (key != null && key.contains(".")) {
            try {
                load(key, key);
                return entries.get(key);
            }
            catch (IllegalArgumentException ex) {
                warnOnce(key, ex.getMessage());
                return null;
            }
        }
        warnOnce(key, "no sound loaded under the key \"" + key + "\" (use Sounds.load(key, file) first)");
        return null;
    }

    private static void warnOnce(String key, String message)
    {
        if (warned.add(String.valueOf(key))) {
            System.err.println("Sounds: " + message);
        }
    }

    private static GreenfootSound silent(String key)
    {
        return new GreenfootSound(String.valueOf(key), true);
    }

    private static synchronized GreenfootSound start(String key, int volume, double pan, boolean loop)
    {
        Entry e = resolve(key);
        if (e == null) {
            return silent(key);
        }
        e.prune();
        while (e.active.size() >= e.maxVoices) {
            e.active.remove(0).stop();
        }
        GreenfootSound s = new GreenfootSound(e.filename);
        s.setCategory(e.category);
        s.setVolume(volume < 0 ? e.volume : volume);
        s.setPan(pan);
        if (loop) {
            s.playLoop();
        }
        else {
            s.play();
        }
        e.active.add(s);
        return s;
    }
}
