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

import greenfoot.core.Simulation;
import greenfoot.gui.WorldRenderer;
import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

import java.awt.image.BufferedImage;

/**
 * Tests that smooth rendering draws at fractional positions, while the default
 * mode stays pixel-identical to upstream Greenfoot.
 */
public class SmoothRenderTest extends TestCase
{
    private static final int RED = 0xFFFF0000;
    private static final int WHITE = 0xFFFFFFFF;

    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        Simulation.initialize();
    }

    private static BufferedImage render(World world)
    {
        BufferedImage img = new BufferedImage(world.getWidthInPixels(), world.getHeightInPixels(),
                BufferedImage.TYPE_INT_ARGB);
        new WorldRenderer().renderWorld(world, img);
        return img;
    }

    private static TestObject redSquare(int size)
    {
        TestObject o = new TestObject(size, size);
        GreenfootImage img = o.getImage();
        img.setColor(Color.RED);
        img.fill();
        return o;
    }

    private static World whiteWorld()
    {
        World world = WorldCreator.createWorld(30, 30, 1);
        GreenfootImage bg = new GreenfootImage(30, 30);
        bg.setColor(Color.WHITE);
        bg.fill();
        world.setBackground(bg);
        return world;
    }

    public void testDefaultModeDrawsAtWholePixels()
    {
        World world = whiteWorld();
        TestObject o = redSquare(4);
        world.addObject(o, 0, 0);
        o.setLocation(10.25, 10.0);   // rounds to cell 10

        BufferedImage img = render(world);
        // Cell 10 with a 4-wide image: centre 10.5, painted at floor(8.5) = 8 .. 11
        int y = 10;
        assertEquals(WHITE, img.getRGB(7, y));
        for (int x = 8; x <= 11; x++) {
            assertEquals("x=" + x, RED, img.getRGB(x, y));
        }
        assertEquals(WHITE, img.getRGB(12, y));
    }

    public void testSmoothModeDrawsAtFractionalPixels()
    {
        World world = whiteWorld();
        world.setSmoothRendering(true);
        TestObject o = redSquare(4);
        world.addObject(o, 0, 0);
        o.setLocation(10.25, 10.0);

        BufferedImage img = render(world);
        // Whole-cell placement would paint at column 8 (see testDefaultModeDrawsAtWholePixels);
        // the 0.25 fractional part shifts that to 8.25 .. 12.25, so columns 8 and 12
        // are partially covered (blended) and 9..11 fully red.
        int y = 10;
        int left = img.getRGB(8, y);
        int right = img.getRGB(12, y);
        assertTrue("left edge should be blended, was " + Integer.toHexString(left), left != WHITE && left != RED);
        assertTrue("right edge should be blended, was " + Integer.toHexString(right), right != WHITE && right != RED);
        for (int x = 9; x <= 11; x++) {
            assertEquals("x=" + x, RED, img.getRGB(x, y));
        }
        assertEquals(WHITE, img.getRGB(7, y));
        assertEquals(WHITE, img.getRGB(13, y));
        // The left edge has more coverage (0.75) than the right (0.25), so it is redder
        int leftGreen = (left >> 8) & 0xFF;
        int rightGreen = (right >> 8) & 0xFF;
        assertTrue(leftGreen < rightGreen);
    }

    public void testSmoothModeAtWholePixelsMatchesDefault()
    {
        World world = whiteWorld();
        TestObject o = redSquare(4);
        world.addObject(o, 0, 0);
        o.setLocation(10, 10);
        BufferedImage plain = render(world);
        world.setSmoothRendering(true);
        BufferedImage smooth = render(world);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                assertEquals("pixel " + x + "," + y, plain.getRGB(x, y), smooth.getRGB(x, y));
            }
        }
    }

    public void testLockedImageRotationIsWhatIsDrawn()
    {
        World world = whiteWorld();
        // 8x2 red bar: horizontal when image rotation is 0
        TestObject o = new TestObject(8, 2);
        o.getImage().setColor(Color.RED);
        o.getImage().fill();
        world.addObject(o, 15, 15);
        o.setRotation(90);
        o.setImageRotation(0);

        BufferedImage img = render(world);
        // Drawn horizontally: pixels left and right of centre are red, above/below are white
        assertEquals(RED, img.getRGB(12, 15));
        assertEquals(RED, img.getRGB(18, 15));
        assertEquals(WHITE, img.getRGB(15, 12));
        assertEquals(WHITE, img.getRGB(15, 18));
    }
}
