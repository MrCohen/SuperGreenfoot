/*
 This file is part of the Greenfoot program.
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

/**
 * How the world image is scaled when it is shown larger than its own size,
 * for example in full screen or in an enlarged player window.
 *
 * @see Greenfoot#setScaleMode(ScaleMode)
 * @since SuperGreenfoot 1.0
 */
public enum ScaleMode
{
    /**
     * Scale by a whole number only (1x, 2x, 3x...) with no smoothing, so every
     * world pixel becomes a crisp square block. Leaves black borders when the
     * world does not divide the screen evenly.
     */
    PIXEL_PERFECT,

    /**
     * Scale to fill as much of the screen as possible while keeping the aspect
     * ratio, smoothing the pixels. Fills the screen but can look soft when the
     * factor is not a whole number.
     */
    SMOOTH
}
