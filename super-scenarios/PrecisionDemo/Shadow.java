import greenfoot.*;

/**
 * A translucent ellipse that follows a walker and sits just below it, but is
 * always painted underneath it thanks to z = -1 (same y-sort key otherwise).
 */
public class Shadow extends Actor
{
    private final Walker owner;

    public Shadow(Walker owner)
    {
        this.owner = owner;
        GreenfootImage img = new GreenfootImage(48, 16);
        img.setColor(new Color(0, 0, 0, 120));
        img.fillOval(0, 0, 48, 16);
        setImage(img);
        setZ(-1);
    }

    public void act()
    {
        if (owner.getWorld() == null) {
            getWorld().removeObject(this);
            return;
        }
        // Same y as the owner so the y-sort ties and z decides; shadow drawn 18px lower.
        setLocation(owner.getPreciseX(), owner.getPreciseY());
    }
}
