import greenfoot.*;

/**
 * An item that lives inside the inventory window. It can be dragged around
 * inside the window (coordinates are the window's own), and shows a tooltip
 * when the mouse hovers over it.
 */
public class Item extends Actor
{
    private final String name;

    public Item(String name, Color color)
    {
        this.name = name;
        GreenfootImage img = new GreenfootImage(40, 40);
        img.setColor(color);
        img.fillRect(0, 0, 40, 40);
        img.setColor(Color.BLACK);
        img.drawRect(0, 0, 39, 39);
        img.setFont(new Font("SansSerif", true, false, 20));
        img.drawCenteredString(name.substring(0, 1), 20, 22);
        setImage(img);
    }

    public String getName()
    {
        return name;
    }

    public void act()
    {
        MouseInfo m = Greenfoot.getMouseInfo();
        if (m == null || getParentWindow() == null) {
            return;
        }
        // Drag within the window: convert the world mouse position to window coordinates.
        if (Greenfoot.mouseDragged(this)) {
            setLocation(getParentWindow().toLocalX(m.getX()), getParentWindow().toLocalY(m.getY()));
        }
    }
}
