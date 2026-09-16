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
 * A sample source that decodes a (long) sound file on demand rather than
 * holding it in memory. Used for music and other large files. Looping
 * reopens the file.
 */
public final class StreamSource implements SampleSource
{
    private final URL url;
    private AudioDecoder.PcmReader reader;

    public StreamSource(URL url) throws IOException, UnsupportedAudioFileException
    {
        this.url = url;
        this.reader = AudioDecoder.open(url);
    }

    @Override
    public int read(float[] dst, int dstFrameOffset, int frames) throws IOException
    {
        if (reader == null) {
            return 0;
        }
        return reader.read(dst, dstFrameOffset, frames);
    }

    @Override
    public void reset() throws IOException
    {
        close();
        try {
            reader = AudioDecoder.open(url);
        }
        catch (UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close()
    {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }
}
