import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * A bar that shows a value out of a maximum: health, energy, ammo, progress,
 * time left. It can follow an actor around, floating just above it, or stay
 * wherever you put it.
 *
 * <p>A health bar for the player, made in the world:</p>
 * <pre>
 *     Player player = new Player();
 *     addObject(player, 300, 200);
 *     StatBar health = new StatBar(100, player);   // full: 100 out of 100
 *     addObject(health, 0, 0);                     // it moves itself above the player
 *     ...
 *     health.setValue(75);                         // the bar slides down to 75
 * </pre>
 *
 * <p>A StatBar can also hide itself when it is full (setHideWhenFull), have a
 * border (setBorder), jump to new values instead of sliding (setAnimated), and
 * show several bars in one, such as health and mana, by giving the
 * constructor arrays of values. Pass null as the owner for a bar that stays
 * where you put it.</p>
 *
 * <p>Based on Jordan Cohen's SuperStatBar.</p>
 *
 * @author SuperGreenfoot contributors
 * @version 1.0
 */
public class StatBar extends Actor
{
    // The look. Change the defaults here, or call setColors and setBorder.
    // Bar 1 is green, bar 2 blue, and so on; the missing part of every bar is red.
    private static final Color[] FILLED_COLORS = { Color.GREEN, new Color(0, 150, 255), Color.ORANGE, Color.MAGENTA };
    private static final Color MISSING_COLOR = Color.RED;
    private Color borderColor = null;   // null: no border
    private int borderThickness = 1;

    private int[] maxValues;
    private int[] values;
    private double[] shownValues;   // what the bars show now; they slide towards values
    private Color[] filledColors;
    private Color[] missingColors;
    private int width;
    private int height;

    private Actor owner;                // the actor to follow, or null to stay put
    private boolean autoOffset = true;  // true: sit just above the owner's image
    private int offset;                 // otherwise: how far below the owner's centre (negative is above)
    private boolean hideWhenFull = false;
    private boolean animated = true;

    /**
     * Make a full bar (100 out of 100), 48 x 6 pixels, that stays where you put it.
     */
    public StatBar()
    {
        this(100, 100, null, 48, 6);
    }

    /**
     * Make a full bar, 48 x 6 pixels, that follows an actor.
     *
     * @param maxValue  the most the value can be; the bar starts full
     * @param owner     the actor to follow, or null for a bar that stays where you put it
     */
    public StatBar(int maxValue, Actor owner)
    {
        this(maxValue, maxValue, owner, 48, 6);
    }

    /**
     * Make a bar of any size.
     *
     * @param maxValue  the most the value can be
     * @param value     the value to start with
     * @param owner     the actor to follow, or null for a bar that stays where you put it
     * @param width     the width of the bar in pixels
     * @param height    the height of the bar in pixels
     */
    public StatBar(int maxValue, int value, Actor owner, int width, int height)
    {
        this(new int[] { maxValue }, new int[] { value }, owner, width, height);
    }

    /**
     * Make several bars in one, stacked top to bottom, such as health and mana.
     * The two arrays must be the same length, one entry per bar.
     *
     * @param maxValues  the most each value can be
     * @param values     the value each bar starts with
     * @param owner      the actor to follow, or null for bars that stay where you put them
     * @param width      the width in pixels
     * @param height     the height in pixels, shared between the bars
     */
    public StatBar(int[] maxValues, int[] values, Actor owner, int width, int height)
    {
        this.maxValues = maxValues.clone();
        this.values = new int[maxValues.length];
        this.shownValues = new double[maxValues.length];
        this.filledColors = new Color[maxValues.length];
        this.missingColors = new Color[maxValues.length];
        for (int i = 0; i < maxValues.length; i++)
        {
            this.values[i] = clamp(values[i], i);
            shownValues[i] = this.values[i];
            filledColors[i] = FILLED_COLORS[i % FILLED_COLORS.length];
            missingColors[i] = MISSING_COLOR;
        }
        this.owner = owner;
        this.width = width;
        this.height = height;
        redraw();
    }

    /**
     * Follow the owner (if there is one) and slide the bars towards their values.
     */
    public void act()
    {
        follow();
        if (slide())
        {
            redraw();
        }
    }

    /**
     * Move to the owner as soon as this bar is added to the world.
     */
    protected void addedToWorld(World world)
    {
        follow();
    }

    // ----- Values -----

    /**
     * Set the value (of the first bar, if there are several).
     */
    public void setValue(int value)
    {
        values[0] = clamp(value, 0);
        valuesChanged();
    }

    /**
     * Set the value of every bar, one entry per bar.
     */
    public void setValue(int[] newValues)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = clamp(newValues[i], i);
        }
        valuesChanged();
    }

    /**
     * Return the value (of the first bar, if there are several).
     */
    public int getValue()
    {
        return values[0];
    }

    /**
     * Change the most the value can be (of the first bar, if there are several).
     */
    public void setMaxValue(int maxValue)
    {
        maxValues[0] = Math.max(1, maxValue);
        values[0] = clamp(values[0], 0);
        valuesChanged();
    }

    /**
     * Change the most each value can be, one entry per bar.
     */
    public void setMaxValue(int[] newMaxValues)
    {
        for (int i = 0; i < maxValues.length; i++)
        {
            maxValues[i] = Math.max(1, newMaxValues[i]);
            values[i] = clamp(values[i], i);
        }
        valuesChanged();
    }

    // ----- Behaviour -----

    /**
     * Slide smoothly to new values over a few acts (true, the default), or jump straight to them (false).
     */
    public void setAnimated(boolean animated)
    {
        this.animated = animated;
        valuesChanged();
    }

    /**
     * Hide the bar while every value is at its maximum (for example, only show
     * an enemy's health bar once it has been hit).
     */
    public void setHideWhenFull(boolean hideWhenFull)
    {
        this.hideWhenFull = hideWhenFull;
        redraw();
    }

    /**
     * Follow a different actor, or null to stay where the bar is.
     */
    public void setOwner(Actor owner)
    {
        this.owner = owner;
        follow();
    }

    /**
     * Set how far below the owner's centre the bar sits, in pixels (negative
     * is above). Without this the bar sits just above the owner's image.
     */
    public void setOffset(int offset)
    {
        this.offset = offset;
        autoOffset = false;
        follow();
    }

    // ----- The look -----

    /**
     * Set the colours of every bar: the filled part and the missing part.
     */
    public void setColors(Color filledColor, Color missingColor)
    {
        for (int i = 0; i < filledColors.length; i++)
        {
            filledColors[i] = filledColor;
            missingColors[i] = missingColor;
        }
        redraw();
    }

    /**
     * Set the colours of each bar, one entry per bar.
     */
    public void setColors(Color[] filledColors, Color[] missingColors)
    {
        for (int i = 0; i < this.filledColors.length; i++)
        {
            this.filledColors[i] = filledColors[i];
            this.missingColors[i] = missingColors[i];
        }
        redraw();
    }

    /**
     * Give the bar a border, or pass null for no border (the default).
     *
     * @param color      the border colour, or null for none
     * @param thickness  the border thickness in pixels
     */
    public void setBorder(Color color, int thickness)
    {
        borderColor = color;
        borderThickness = Math.max(0, thickness);
        redraw();
    }

    // ----- Inner workings -----

    /**
     * Keep a value between 0 and its bar's maximum.
     */
    private int clamp(int value, int bar)
    {
        return Math.max(0, Math.min(maxValues[bar], value));
    }

    /**
     * Without animation, show the new values straight away; with it, act() slides to them.
     */
    private void valuesChanged()
    {
        if (!animated)
        {
            for (int i = 0; i < values.length; i++)
            {
                shownValues[i] = values[i];
            }
        }
        redraw();
    }

    /**
     * Move each shown value a quarter of the way to its real value, snapping
     * the last little bit. Returns whether anything changed.
     */
    private boolean slide()
    {
        boolean changed = false;
        for (int i = 0; i < values.length; i++)
        {
            double gap = values[i] - shownValues[i];
            if (gap != 0)
            {
                // Snap once the gap is less than half a pixel of bar:
                if (Math.abs(gap) * width < maxValues[i] * 0.5)
                {
                    shownValues[i] = values[i];
                }
                else
                {
                    shownValues[i] += gap / 4;
                }
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Sit at the owner's position, or leave the world if the owner has left it.
     */
    private void follow()
    {
        World world = getWorld();
        if (owner == null || world == null)
        {
            return;
        }
        if (owner.getWorld() == null)
        {
            world.removeObject(this);
            return;
        }
        int dy = offset;
        if (autoOffset)
        {
            GreenfootImage ownerImage = owner.getImage();
            int ownerHeight = (ownerImage == null) ? 0 : ownerImage.getHeight();
            dy = -(ownerHeight / 2 + height / 2 + 4);
        }
        setLocation(owner.getX(), owner.getY() + dy);
    }

    /**
     * Draw the bars as this actor's image.
     */
    private void redraw()
    {
        GreenfootImage image = new GreenfootImage(width, height);
        if (hideWhenFull && isFull())
        {
            setImage(image);   // an empty (see-through) image
            return;
        }

        int border = (borderColor == null) ? 0 : borderThickness;
        if (border > 0)
        {
            // Fill it all with the border colour; the bars cover all but the edge.
            image.setColor(borderColor);
            image.fill();
        }
        int innerWidth = width - 2 * border;
        int innerHeight = height - 2 * border;
        for (int i = 0; i < values.length; i++)
        {
            int top = border + innerHeight * i / values.length;
            int bottom = border + innerHeight * (i + 1) / values.length;
            int filledWidth = (int) Math.round(innerWidth * shownValues[i] / maxValues[i]);
            image.setColor(filledColors[i]);
            image.fillRect(border, top, filledWidth, bottom - top);
            image.setColor(missingColors[i]);
            image.fillRect(border + filledWidth, top, innerWidth - filledWidth, bottom - top);
        }
        setImage(image);
    }

    /**
     * Whether every bar shows its maximum.
     */
    private boolean isFull()
    {
        for (int i = 0; i < values.length; i++)
        {
            if (shownValues[i] < maxValues[i])
            {
                return false;
            }
        }
        return true;
    }
}
