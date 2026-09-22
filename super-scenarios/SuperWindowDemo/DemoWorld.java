import greenfoot.*;

/**
 * SuperWindow demo. Press Run, then:
 *
 *   - Drag the Inventory window by its title bar; the items move with it.
 *     Its yellow button minimises it, the red one closes it (press I to reopen).
 *   - Drag items around inside the Inventory. They are clipped at its edge.
 *   - Hover an item for a tooltip (an always-on-top window that follows the mouse).
 *   - Scroll the Scores window with the mouse wheel or its scroll bar.
 *   - Press P for a modal pause window: nothing behind it can be clicked.
 *   - The top-left HUD is a borderless, locked panel.
 *   - Press W to jump to a second world, taking the Inventory window with you,
 *     contents intact. W again brings it back.
 *   - Click anywhere on the ground to send the critters there. Clicking on a
 *     window does not (see DemoWorld.act for the check).
 */
public class DemoWorld extends World
{
    private final InventoryWindow inventory;
    private final PauseWindow pause;
    private final Label hudLabel;
    private int ticks;

    public DemoWorld()
    {
        this(new InventoryWindow());
    }

    /** Build the world around an inventory window that may come from another world. */
    public DemoWorld(InventoryWindow inventory)
    {
        super(800, 520, 1);
        paintGround();
        this.inventory = inventory;

        for (int i = 0; i < 8; i++) {
            addObject(new Critter(), 100 + Greenfoot.getRandomNumber(600), 150 + Greenfoot.getRandomNumber(300));
        }

        // A borderless, locked HUD panel with a transparent background.
        SuperWindow hud = new SuperWindow(260, 44);
        hud.setBorderThickness(0);
        hud.setBackgroundColor(new Color(0, 0, 0, 90));
        hudLabel = new Label("", 16, Color.WHITE);
        hud.addObject(hudLabel, 130, 22);
        addObject(hud, 0, 0);
        hud.setTopLeft(8, 8);

        // The inventory: kept between worlds.
        addObject(inventory, 0, 0);
        inventory.setTopLeft(460, 40);

        // A scrolling list.
        ListWindow scores = new ListWindow();
        addObject(scores, 0, 0);
        scores.setTopLeft(40, 300);

        // A picture in a frame (no title bar).
        SuperWindow frame = new SuperWindow(makePicture(), null);
        frame.setBorderColor(new Color(120, 80, 40));
        frame.setBorderThickness(6);
        addObject(frame, 0, 0);
        frame.setTopLeft(300, 70);

        // The pause window starts closed.
        pause = new PauseWindow();
        addObject(pause, getWidth() / 2, getHeight() / 2);
        pause.close();

        addObject(new Tooltip(), 0, 0);
    }

    public void act()
    {
        ticks++;
        hudLabel.setText("Ticks: " + ticks + "   Items: " + inventory.numberOfObjects()
                + "   Windows: " + getWindows().size());

        String key = Greenfoot.getKey();
        if ("p".equals(key)) {
            pause.toggle();
        }
        else if ("i".equals(key)) {
            inventory.open();
        }
        else if ("w".equals(key)) {
            Greenfoot.setWorld(new SecondWorld(inventory));
        }

        // A click on the ground (not on any window) sends the critters there.
        MouseInfo m = Greenfoot.getMouseInfo();
        if (Greenfoot.mouseClicked(null) && m != null && m.getWindow() == null) {
            for (Critter c : getObjects(Critter.class)) {
                c.turnTowards(m.getX(), m.getY());
            }
        }
    }

    public boolean isPaused()
    {
        return pause.isOpen();
    }

    private void paintGround()
    {
        GreenfootImage bg = getBackground();
        bg.setColor(new Color(60, 110, 60));
        bg.fill();
        bg.setColor(new Color(70, 125, 70));
        for (int i = 0; i < 400; i++) {
            int x = Greenfoot.getRandomNumber(getWidth());
            int y = Greenfoot.getRandomNumber(getHeight());
            bg.fillRect(x, y, 3, 3);
        }
    }

    private static GreenfootImage makePicture()
    {
        GreenfootImage img = new GreenfootImage(140, 100);
        for (int y = 0; y < 100; y++) {
            img.setColor(new Color(40, 60 + y, 200 - y));
            img.drawLine(0, y, 139, y);
        }
        img.setColor(Color.YELLOW);
        img.fillOval(90, 15, 30, 30);
        img.setColor(new Color(40, 120, 40));
        img.fillRect(0, 75, 140, 25);
        return img;
    }
}
