import greenfoot.*;

/**
 * Walks up and down at a fractional speed. Because the world is y-sorted,
 * whichever walker is lower on the screen is drawn in front.
 */
public class Walker extends Actor
{
    private final double speed;
    private double dir = 1;

    public Walker(double speed)
    {
        this.speed = speed;
        GreenfootImage img = new GreenfootImage(40, 48);
        img.setColor(new Color(90, 140, 255));
        img.fillOval(0, 0, 40, 48);
        img.setColor(Color.WHITE);
        img.setFont(new Font("SansSerif", true, false, 18));
        img.drawCenteredString(String.valueOf((int) (speed * 100)), 20, 24);
        setImage(img);
    }

    public void act()
    {
        setLocation(getPreciseX(), getPreciseY() + speed * dir);
        if (getPreciseY() > 340 || getPreciseY() < 270) {
            dir = -dir;
        }
    }
}
