/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2005-2023 Poul Henriksen and Michael Kolling
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
 * Gives classes outside the greenfoot package (the renderer) access to the
 * package-private geometry of a SuperWindow without exposing it in the public API.
 */
public class WindowVisitor
{
    /** World pixel x of the visible content area's left edge. */
    public static int getContentLeftPx(SuperWindow w)
    {
        return w.contentLeftPx();
    }

    /** World pixel y of the visible content area's top edge. */
    public static int getContentTopPx(SuperWindow w)
    {
        return w.contentTopPx();
    }

    public static int getContentWidthPx(SuperWindow w)
    {
        return w.contentWidthPx();
    }

    public static int getContentHeightPx(SuperWindow w)
    {
        return w.contentHeightPx();
    }

    /** World pixel x of the window's local cell 0 (after scrolling). */
    public static int getContentOriginPixelX(SuperWindow w)
    {
        return w.contentOriginPixelX();
    }

    /** World pixel y of the window's local cell 0 (after scrolling). */
    public static int getContentOriginPixelY(SuperWindow w)
    {
        return w.contentOriginPixelY();
    }

    /** The content background image, or null if none was created. */
    public static GreenfootImage getBackgroundImage(SuperWindow w)
    {
        return w.getBackgroundNoInit();
    }

    /** The scroll bar overlay, or null if the window needs none. */
    public static GreenfootImage getScrollBarImage(SuperWindow w)
    {
        return w.getScrollBarImage();
    }
}
