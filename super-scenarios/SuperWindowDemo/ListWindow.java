import greenfoot.*;

/**
 * A scrollable list: the content is much taller than the window. Use the mouse
 * wheel over the window, or drag the scroll bar.
 */
public class ListWindow extends SuperWindow
{
    public ListWindow()
    {
        super(200, 150, "Scores (scroll me)");
        setMinimizable(false);
        setContentSize(200, 30 * 24 + 10);
        setScrollable(true);
        setBackgroundColor(new Color(240, 240, 250));
        for (int i = 0; i < 30; i++) {
            addObject(new Label((i + 1) + ".  Player " + (char) ('A' + i % 26) + "   " + (5000 - i * 137), 14,
                    i % 2 == 0 ? Color.BLACK : new Color(60, 60, 120)), 95, 17 + i * 24);
        }
    }
}
