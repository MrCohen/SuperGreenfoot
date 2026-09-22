import greenfoot.*;

/**
 * A small always-on-top window that follows the mouse and names the item under it.
 */
public class Tooltip extends SuperWindow
{
    private final Label label;

    public Tooltip()
    {
        super(90, 22);
        setAlwaysOnTop(true);
        setKeepOnScreen(false);
        setBackgroundColor(new Color(255, 255, 210));
        setBorderThickness(1);
        setBorderColor(Color.DARK_GRAY);
        label = new Label("", 13, Color.BLACK);
        addObject(label, 45, 11);
        close();
    }

    public void act()
    {
        // MouseInfo.getActor() is only set on acts with a mouse event, so look up
        // whatever item is under the mouse position instead (this sees into open windows).
        MouseInfo m = Greenfoot.getMouseInfo();
        java.util.List<Item> under = m == null ? null : getWorld().getObjectsAt(m.getX(), m.getY(), Item.class);
        if (under != null && !under.isEmpty()) {
            label.setText(under.get(0).getName());
            setTopLeft(m.getX() + 14, m.getY() + 10);
            open();
        }
        else {
            close();
        }
    }
}
