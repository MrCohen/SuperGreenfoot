import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * A Counter class that allows you to display a numerical value on screen.
 *
 * The Counter is an actor, so you will need to create it, and then add it to
 * the world in Greenfoot.  If you keep a reference to the Counter then you
 * can adjust its value.  Here's an example of a world class that
 * displays a counter with the number of act cycles that have occurred:
 *
 * <pre>
 * class CountingWorld
 * {
 *     private Counter actCounter;
 *
 *     public CountingWorld()
 *     {
 *         super(600, 400, 1);
 *         actCounter = new Counter("Act Cycles: ");
 *         addObject(actCounter, 100, 100);
 *     }
 *
 *     public void act()
 *     {
 *         actCounter.setValue(actCounter.getValue() + 1);
 *     }
 * }
 * </pre>
 *
 * The counter draws itself: a gold, rounded box with a dark outline, which
 * grows wider when the text needs more room.  To change its colours or size,
 * use the longer constructor, e.g.
 * <code>new Counter("Lives: ", 30, Color.WHITE, Color.BLUE, Color.BLACK)</code>,
 * or call setColors.
 *
 * @author Neil Brown and Michael Kölling (drawn in code for SuperGreenfoot)
 * @version 2.0
 */
public class Counter extends Actor
{
    // The look. Change the defaults here, or use the longer constructor or setColors.
    private Color textColor = Color.BLACK;
    private Color fillColor = new Color(241, 195, 37);   // gold
    private Color outlineColor = new Color(10, 8, 2);    // almost black
    private Font font = new Font(true, false, 22);       // bold, 22 point

    private GreenfootImage background;   // the box without its text, redrawn only when it changes
    private int value;
    private int target;
    private String prefix;

    public Counter()
    {
        this(new String());
    }

    /**
     * Create a new counter, initialised to 0.
     */
    public Counter(String prefix)
    {
        value = 0;
        target = 0;
        this.prefix = prefix;
        updateImage();
    }

    /**
     * Create a new counter, initialised to 0, with your own text size and colours.
     *
     * @param prefix        the text shown before the number, e.g. "Score: "
     * @param fontSize      the size of the text (the standard counter uses 22)
     * @param textColor     the colour of the text
     * @param fillColor     the colour of the box
     * @param outlineColor  the colour of the box's outline
     */
    public Counter(String prefix, int fontSize, Color textColor, Color fillColor, Color outlineColor)
    {
        this.font = new Font(true, false, fontSize);
        this.textColor = textColor;
        this.fillColor = fillColor;
        this.outlineColor = outlineColor;
        value = 0;
        target = 0;
        this.prefix = prefix;
        updateImage();
    }

    /**
     * Animate the display to count up (or down) to the current target value.
     */
    public void act()
    {
        if (value < target) {
            value++;
            updateImage();
        }
        else if (value > target) {
            value--;
            updateImage();
        }
    }

    /**
     * Add a new score to the current counter value.  This will animate
     * the counter over consecutive frames until it reaches the new value.
     */
    public void add(int score)
    {
        target += score;
    }

    /**
     * Return the current counter value.
     */
    public int getValue()
    {
        return target;
    }

    /**
     * Set a new counter value.  This will not animate the counter.
     */
    public void setValue(int newValue)
    {
        target = newValue;
        value = newValue;
        updateImage();
    }

    /**
     * Sets a text prefix that should be displayed before
     * the counter value (e.g. "Score: ").
     */
    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
        updateImage();
    }

    /**
     * Change the colours of the text, the box and the box's outline.
     */
    public void setColors(Color textColor, Color fillColor, Color outlineColor)
    {
        this.textColor = textColor;
        this.fillColor = fillColor;
        this.outlineColor = outlineColor;
        background = null;   // the box must be drawn again in the new colours
        updateImage();
    }

    /**
     * Update the image on screen to show the current value.
     */
    private void updateImage()
    {
        String text = prefix + value;
        // The standard 22-point counter is at least 88 x 26, like the old counter picture.
        int height = font.getSize() + 4;
        int width = Math.max(font.getSize() * 4, font.getStringWidth(text) + height);
        if (background == null || background.getWidth() != width)
        {
            background = drawBox(width, height);
        }

        GreenfootImage image = new GreenfootImage(background);
        image.setFont(font);
        image.setColor(textColor);
        image.drawCenteredString(text, width / 2, height / 2);
        setImage(image);
    }

    /**
     * Draw the box: a bar with round ends and a thin outline, shaded darker
     * towards its edges (more at the top) so that it looks slightly raised.
     * It is drawn one pixel at a time so that the round ends come out smooth.
     */
    private GreenfootImage drawBox(int width, int height)
    {
        GreenfootImage box = new GreenfootImage(width, height);
        double radius = height / 2.0;
        double outline = height / 26.0;   // 1 pixel on the standard counter
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // How far the middle of this pixel is from the line along the middle of the box:
                double px = x + 0.5;
                double py = y + 0.5;
                double nearestX = Math.max(radius, Math.min(width - radius, px));
                double distance = Math.hypot(px - nearestX, py - radius);

                // How much of the pixel is inside the box (0 to 1), and how much of
                // that is inside the outline:
                double inside = clamp(radius - distance + 0.5);
                if (inside == 0) {
                    continue;
                }
                double fill = clamp(radius - outline - distance + 0.5);

                // Darker near the edge: up to 40% darker at the top, 20% at the bottom.
                double depth = Math.min(1, Math.max(0, radius - outline - distance) / (radius - outline));
                double darkest = 0.3 - 0.1 * (py - radius) / radius;
                double shade = 1 - darkest * (1 - depth) * (1 - depth);

                int r = mix(outlineColor.getRed(), fillColor.getRed() * shade, fill);
                int g = mix(outlineColor.getGreen(), fillColor.getGreen() * shade, fill);
                int b = mix(outlineColor.getBlue(), fillColor.getBlue() * shade, fill);
                box.setColorAt(x, y, new Color(r, g, b, (int) Math.round(255 * inside)));
            }
        }
        return box;
    }

    private static double clamp(double amount)
    {
        return Math.max(0, Math.min(1, amount));
    }

    /**
     * Blend from one colour value to another: amount 0 gives from, 1 gives to.
     */
    private static int mix(int from, double to, double amount)
    {
        return (int) Math.round(from + (to - from) * amount);
    }
}
