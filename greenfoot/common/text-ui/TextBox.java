import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)
import java.util.ArrayList;
import java.util.List;

/**
 * A box of text for your world: a score, a title, a message, or a scrolling
 * log of what is happening in your game.
 *
 * <p>A TextBox sizes itself to fit its text, so the simplest use is one line:</p>
 * <pre>
 *     TextBox title = new TextBox("Game Over", 48);
 *     addObject(title, getWidth() / 2, getHeight() / 2);
 *     ...
 *     title.setText("You Win!");
 * </pre>
 *
 * <p>For several lines, pass an array of Strings, or put "\n" in the text.
 * For a scrolling log, make a box with a fixed number of lines and use addLine:
 * each new line goes in at the bottom and the oldest one scrolls off the top.</p>
 * <pre>
 *     TextBox log = new TextBox(4, new Font(18), 300);   // 4 lines, 300 pixels wide
 *     addObject(log, 200, 350);
 *     log.addLine("You found a key.");
 * </pre>
 *
 * <p>Everything the box works out for itself can also be set by hand:
 * setWidth, setHeight, setLineCount and setPadding. Pass TextBox.AUTO to any
 * of them to go back to automatic. Colours, border, font and centring are set
 * with setColors, setBorder, setFont, setCentered and setVerticallyCentered;
 * pass null to setColors for no background (the text sits straight on the
 * world) or no border.</p>
 *
 * <p>Based on Jordan Cohen's SuperTextBox.</p>
 *
 * @author SuperGreenfoot contributors
 * @version 1.0
 */
public class TextBox extends Actor
{
    /** Pass this to setWidth, setHeight, setLineCount or setPadding to let the box work it out. */
    public static final int AUTO = -1;

    // The look. Change the defaults here, or call setColors, setBorder and setFont.
    private Color textColor = Color.BLACK;
    private Color backgroundColor = Color.WHITE;
    private Color borderColor = Color.DARK_GRAY;
    private int borderThickness = 2;
    private Font font;

    // The layout. AUTO means the box works it out from its text.
    private int fixedWidth = AUTO;       // AUTO: as wide as the widest line
    private int fixedHeight = AUTO;      // AUTO: as tall as its lines need
    private int fixedLineCount = AUTO;   // AUTO: as many lines as the text has
    private int fixedPadding = AUTO;     // AUTO: half the font size
    private boolean centered = false;           // each line centred from side to side
    private boolean verticallyCentered = true;  // the text centred up and down in a tall box

    private List<String> lines = new ArrayList<String>();

    /**
     * Make a box that fits one line of text (use "\n" in the text for more lines).
     *
     * @param text      the text to show
     * @param fontSize  the size of the text, e.g. 24
     */
    public TextBox(String text, int fontSize)
    {
        this(text, new Font(fontSize));
    }

    /**
     * Make a box that fits one line of text in any font (use "\n" in the text for more lines).
     *
     * @param text  the text to show
     * @param font  the font, e.g. new Font("Serif", true, false, 32) for 32-point bold Serif
     */
    public TextBox(String text, Font font)
    {
        this.font = font;
        setText(text);
    }

    /**
     * Make a box that fits several lines of text.
     *
     * @param lines  the text to show, one line per String
     * @param font   the font, e.g. new Font(24)
     */
    public TextBox(String[] lines, Font font)
    {
        this.font = font;
        setText(lines);
    }

    /**
     * Make an empty box with room for a fixed number of lines, for example a
     * scrolling log that you fill with addLine.
     *
     * @param lineCount  how many lines the box shows
     * @param font       the font, e.g. new Font(18)
     * @param width      the width of the box in pixels
     */
    public TextBox(int lineCount, Font font, int width)
    {
        this.font = font;
        this.fixedLineCount = lineCount;
        this.fixedWidth = width;
        setText("");
    }

    // ----- The text -----

    /**
     * Replace all the text. Use "\n" to start a new line.
     */
    public void setText(String text)
    {
        setText(text.split("\n", -1));
    }

    /**
     * Replace all the text with these lines.
     */
    public void setText(String[] newLines)
    {
        lines.clear();
        for (String line : newLines)
        {
            lines.add(line == null ? "" : line);
        }
        fitToLineCount(false);
        redraw();
    }

    /**
     * Add a line at the bottom. In a box with a fixed number of lines the
     * other lines move up and the top one disappears, like a chat or a game
     * log; otherwise the box grows taller.
     */
    public void addLine(String line)
    {
        lines.add(line == null ? "" : line);
        fitToLineCount(true);
        redraw();
    }

    /**
     * Return all the text, with "\n" between the lines.
     */
    public String getText()
    {
        return String.join("\n", lines);
    }

    // ----- The look -----

    /**
     * Change the colours. Pass null for backgroundColor to have no background,
     * or null for borderColor to have no border.
     */
    public void setColors(Color textColor, Color backgroundColor, Color borderColor)
    {
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        redraw();
    }

    /**
     * Set how thick the border is, in pixels (0 for no border).
     */
    public void setBorder(int thickness)
    {
        borderThickness = Math.max(0, thickness);
        redraw();
    }

    /**
     * Change the font, e.g. setFont(new Font("Monospaced", false, false, 20)).
     */
    public void setFont(Font font)
    {
        this.font = font;
        redraw();
    }

    /**
     * Centre each line from side to side (true), or start each line at the left (false, the default).
     */
    public void setCentered(boolean centered)
    {
        this.centered = centered;
        redraw();
    }

    /**
     * In a box taller than its text, centre the text up and down (true, the
     * default), or put it at the top (false).
     */
    public void setVerticallyCentered(boolean verticallyCentered)
    {
        this.verticallyCentered = verticallyCentered;
        redraw();
    }

    // ----- The size -----

    /**
     * Fix the width in pixels (text that doesn't fit is cut off), or AUTO to fit the widest line.
     */
    public void setWidth(int width)
    {
        fixedWidth = width > 0 ? width : AUTO;
        redraw();
    }

    /**
     * Fix the height in pixels, or AUTO to fit the lines. The box is never
     * made shorter than its lines need.
     */
    public void setHeight(int height)
    {
        fixedHeight = height > 0 ? height : AUTO;
        redraw();
    }

    /**
     * Fix how many lines the box shows, or AUTO for as many as the text has.
     * With too many lines the box keeps the last ones.
     */
    public void setLineCount(int lineCount)
    {
        fixedLineCount = lineCount > 0 ? lineCount : AUTO;
        fitToLineCount(false);
        redraw();
    }

    /**
     * Set the space in pixels between the border and the text, or AUTO for half the font size.
     */
    public void setPadding(int padding)
    {
        fixedPadding = padding >= 0 ? padding : AUTO;
        redraw();
    }

    // ----- Drawing -----

    /**
     * With a fixed number of lines, drop lines from the top until the text fits
     * and fill any spare lines with blanks: at the top when adding to a log (so
     * it fills upwards from the bottom), at the bottom otherwise.
     */
    private void fitToLineCount(boolean fillFromBottom)
    {
        if (fixedLineCount == AUTO)
        {
            return;
        }
        while (lines.size() > fixedLineCount)
        {
            lines.remove(0);
        }
        while (lines.size() < fixedLineCount)
        {
            if (fillFromBottom)
            {
                lines.add(0, "");
            }
            else
            {
                lines.add("");
            }
        }
    }

    /**
     * Draw the box and its text as this actor's image.
     */
    private void redraw()
    {
        int border = (borderColor == null) ? 0 : borderThickness;
        int padding = (fixedPadding == AUTO) ? font.getSize() / 2 : fixedPadding;
        int inset = border + padding;
        int lineHeight = font.getLineHeight();

        int widest = 0;
        for (String line : lines)
        {
            widest = Math.max(widest, font.getStringWidth(line));
        }
        int textHeight = lines.size() * lineHeight;
        int width = (fixedWidth == AUTO) ? widest + 2 * inset : fixedWidth;
        int height = Math.max(fixedHeight, textHeight + 2 * inset);

        GreenfootImage image = new GreenfootImage(Math.max(1, width), height);
        if (backgroundColor != null)
        {
            image.setColor(backgroundColor);
            image.fill();
        }
        if (border > 0)
        {
            image.setColor(borderColor);
            for (int i = 0; i < border; i++)
            {
                image.drawRect(i, i, width - 1 - 2 * i, height - 1 - 2 * i);
            }
        }

        image.setFont(font);
        image.setColor(textColor);
        int top = verticallyCentered ? (height - textHeight) / 2 : inset;
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            // drawCenteredString centres the line on a point, so to start a line at
            // the left we centre it half its own width in from the left edge.
            int centerX = centered ? width / 2 : inset + font.getStringWidth(line) / 2;
            int centerY = top + i * lineHeight + lineHeight / 2;
            image.drawCenteredString(line, centerX, centerY);
        }
        setImage(image);
    }
}
