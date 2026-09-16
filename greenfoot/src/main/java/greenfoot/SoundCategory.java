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

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Categories of sound, each with its own volume and mute control in
 * {@link Sounds}. A sound's final loudness is its own volume, times the volume
 * of its category, times the master volume.
 *
 * @since SuperGreenfoot 1.0
 */
@OnThread(Tag.Any)
public enum SoundCategory
{
    /** Short sound effects: jumps, hits, pickups. The default category. */
    EFFECTS,

    /** Background music. {@link Sounds#playMusic(String)} uses this category. */
    MUSIC,

    /** Spoken lines and narration. */
    VOICE,

    /** Looping ambience such as wind, rain or crowd noise. */
    AMBIENT
}
