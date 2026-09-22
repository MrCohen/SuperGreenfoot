import greenfoot.*;

/**
 * A wandering creature on the ground. It never collides with anything inside a
 * window, even when a window is dragged over it.
 */
public class Critter extends Actor
{
    public Critter()
    {
        GreenfootImage img = new GreenfootImage(24, 24);
        img.setColor(new Color(230, 120, 60));
        img.fillOval(0, 0, 24, 24);
        img.setColor(Color.BLACK);
        img.fillOval(15, 8, 5, 5);
        setImage(img);
        setRotation(Greenfoot.getRandomNumber(360));
    }

    public void act()
    {
        World w = getWorld();
        if (w instanceof DemoWorld && ((DemoWorld) w).isPaused()) {
            return;
        }
        move(1.5);
        if (Greenfoot.getRandomNumber(40) == 0) {
            turn(Greenfoot.getRandomNumber(90) - 45);
        }
        if (isAtEdge()) {
            turn(180);
        }
        Critter other = (Critter) getOneIntersectingObject(Critter.class);
        if (other != null) {
            turn(180);
            move(2);
        }
    }
}
