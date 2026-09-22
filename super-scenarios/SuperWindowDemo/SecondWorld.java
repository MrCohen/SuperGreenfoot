import greenfoot.*;

/**
 * A second world that receives the inventory window from DemoWorld. The window
 * arrives with all its items still inside. Press W to go back.
 */
public class SecondWorld extends World
{
    private final InventoryWindow inventory;

    public SecondWorld(InventoryWindow inventory)
    {
        super(800, 520, 1);
        this.inventory = inventory;
        getBackground().setColor(new Color(40, 40, 70));
        getBackground().fill();
        getBackground().setColor(Color.WHITE);
        getBackground().setFont(new Font("SansSerif", true, false, 22));
        getBackground().drawString("Second world. The inventory came along. Press W to go back.", 40, 60);

        addObject(inventory, 0, 0);
        inventory.setTopLeft(200, 150);
    }

    public void act()
    {
        if ("w".equals(Greenfoot.getKey())) {
            Greenfoot.setWorld(new DemoWorld(inventory));
        }
    }
}
