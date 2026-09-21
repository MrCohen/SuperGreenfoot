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
package greenfoot.templates;

import greenfoot.GreenfootImage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * What the thumbnail scenes (src/test/templates/TemplateThumbnails.java) need beyond the
 * Greenfoot API: GreenfootImage.scale is nearest-neighbour, which makes the small
 * imagelib pictures blocky when a scene draws them at thumbnail scale.
 */
public class ThumbnailSupport
{
    /** Load a picture from the Greenfoot image library, e.g. "animals/wombat.png", scaled smoothly. */
    public static GreenfootImage libraryImage(String name, double scale)
    {
        return scaleSmoothly(new GreenfootImage("imagelib/" + name), scale);
    }

    /** A smoothly scaled copy of the image. */
    public static GreenfootImage scaleSmoothly(GreenfootImage image, double scale)
    {
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        GreenfootImage scaled = new GreenfootImage(width, height);
        Graphics2D g = scaled.getAwtImage().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image.getAwtImage(), 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }
}
