import greenfoot.*;

/**
 * A tree drawn in a picture exactly as tall as what it draws, with the foot of
 * the trunk near the bottom edge.
 *
 * Without a sort anchor a picture like this has to be padded with empty rows
 * below it until the trunk's foot reaches the middle, or the tree sorts as if it
 * stood where its canopy is. That padding would make this image nearly twice as
 * tall for nothing. With World.setZSortAnchor(ZSortAnchor.BOTTOM) the picture
 * stays this size, and setZSortOffset trims the few pixels of shadow that are
 * drawn below the trunk's foot.
 */
public class Tree extends Actor
{
    /** How far the ground shadow reaches below the foot of the trunk. */
    private static final int SHADOW_DEPTH = 6;

    private static final int WIDTH = 46;

    public Tree(int height)
    {
        GreenfootImage img = new GreenfootImage(WIDTH, height);
        int foot = height - SHADOW_DEPTH;          // where the trunk meets the ground
        int trunkTop = foot - height / 3;
        int middle = WIDTH / 2;

        img.setColor(new Color(0, 0, 0, 70));
        img.fillOval(middle - 15, foot - 4, 30, SHADOW_DEPTH + 4);

        img.setColor(new Color(96, 68, 44));
        img.fillRect(middle - 4, trunkTop, 8, foot - trunkTop);

        img.setColor(new Color(44, 118, 72));
        img.fillOval(2, 0, WIDTH - 4, trunkTop + 8);
        img.setColor(new Color(62, 146, 92));
        img.fillOval(8, 4, WIDTH - 22, (trunkTop + 8) / 2);

        setImage(img);
        setZSortOffset(-SHADOW_DEPTH);
    }
}
