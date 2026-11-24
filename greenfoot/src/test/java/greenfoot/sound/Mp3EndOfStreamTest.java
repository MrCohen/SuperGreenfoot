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

import junit.framework.TestCase;

import java.net.URL;

/**
 * An MP3 ending in an ID3v1 tag whose last byte (the genre) is 0xFF: JLayer
 * reads its last frame and then has no more, but asked for another it reads
 * stale bytes as a frame and throws ArrayIndexOutOfBoundsException. A
 * student's sound like this (a 13-second countdown) made their world fail
 * to construct, since a GreenfootSound decodes its whole file when made.
 * The fixture is a generated 440 Hz tone, one second, with such a tag.
 */
public class Mp3EndOfStreamTest extends TestCase
{
    @Override
    protected void setUp()
    {
        // AudioDecoder checks the MP3 library is there through the delegate
        greenfoot.util.GreenfootUtil.initialise(new greenfoot.TestUtilDelegate());
    }

    private URL fixture()
    {
        URL url = getClass().getResource("id3v1-genre-ff.mp3");
        assertNotNull("test resource missing", url);
        return url;
    }

    public void testReadingPastTheEndGivesMinusOneNotAnException() throws Exception
    {
        Mp3AudioInputStream in = new Mp3AudioInputStream(fixture());
        byte[] b = new byte[16384];
        long total = 0;
        int n;
        while ((n = in.read(b, 0, b.length)) > 0) {
            total += n;
        }
        assertEquals("the end is -1", -1, n);
        assertTrue("the tone was read: " + total, total > 100_000);
        assertEquals("and stays -1", -1, in.read(b, 0, b.length));
        in.close();
    }

    public void testRestartReadsTheFileAgain() throws Exception
    {
        Mp3AudioInputStream in = new Mp3AudioInputStream(fixture());
        byte[] b = new byte[4608];
        long first = 0;
        int n;
        while ((n = in.read(b, 0, b.length)) > 0) {
            first += n;
        }
        in.restart();
        long second = 0;
        while ((n = in.read(b, 0, b.length)) > 0) {
            second += n;
        }
        assertEquals(first, second);
        in.close();
    }

    public void testTheWholeFileDecodesForTheMixer() throws Exception
    {
        PcmClip clip = PcmClip.decode(fixture());
        assertTrue("about a second: " + clip.getSeconds(), clip.getSeconds() > 0.9 && clip.getSeconds() < 1.1);
    }
}
