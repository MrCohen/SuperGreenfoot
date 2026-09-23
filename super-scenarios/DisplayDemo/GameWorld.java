import greenfoot.*;

/**
 * SuperGreenfoot display API demo, part 2: the "game" the menu chose.
 *
 * A bouncing ball plus a readout of the live display state. The same keys
 * as the menu work here (F, P, C, L, W); M returns to the menu.
 *
 * The bottom bar demonstrates the mouse state API: hold the left button to
 * charge a shot (Greenfoot.isMouseButtonDown) and let go to fire a ball from
 * the pointer (Greenfoot.mouseReleased). Try it with H and X on, so the
 * cursor is a crosshair.
 */
public class GameWorld extends World
{
    private static final int MAX_CHARGE = 45;

    private final Readout readout = new Readout();
    private final Readout mouseReadout = new Readout();
    private int charge;
    private String lastRelease = "nothing let go of yet";

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
        addObject(mouseReadout, width / 2, height - 20);
    }

    public void act()
    {
        String key = Greenfoot.getKey();
        if (key != null && !DisplayKeys.handle(key) && key.equals("m")) {
            Greenfoot.setWorld(new MenuWorld());
            return;
        }
        readout.update(getWidth() + "x" + getHeight() + "   " + DisplayKeys.status() + "   M: menu");
        handleMouse();
        mouseReadout.update(heldText() + "   charge " + charge + "/" + MAX_CHARGE + "   " + lastRelease);
    }

    /** Hold the left button to charge, let go to fire a ball from the pointer. */
    private void handleMouse()
    {
        if (Greenfoot.isMouseButtonDown(1)) {
            charge = Math.min(charge + 1, MAX_CHARGE);
        }
        if (!Greenfoot.mouseReleased(null)) {
            return;
        }
        MouseInfo mouse = Greenfoot.getMouseInfo();
        int button = mouse == null ? 0 : mouse.getButton();
        lastRelease = "let go of " + buttonName(button);
        if (button == 1) {
            if (charge > 0 && mouse != null) {
                double speed = 1 + 9.0 * charge / MAX_CHARGE;
                addObject(new Ball(speed, -speed / 2), mouse.getX(), mouse.getY());
            }
            charge = 0;
        }
    }

    /** Which buttons are down right now, as words. */
    private static String heldText()
    {
        String held = "";
        for (int button = 1; button <= 3; button++) {
            if (Greenfoot.isMouseButtonDown(button)) {
                held += (held.isEmpty() ? "" : "+") + buttonName(button);
            }
        }
        return "holding " + (held.isEmpty() ? "nothing" : held);
    }

    private static String buttonName(int button)
    {
        switch (button) {
            case 1: return "left";
            case 2: return "middle";
            case 3: return "right";
            default: return "no button";
        }
    }
}
