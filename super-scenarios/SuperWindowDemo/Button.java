import greenfoot.*;

/**
 * A simple button: a label on a rounded rectangle. Check it with
 * Greenfoot.mouseClicked(button).
 */
public class Button extends Actor
{
    public Button(String text)
    {
        GreenfootImage img = new GreenfootImage(120, 34);
        img.setColor(new Color(90, 160, 90));
        img.fillRect(0, 0, 120, 34);
        img.setColor(Color.WHITE);
        img.drawRect(0, 0, 119, 33);
        img.setFont(new Font("SansSerif", true, false, 16));
        img.drawCenteredString(text, 60, 22);
        setImage(img);
    }
}
