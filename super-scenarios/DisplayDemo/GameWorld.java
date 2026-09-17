import greenfoot.*;

/**
 * SuperGreenfoot display API demo, part 2: the "game" the menu chose.
 *
 * A bouncing ball plus a readout of the live display state. The same keys
 * as the menu work here (F, P, C, L, W); M returns to the menu.
 */
public class GameWorld extends World
{
    private final Readout readout = new Readout();

    public GameWorld(int width, int height)
    {
        super(width, height, 1);
        setSmoothRendering(true);
        GreenfootImage bg = getBackground();
        bg.setColor(new Color(30, 60, 40));
        bg.fill();
        // A 1-pixel grid every 40 px makes scaling artefacts easy to see
        bg.setColor(new Color(50, 90, 60));
        for (int x = 0; x < width; x += 40) {
            bg.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 40) {
            bg.drawLine(0, y, width, y);
        }
        for (int i = 0; i < 5; i++) {
            addObject(new Ball(), 60 + i * width / 6, 80 + i * 30);
        }
        addObject(readout, width / 2, 24);
    }

    public void act()
    {
        String key = Greenfoot.getKey();
        if (key != null && !DisplayKeys.handle(key) && key.equals("m")) {
            Greenfoot.setWorld(new MenuWorld());
            return;
        }
        readout.update(getWidth() + "x" + getHeight() + "   " + DisplayKeys.status() + "   M: menu");
    }
}
