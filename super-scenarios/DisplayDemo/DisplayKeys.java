import greenfoot.*;

/**
 * SuperGreenfoot display API demo: the keys both worlds understand.
 *
 *   F  toggle full screen            (Greenfoot.setFullScreen)
 *   P  pixel-perfect / smooth        (Greenfoot.setScaleMode)
 *   C  show / hide the run controls  (Greenfoot.setControlsVisible)
 *   L  lock the controls hidden      (Greenfoot.setControlsLocked)
 *   W  window at 1x / 2x             (Greenfoot.setWindowScale, exported game only)
 *   H  hide / show the mouse cursor  (Greenfoot.setCursorVisible)
 *   X  crosshair / normal cursor     (Greenfoot.setCursor with a file)
 *   D  drawn cursor / normal cursor  (Greenfoot.setCursor with a picture drawn in code)
 */
public class DisplayKeys
{
    private static boolean crosshair = false;
    private static boolean drawn = false;

    /** A cursor drawn in code: a ring with a dot, hot spot in the middle. */
    private static GreenfootImage ringCursor()
    {
        GreenfootImage img = new GreenfootImage(24, 24);
        img.setColor(Color.YELLOW);
        img.drawOval(1, 1, 21, 21);
        img.drawOval(2, 2, 19, 19);
        img.fillOval(10, 10, 4, 4);
        return img;
    }

    /** Handle one key name from Greenfoot.getKey(); returns true if it was a display key. */
    public static boolean handle(String key)
    {
        if (key == null) {
            return false;
        }
        switch (key) {
            case "f":
                Greenfoot.setFullScreen(!Greenfoot.isFullScreen());
                return true;
            case "p":
                Greenfoot.setScaleMode(Greenfoot.getScaleMode() == ScaleMode.PIXEL_PERFECT
                        ? ScaleMode.SMOOTH : ScaleMode.PIXEL_PERFECT);
                return true;
            case "c":
                Greenfoot.setControlsVisible(!Greenfoot.isControlsVisible());
                return true;
            case "l":
                Greenfoot.setControlsLocked(!Greenfoot.isControlsLocked());
                return true;
            case "w":
                Greenfoot.setWindowScale(Greenfoot.getWindowScale() >= 2 ? 1 : 2);
                return true;
            case "h":
                Greenfoot.setCursorVisible(!Greenfoot.isCursorVisible());
                return true;
            case "x":
                crosshair = !crosshair;
                drawn = false;
                Greenfoot.setCursor(crosshair ? "crosshair.png" : null);
                return true;
            case "d":
                drawn = !drawn;
                crosshair = false;
                Greenfoot.setCursor(drawn ? ringCursor() : null, 12, 12);
                return true;
            default:
                return false;
        }
    }

    /** One line describing the current display state, for the on-screen readout. */
    public static String status()
    {
        return (Greenfoot.isFullScreen() ? "FULL SCREEN" : "windowed")
                + "  scale " + String.format("%.2f", Greenfoot.getDisplayScale()) + "x"
                + "  " + Greenfoot.getScaleMode()
                + "  controls " + (Greenfoot.isControlsLocked() ? "locked" : Greenfoot.isControlsVisible() ? "shown" : "hidden")
                + "  window " + String.format("%.0f", Greenfoot.getWindowScale()) + "x"
                + "  cursor " + (Greenfoot.isCursorVisible() ? (crosshair ? "crosshair" : drawn ? "drawn ring" : "normal") : "hidden");
    }

    /** Where the scenario runs and what screen it has. */
    public static String environment()
    {
        int w = Greenfoot.getScreenWidth();
        int h = Greenfoot.getScreenHeight();
        String screen = w == 0 ? "screen size unknown" : "screen " + w + "x" + h;
        return (Greenfoot.isStandalone() ? "exported game" : "IDE") + ", " + screen
                + (Greenfoot.isFullScreenSupported() ? "" : ", no full screen here");
    }
}
