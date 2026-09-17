import greenfoot.*;

/** A text bar that only re-renders when its text changes. */
public class Readout extends Actor
{
    private String shown = null;

    public void update(String text)
    {
        if (text.equals(shown)) {
            return;
        }
        shown = text;
        Font font = new Font("SansSerif", false, false, 13);
        int w = Math.max(10, font.getStringWidth(text) + 16);
        GreenfootImage img = new GreenfootImage(w, 24);
        img.setColor(new Color(0, 0, 0, 160));
        img.fill();
        img.setColor(Color.WHITE);
        img.setFont(font);
        img.drawCenteredString(text, w / 2, 12);
        setImage(img);
    }
}
