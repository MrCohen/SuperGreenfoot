import greenfoot.*;

/**
 * A line of text.
 */
public class Label extends Actor
{
    private final int size;
    private final Color color;

    public Label(String text, int size, Color color)
    {
        this.size = size;
        this.color = color;
        setText(text);
    }

    public void setText(String text)
    {
        setImage(new GreenfootImage(text.isEmpty() ? " " : text, size, color, new Color(0, 0, 0, 0)));
    }
}
