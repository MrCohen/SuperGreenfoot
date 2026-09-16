import greenfoot.*;

/**
 * A text label sized from Font metrics and drawn with drawCenteredString.
 */
public class Label extends Actor
{
    public Label(String text)
    {
        Font font = new Font("SansSerif", false, false, 16);
        int w = font.getStringWidth(text) + 16;
        int h = font.getStringHeight(text) + 12;
        GreenfootImage img = new GreenfootImage(w, h);
        img.setColor(new Color(255, 255, 255, 40));
        img.fill();
        img.setFont(font);
        img.setColor(Color.WHITE);
        img.drawCenteredString(text, w / 2, h / 2);
        setImage(img);
    }
}
