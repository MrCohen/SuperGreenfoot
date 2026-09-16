import greenfoot.*;

/**
 * Moves in a circle. With precise=false it uses Greenfoot's integer API and
 * visibly steps; with precise=true it uses the SuperGreenfoot double API and glides.
 */
public class Orbiter extends Actor
{
    private final boolean precise;
    private int frame = 0;

    public Orbiter(boolean precise)
    {
        this.precise = precise;
        GreenfootImage img = new GreenfootImage(28, 14);
        img.setColor(precise ? new Color(80, 220, 120) : new Color(240, 160, 60));
        img.fillRect(0, 0, 28, 14);
        img.setColor(Color.WHITE);
        img.fillRect(22, 4, 5, 6);   // "nose" so rotation is visible
        setImage(img);
    }

    public void act()
    {
        if (precise) {
            move(0.7);
            turn(0.6);
        }
        else {
            move(1);
            if (++frame % 2 == 0) {
                turn(1);
            }
        }
    }
}
