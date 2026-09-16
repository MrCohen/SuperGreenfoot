/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2016  Poul Henriksen and Michael Kolling
 
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
 * A representation of a Font. The Font can be used to write text on the screen.
 *
 * @author Fabio Heday
 * @author Amjad Altadmri
 */
public class Font
{

    private final java.awt.Font font;

    /**
     * Creates a Greenfoot font based on a java.awt.Font
     *
     * @param font
     */
    Font(java.awt.Font font)
    {
        this.font = font;
    }

    /**
     * Creates a font from the specified font name, size and style.
     *
     * @param name The font name
     * @param bold True if the font is meant to be bold
     * @param italic True if the font is meant to be italic
     * @param size The size of the font
     */
    public Font(String name, boolean bold, boolean italic, int size)
    {
        int style = java.awt.Font.PLAIN;
        if (bold) {
            style = java.awt.Font.BOLD;
        }
        if (italic) {
            style = style | java.awt.Font.ITALIC;
        }
        this.font = new java.awt.Font(name, style, size);
    }

    /**
     * Creates a sans serif font with the specified size and style.
     *
     * @param bold True if the font is meant to be bold
     * @param italic True if the font is meant to be italic
     * @param size The size of the font
     */
    public Font(boolean bold, boolean italic, int size)
    {
        this("SansSerif", bold, italic, size);
    }

    /**
     * Creates a font from the specified font name and size.
     *
     * @param name The font name
     * @param size The size of the font
     */
    public Font(String name, int size)
    {
        this(name, false, false, size);
    }

    /**
     * Creates a sans serif font of a given size.
     *
     * @param size The size of the font
     */
    public Font(int size)
    {
        this(false, false, size);
    }

    /**
     * Indicates whether or not this Font style is plain.
     *
     * @return true if this font style is plain; false otherwise
     */
    public boolean isPlain()
    {
        return this.font.isPlain();
    }

    /**
     * Indicates whether or not this Font style is bold.
     *
     * @return true if this font style is bold; false otherwise
     */
    public boolean isBold()
    {
        return this.font.isBold();
    }

    /**
     * Indicates whether or not this Font style is italic.
     *
     * @return true if this font style is italic; false otherwise
     */
    public boolean isItalic()
    {
        return this.font.isItalic();
    }

    /**
     * Returns the logical name of this font.
     *
     * @return a <code>String</code> representing the logical name of this font.
     */
    public String getName()
    {
        return this.font.getName();
    }

    /**
     * Returns the point size of this font, rounded to an integer.
     *
     * @return the point size of this font in 1/72 of an inch units.
     */
    public int getSize()
    {
        return this.font.getSize();
    }

    /**
     * Creates a new <code>Font</code> object by cloning the current
     * one and then applying a new size to it.
     *
     * @param size the size for the new <code>Font</code>.
     * @return a new <code>Font</code> object.
     */
    public Font deriveFont(float size)
    {
        return new Font(font.deriveFont(size));
    }

    // ==================================
    //
    // SuperGreenfoot: text measurement
    //
    // ==================================

    /**
     * Shared graphics context used only for measuring text. Its rendering hints match
     * those used by GreenfootImage.drawString so that measurements agree with drawing.
     */
    private static java.awt.Graphics2D measureGraphics;

    private static synchronized java.awt.Graphics2D getMeasureGraphics()
    {
        if (measureGraphics == null) {
            java.awt.image.BufferedImage scratch =
                    new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            measureGraphics = scratch.createGraphics();
            measureGraphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        return measureGraphics;
    }

    /**
     * Visual (ink) bounds of one line of text, relative to the baseline origin.
     */
    private java.awt.geom.Rectangle2D inkBounds(String line)
    {
        if (line.isEmpty()) {
            return new java.awt.geom.Rectangle2D.Double();
        }
        java.awt.Graphics2D g = getMeasureGraphics();
        synchronized (Font.class) {
            return font.createGlyphVector(g.getFontRenderContext(), line).getVisualBounds();
        }
    }

    /**
     * Return the width in pixels of the ink of the given text when drawn in this font:
     * the distance from the leftmost drawn pixel to the rightmost. For text containing
     * newlines, the widest line is returned. Use this to centre or right-align text
     * before calling {@link GreenfootImage#drawString(String, int, int)}.
     *
     * @param text The text to measure.
     * @return The width of the drawn text in pixels (0 for empty text).
     * @since SuperGreenfoot 1.0
     */
    public int getStringWidth(String text)
    {
        double max = 0;
        for (String line : text.split("\n", -1)) {
            max = Math.max(max, inkBounds(line).getWidth());
        }
        return (int) Math.ceil(max);
    }

    /**
     * Return the height in pixels of the ink of the given text when drawn in this font:
     * the distance from the topmost drawn pixel to the bottommost, including descenders
     * such as the tail of a "g". Text containing newlines is measured as drawn by
     * {@link GreenfootImage#drawString(String, int, int)}, one line height apart.
     *
     * @param text The text to measure.
     * @return The height of the drawn text in pixels (0 for empty text).
     * @since SuperGreenfoot 1.0
     */
    public int getStringHeight(String text)
    {
        String[] lines = text.split("\n", -1);
        int lineHeight = getLineHeight();
        double top = Double.POSITIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < lines.length; i++) {
            java.awt.geom.Rectangle2D r = inkBounds(lines[i]);
            if (r.getWidth() == 0 && r.getHeight() == 0) {
                continue;
            }
            top = Math.min(top, r.getMinY() + i * lineHeight);
            bottom = Math.max(bottom, r.getMaxY() + i * lineHeight);
        }
        if (top > bottom) {
            return 0;
        }
        return (int) Math.ceil(bottom - top);
    }

    /**
     * Return the ascent of this font: the distance in pixels from the baseline up to
     * the top of the tallest characters (such as capital letters and "l").
     *
     * @return The ascent in pixels.
     * @since SuperGreenfoot 1.0
     */
    public int getAscent()
    {
        return getMeasureGraphics().getFontMetrics(font).getAscent();
    }

    /**
     * Return the descent of this font: the distance in pixels from the baseline down
     * to the bottom of characters with descenders (such as "g", "p" and "y").
     *
     * @return The descent in pixels.
     * @since SuperGreenfoot 1.0
     */
    public int getDescent()
    {
        return getMeasureGraphics().getFontMetrics(font).getDescent();
    }

    /**
     * Return the line height of this font: the vertical distance in pixels between the
     * baselines of two consecutive lines drawn with
     * {@link GreenfootImage#drawString(String, int, int)}.
     *
     * @return The line height in pixels.
     * @since SuperGreenfoot 1.0
     */
    public int getLineHeight()
    {
        return getMeasureGraphics().getFontMetrics(font).getHeight();
    }

    /**
     * Height in pixels of capital letters and ascenders above the baseline, measured
     * from ink. Used for visually centring text.
     */
    int getCapAscent()
    {
        return (int) Math.ceil(-inkBounds("ABCTXbdfhkl").getMinY());
    }

    /**
     * Left offset of the ink of the given line relative to the drawing origin (can be
     * non-zero for italic or overhanging glyphs). Package-private, used for centring.
     */
    int getInkLeft(String line)
    {
        return (int) Math.floor(inkBounds(line).getMinX());
    }

    /**
     * * Determines whether another object is equal to this font.
     *
     * @param obj the object to test for equality with this font
     * @return true if the fonts are the same; false otherwise.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == this) {
            return true;
        }

        return (obj != null) && (obj instanceof Font) && ((Font) obj).getFontObject().equals(this.font);
    }

    /**
     * Returns a hashcode for this font.
     *
     * @return a hashcode value for this font.
     */
    @Override
    public int hashCode()
    {
        return font.hashCode();
    }

    /**
     * Return a text representation of the font.
     * @return Details of the font
     */
    @Override
    public String toString()
    {
        return "Font{" + "font=" + font + '}';
    }

    /**
     * Return the internal Font object representing the Greenfoot.Font.
     *
     * @return the java.awt.Font object
     */
    java.awt.Font getFontObject()
    {
        return this.font;
    }

}
