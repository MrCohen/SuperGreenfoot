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
import greenfoot.*;

/**
 * Behaviour checks for the Import Class templates, written as scenario code (default
 * package, like the templates themselves once imported). ImportableTemplatesTest compiles
 * this with the templates and runs every public static method whose name starts with
 * "check"; a check fails by throwing.
 */
public class TemplateChecks
{
    // ----- TextBox -----

    public static void checkTextBoxFitsItsText()
    {
        TextBox shortBox = new TextBox("Hi", 24);
        TextBox longBox = new TextBox("Hello there, world", 24);
        expect(longBox.getImage().getWidth() > shortBox.getImage().getWidth(), "longer text makes a wider box");
        expect(longBox.getImage().getHeight() == shortBox.getImage().getHeight(), "one line is one line tall");

        shortBox.setText("Hello there, world");
        expect(shortBox.getImage().getWidth() == longBox.getImage().getWidth(), "setText refits the box");
    }

    public static void checkTextBoxLines()
    {
        Font font = new Font(20);
        TextBox one = new TextBox("a", font);
        TextBox three = new TextBox(new String[] { "a", "b", "c" }, font);
        TextBox newlines = new TextBox("a\nb\nc", font);
        expect(three.getImage().getHeight() > one.getImage().getHeight(), "three lines are taller than one");
        expect(newlines.getImage().getHeight() == three.getImage().getHeight(), "\\n makes lines like an array does");
        expect(newlines.getText().equals("a\nb\nc"), "getText joins the lines: " + newlines.getText());
    }

    public static void checkTextBoxManualSize()
    {
        TextBox box = new TextBox("Hi", 24);
        int fitWidth = box.getImage().getWidth();
        int fitHeight = box.getImage().getHeight();

        box.setWidth(300);
        expect(box.getImage().getWidth() == 300, "setWidth fixes the width");
        box.setWidth(TextBox.AUTO);
        expect(box.getImage().getWidth() == fitWidth, "AUTO fits the width again");

        box.setHeight(200);
        expect(box.getImage().getHeight() == 200, "setHeight fixes the height");
        box.setHeight(5);
        expect(box.getImage().getHeight() == fitHeight, "the box is never shorter than its text");

        box.setPadding(0);
        box.setColors(Color.BLACK, null, null);
        box.setHeight(TextBox.AUTO);
        int[] ink = inkBounds(box.getImage());
        int inkWidth = ink[2] - ink[0] + 1;
        expect(Math.abs(box.getImage().getWidth() - inkWidth) <= 1,
                "with no padding or border the box is as wide as the text: " + box.getImage().getWidth() + " vs " + inkWidth);
    }

    public static void checkTextBoxLog()
    {
        TextBox log = new TextBox(3, new Font(16), 200);
        int height = log.getImage().getHeight();
        expect(log.getImage().getWidth() == 200, "the log's width is fixed");

        log.addLine("one");
        expect(log.getText().equals("\n\none"), "a log fills from the bottom: " + log.getText().replace("\n", "|"));
        log.addLine("two");
        log.addLine("three");
        log.addLine("four");
        expect(log.getText().equals("two\nthree\nfour"), "the oldest line scrolls off: " + log.getText().replace("\n", "|"));
        expect(log.getImage().getHeight() == height, "a log keeps its height");

        TextBox growing = new TextBox("one", new Font(16));
        int oneLine = growing.getImage().getHeight();
        growing.addLine("two");
        expect(growing.getImage().getHeight() > oneLine, "without a line count, addLine grows the box");

        growing.setLineCount(1);
        expect(growing.getText().equals("two"), "setLineCount keeps the last lines: " + growing.getText());
    }

    public static void checkTextBoxColors()
    {
        TextBox box = new TextBox("Hi", 24);
        expect(box.getImage().getColorAt(0, 0).equals(Color.DARK_GRAY), "the default border is dark grey");
        expect(box.getImage().getColorAt(box.getImage().getWidth() / 2, 3).equals(Color.WHITE),
                "the default background is white");

        box.setColors(Color.RED, null, null);
        expect(box.getImage().getColorAt(0, 0).getAlpha() == 0, "null background and border leave it see-through");

        box.setColors(Color.BLACK, Color.YELLOW, Color.BLUE);
        box.setBorder(0);
        expect(box.getImage().getColorAt(0, 0).equals(Color.YELLOW), "a 0 border shows the background at the edge");
    }

    public static void checkTextBoxAlignment()
    {
        TextBox box = new TextBox("Hi", new Font(24));
        box.setColors(Color.BLACK, null, null);
        box.setWidth(300);
        box.setHeight(200);

        int[] ink = inkBounds(box.getImage());
        expect(ink[0] <= 20, "text starts at the left by default: " + ink[0]);
        int above = ink[1];
        int below = 199 - ink[3];
        expect(Math.abs(above - below) <= 8, "text is centred up and down by default: " + above + " above, " + below + " below");

        box.setCentered(true);
        ink = inkBounds(box.getImage());
        int left = ink[0];
        int right = 299 - ink[2];
        expect(Math.abs(left - right) <= 2, "setCentered centres the text: " + left + " left, " + right + " right");

        box.setVerticallyCentered(false);
        ink = inkBounds(box.getImage());
        expect(ink[1] < 30, "setVerticallyCentered(false) puts the text at the top: " + ink[1]);
    }

    // ----- StatBar -----

    public static void checkStatBarValues()
    {
        StatBar bar = new StatBar();
        expect(bar.getImage().getWidth() == 48 && bar.getImage().getHeight() == 6, "the default bar is 48 x 6");
        expect(bar.getValue() == 100, "the default bar is full");

        bar.setAnimated(false);
        bar.setValue(50);
        GreenfootImage image = bar.getImage();
        expect(image.getColorAt(10, 3).equals(Color.GREEN), "the filled part is green");
        expect(image.getColorAt(40, 3).equals(Color.RED), "the missing part is red");

        bar.setValue(150);
        expect(bar.getValue() == 100, "values stop at the maximum");
        bar.setValue(-5);
        expect(bar.getValue() == 0, "values stop at 0");

        bar.setMaxValue(200);
        bar.setValue(100);
        expect(bar.getImage().getColorAt(40, 3).equals(Color.RED), "setMaxValue changes what full means");
    }

    public static void checkStatBarSlides()
    {
        StatBar bar = new StatBar(100, null);
        bar.setValue(0);
        expect(bar.getImage().getColorAt(40, 3).equals(Color.GREEN), "an animated bar has not moved before it acts");
        bar.act();
        expect(bar.getImage().getColorAt(46, 3).equals(Color.RED), "it starts sliding on the first act");
        for (int i = 0; i < 60; i++)
        {
            bar.act();
        }
        expect(bar.getImage().getColorAt(1, 3).equals(Color.RED), "it gets all the way there");
    }

    public static void checkStatBarFollowsItsOwner()
    {
        World world = WorldCreator.createWorld(600, 400, 1);
        Actor owner = new Actor() {};
        owner.setImage(new GreenfootImage(40, 40));
        world.addObject(owner, 100, 100);
        StatBar bar = new StatBar(100, owner);
        world.addObject(bar, 0, 0);
        expect(bar.getX() == 100 && bar.getY() == 100 - (20 + 3 + 4),
                "the bar sits just above its owner when added: " + bar.getX() + "," + bar.getY());

        owner.setLocation(200, 150);
        bar.act();
        expect(bar.getX() == 200 && bar.getY() == 123, "the bar follows: " + bar.getX() + "," + bar.getY());

        bar.setOffset(30);
        expect(bar.getY() == 180, "setOffset moves it below: " + bar.getY());

        world.removeObject(owner);
        bar.act();
        expect(bar.getWorld() == null, "the bar leaves when its owner does");
    }

    public static void checkStatBarHidesWhenFull()
    {
        StatBar bar = new StatBar();
        bar.setAnimated(false);
        bar.setHideWhenFull(true);
        expect(bar.getImage().getColorAt(10, 3).getAlpha() == 0, "a full bar hides");
        bar.setValue(50);
        expect(bar.getImage().getColorAt(10, 3).equals(Color.GREEN), "a bar that isn't full shows");
    }

    public static void checkStatBarSeveralBars()
    {
        StatBar bars = new StatBar(new int[] { 100, 50 }, new int[] { 100, 25 }, null, 40, 10);
        GreenfootImage image = bars.getImage();
        expect(image.getColorAt(30, 2).equals(Color.GREEN), "the first bar is full and green");
        expect(image.getColorAt(10, 7).equals(new Color(0, 150, 255)), "the second bar is blue");
        expect(image.getColorAt(30, 7).equals(Color.RED), "the second bar is half empty");

        bars.setAnimated(false);
        bars.setValue(new int[] { 0, 50 });
        image = bars.getImage();
        expect(image.getColorAt(30, 2).equals(Color.RED) && image.getColorAt(30, 7).equals(new Color(0, 150, 255)),
                "setValue(int[]) sets every bar");
    }

    public static void checkStatBarBorder()
    {
        StatBar bar = new StatBar();
        bar.setBorder(Color.BLACK, 2);
        GreenfootImage image = bar.getImage();
        expect(image.getColorAt(0, 0).equals(Color.BLACK) && image.getColorAt(1, 1).equals(Color.BLACK), "the border is 2 pixels");
        expect(image.getColorAt(2, 2).equals(Color.GREEN), "the bar is inside the border");
    }

    // ----- Counter -----

    public static void checkCounterLooksLikeTheOldPicture()
    {
        Counter counter = new Counter();
        GreenfootImage image = counter.getImage();
        expect(image.getWidth() == 88 && image.getHeight() == 26, "the standard counter is 88 x 26: "
                + image.getWidth() + " x " + image.getHeight());
        expect(image.getColorAt(0, 0).getAlpha() == 0, "its ends are round");
        Color middle = image.getColorAt(8, 13);
        expect(middle.getRed() > 200 && middle.getGreen() > 160 && middle.getBlue() < 60, "it is gold: " + middle);
        expect(image.getColorAt(44, 0).getRed() < 40, "it has a dark outline");
    }

    public static void checkCounterCounts()
    {
        Counter counter = new Counter("Score: ");
        counter.add(5);
        expect(counter.getValue() == 5, "add sets the target at once");
        GreenfootImage before = counter.getImage();
        counter.act();
        expect(counter.getImage() != before, "acting counts towards the target");
        int width = counter.getImage().getWidth();
        counter.setValue(1000000);
        int grownWidth = counter.getImage().getWidth();
        expect(grownWidth > width, "the box grows to fit the text");
        counter.setValue(1);
        expect(counter.getImage().getWidth() < grownWidth, "and shrinks back");
    }

    public static void checkCounterColors()
    {
        Counter counter = new Counter("Lives: ", 44, Color.WHITE, Color.BLUE, Color.BLACK);
        expect(counter.getImage().getHeight() == 48, "the longer constructor sets the size");
        // A pixel inside the round left end, clear of the text:
        Color middle = counter.getImage().getColorAt(8, 24);
        expect(middle.getBlue() > 200 && middle.getRed() < 40, "and the colours: " + middle);

        counter.setColors(Color.BLACK, Color.GREEN, Color.BLACK);
        middle = counter.getImage().getColorAt(8, 24);
        expect(middle.getGreen() > 200 && middle.getBlue() < 40, "setColors redraws the box: " + middle);
    }

    // ----- Helpers -----

    private static void expect(boolean condition, String what)
    {
        if (!condition)
        {
            throw new AssertionError(what);
        }
    }

    /**
     * The bounds of the non-transparent pixels, as {left, top, right, bottom}.
     */
    private static int[] inkBounds(GreenfootImage image)
    {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if (image.getColorAt(x, y).getAlpha() > 0)
                {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        expect(right >= 0, "the image has something drawn on it");
        return new int[] { left, top, right, bottom };
    }
}
