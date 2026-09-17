import greenfoot.*;

/** Bounces around using the precise movement API. */
public class Ball extends Actor
{
    private double vx = 1.5 + Greenfoot.getRandomNumber(20) / 10.0;
    private double vy = 1.0 + Greenfoot.getRandomNumber(20) / 10.0;

    public Ball()
    {
        GreenfootImage img = new GreenfootImage(24, 24);
        img.setColor(new Color(255, 200, 60));
        img.fillOval(0, 0, 23, 23);
        setImage(img);
    }

    public void act()
    {
        double x = getPreciseX() + vx;
        double y = getPreciseY() + vy;
        World w = getWorld();
        if (x < 12 || x > w.getWidth() - 12) {
            vx = -vx;
        }
        if (y < 40 || y > w.getHeight() - 12) {
            vy = -vy;
        }
        setLocation(x, y);
    }
}
