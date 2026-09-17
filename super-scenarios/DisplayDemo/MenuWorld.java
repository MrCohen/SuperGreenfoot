import greenfoot.*;

/**
 * SuperGreenfoot display API demo, part 1: on-boarding.
 *
 * A game can ask the player how it should be shown before the real world
 * exists: this menu reads the screen size, suggests the world size that fills
 * it at a whole-number scale, and lets the player choose a size and full screen.
 * Press Run, then use the number keys and F / P / C / L / W (see DisplayKeys).
 */
public class MenuWorld extends World
{
    private static final int[][] SIZES = { {640, 360}, {960, 540}, {1280, 720} };
    private String lastShown = "";

    public MenuWorld()
    {
        super(640, 360, 1);
        redraw();
    }

    public void act()
    {
        String key = Greenfoot.getKey();
        if (key != null) {
            if (!DisplayKeys.handle(key)) {
                for (int i = 0; i < SIZES.length; i++) {
                    if (key.equals(String.valueOf(i + 1))) {
                        Greenfoot.setWorld(new GameWorld(SIZES[i][0], SIZES[i][1]));
                        return;
                    }
                }
            }
        }
        redraw();
    }

    /** Redraw the text only when something changed (drawing strings every act is wasteful). */
    private void redraw()
    {
        String state = DisplayKeys.environment() + "\n" + DisplayKeys.status();
        if (state.equals(lastShown)) {
            return;
        }
        lastShown = state;
        GreenfootImage bg = getBackground();
        bg.setColor(new Color(24, 28, 40));
        bg.fill();
        bg.setColor(Color.WHITE);
        bg.setFont(new Font("SansSerif", true, false, 22));
        bg.drawCenteredString("SuperGreenfoot display demo", 320, 40);
        bg.setFont(new Font("SansSerif", false, false, 14));
        bg.setColor(new Color(180, 200, 255));
        bg.drawString(DisplayKeys.environment(), 30, 85);
        bg.drawString(DisplayKeys.status(), 30, 105);
        bg.setColor(Color.WHITE);
        int y = 150;
        int sw = Greenfoot.getScreenWidth();
        int sh = Greenfoot.getScreenHeight();
        for (int i = 0; i < SIZES.length; i++) {
            int w = SIZES[i][0];
            int h = SIZES[i][1];
            String fit = "";
            if (sw > 0) {
                double s = Math.min(sw / (double) w, sh / (double) h);
                boolean whole = Math.abs(s - Math.rint(s)) < 0.001;
                fit = whole ? "   fills your screen at " + (int) s + "x (crisp)"
                            : "   fits at " + String.format("%.2f", s) + "x (soft, or " + (int) Math.floor(s) + "x pixel-perfect with borders)";
            }
            bg.drawString("Press " + (i + 1) + " for a " + w + "x" + h + " world" + fit, 30, y);
            y += 24;
        }
        bg.setColor(new Color(150, 150, 150));
        bg.drawString("F full screen   P pixel-perfect/smooth   C controls   L lock   W window 2x (exported game)", 30, 300);
        bg.drawString("Shortcut+Shift+F always leaves full screen; hold Esc 2 s to recover locked controls.", 30, 322);
    }
}
