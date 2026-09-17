import greenfoot.*;

/**
 * SuperGreenfoot display API demo: the keys both worlds understand.
 *
 *   F  toggle full screen            (Greenfoot.setFullScreen)
 *   P  pixel-perfect / smooth        (Greenfoot.setScaleMode)
 *   C  show / hide the run controls  (Greenfoot.setControlsVisible)
 *   L  lock the controls hidden      (Greenfoot.setControlsLocked)
 *   W  window at 1x / 2x             (Greenfoot.setWindowScale, exported game only)
 */
public class DisplayKeys
{
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
                + "  window " + String.format("%.0f", Greenfoot.getWindowScale()) + "x";
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
