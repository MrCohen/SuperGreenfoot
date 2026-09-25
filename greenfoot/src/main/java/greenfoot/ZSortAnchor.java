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
 * Which part of an actor's image sits on the ground, for the y-sorting turned on by
 * {@link World#setZSortByY(boolean)}.
 *
 * <p>Y-sorting paints actors lower on the screen in front of actors higher up, which
 * is what gives a top-down game its depth. The question this answers is which point
 * of a tall picture counts as "lower": the middle of the image, or the feet.
 *
 * @see World#setZSortAnchor(ZSortAnchor)
 * @see Actor#setZSortOffset(double)
 * @since SuperGreenfoot 0.2.0
 */
public enum ZSortAnchor
{
    /**
     * Sort by the actor's own position, which is the middle of its image. This is
     * the default, and how y-sorting has always behaved.
     *
     * <p>A tall picture then has to be padded with empty rows below it until its feet
     * reach the middle, or the tree will be drawn in front of things standing beside
     * its trunk. That padding costs memory: a picture with nothing below its feet ends
     * up twice as tall as it needs to be.
     */
    CENTER,

    /**
     * Sort by the bottom edge of the actor's image, so a picture can be exactly as
     * tall as what it draws and still stand correctly among its neighbours.
     *
     * <p>The height is read at every sort, so changing an actor's image - a walk cycle,
     * a tree becoming a stump - needs nothing else. Where the feet are not quite at the
     * bottom edge, {@link Actor#setZSortOffset(double)} moves the line.
     */
    BOTTOM
}
