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

/**
 * A source of audio frames for the mixer: interleaved stereo floats in the
 * range -1..1 at {@link AudioDecoder#MIX_RATE}. Implementations are either
 * an in-memory clip or a stream decoded on demand.
 *
 * <p>All methods are called from the mixer thread only.
 */
public interface SampleSource
{
    /**
     * Read up to {@code frames} stereo frames into {@code dst} starting at
     * frame offset {@code dstFrameOffset} (i.e. array index 2*offset).
     *
     * @return the number of frames read, or 0 at end of data.
     */
    int read(float[] dst, int dstFrameOffset, int frames) throws IOException;

    /** Rewind to the beginning (used for looping). */
    void reset() throws IOException;

    /** Release any resources. The source is not used again afterwards. */
    void close();
}
