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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Resolves sound file names to decoded clips (small files, cached and shared)
 * or streamed sources (large files), so that playing the same effect many
 * times decodes it once.
 */
public final class SoundLibrary
{
    /** Files larger than this many bytes are streamed instead of decoded into memory. */
    public static final long MAX_CLIP_BYTES = 1_000_000L;

    private static final Map<String, Entry> entries = new HashMap<String, Entry>();

    private SoundLibrary()
    {
    }

    /** A resolved sound file. */
    public static final class Entry
    {
        public final String filename;
        public final URL url;
        /** Decoded data, or null if the file is streamed. */
        private final PcmClip clip;

        Entry(String filename, URL url, PcmClip clip)
        {
            this.filename = filename;
            this.url = url;
            this.clip = clip;
        }

        public boolean isStreamed()
        {
            return clip == null;
        }

        /** A fresh read position for a new voice. */
        public SampleSource newSource() throws IOException
        {
            if (clip != null) {
                return clip.newSource();
            }
            try {
                return new StreamSource(url);
            }
            catch (UnsupportedAudioFileException e) {
                throw new IOException(e);
            }
        }

        /** Duration in seconds if known (clips), else -1. */
        public double getSeconds()
        {
            return clip != null ? clip.getSeconds() : -1;
        }
    }

    /**
     * Resolve and, for small files, decode a sound.
     *
     * @throws IllegalArgumentException if the file cannot be found or decoded.
     */
    public static synchronized Entry get(String filename)
    {
        Entry e = entries.get(filename);
        if (e != null) {
            return e;
        }
        URL url;
        try {
            url = GreenfootUtil.getURL(filename, "sounds");
        }
        catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("Could not find sound file: " + filename);
        }
        long size;
        try {
            size = url.openConnection().getContentLengthLong();
        }
        catch (IOException ex) {
            size = -1;
        }
        PcmClip clip = null;
        if (size >= 0 && size <= MAX_CLIP_BYTES) {
            try {
                clip = PcmClip.decode(url);
            }
            catch (IOException | UnsupportedAudioFileException ex) {
                throw new IllegalArgumentException("Could not load sound file " + filename + ": " + ex.getMessage(), ex);
            }
        }
        else {
            // Validate now so errors surface at construction, not on the mixer thread.
            try {
                AudioDecoder.open(url).close();
            }
            catch (IOException | UnsupportedAudioFileException ex) {
                throw new IllegalArgumentException("Could not load sound file " + filename + ": " + ex.getMessage(), ex);
            }
        }
        e = new Entry(filename, url, clip);
        entries.put(filename, e);
        return e;
    }

    /** Drop all cached clips (tests). */
    public static synchronized void clear()
    {
        entries.clear();
    }

    /** Whether the name refers to a MIDI file, which the mixer does not handle. */
    public static boolean isMidi(String filename)
    {
        String l = filename.toLowerCase();
        return l.endsWith(".mid") || l.endsWith(".midi");
    }
}
