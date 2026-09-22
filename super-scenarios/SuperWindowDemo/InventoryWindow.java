import greenfoot.*;

/**
 * An inventory: a titled window holding a grid of items. Items can be dragged
 * around inside it. Closing it hides it; press I in the world to reopen it.
 */
public class InventoryWindow extends SuperWindow
{
    public InventoryWindow()
    {
        super(300, 220, "Inventory");
        setBackgroundColor(new Color(215, 200, 170));
        setBorderColor(new Color(110, 70, 30));
        setTitleBarColor(new Color(110, 70, 30));

        // Slots drawn on the window's background, like a world background.
        GreenfootImage bg = getBackground();
        bg.setColor(new Color(0, 0, 0, 40));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                bg.fillRect(15 + col * 56, 15 + row * 66, 50, 50);
            }
        }

        String[] names = { "Sword", "Shield", "Potion", "Bread", "Torch", "Rope", "Gem" };
        Color[] colors = { new Color(200, 200, 220), new Color(120, 90, 60), new Color(200, 60, 200),
                new Color(220, 180, 100), new Color(250, 150, 50), new Color(160, 130, 90), new Color(60, 220, 200) };
        for (int i = 0; i < names.length; i++) {
            addObject(new Item(names[i], colors[i]), 40 + (i % 5) * 56, 40 + (i / 5) * 66);
        }
    }

    /** Refresh the title each time the window is opened. */
    protected void opened()
    {
        setTitle("Inventory (" + numberOfObjects() + " items)");
    }
}
