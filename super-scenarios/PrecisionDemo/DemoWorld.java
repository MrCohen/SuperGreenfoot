import greenfoot.*;

/**
 * SuperGreenfoot Phase 1 demo: smooth rendering, fractional movement and
 * rotation, per-actor z, y-sorting, and centred text.
 *
 * Press Run. The left orbiter uses whole-pixel Greenfoot movement (it steps);
 * the right orbiter uses move(double)/turn(double) with smooth rendering (it glides).
 * The walkers in the middle are y-sorted: whichever is lower on screen is in front.
 * The shadow under each walker uses z = -1 so it always draws beneath its walker.
 *
 * The grove at the bottom shows the sort anchor. Press A to switch between
 * ZSortAnchor.CENTER and ZSortAnchor.BOTTOM and watch the walker pass among the
 * trees. With CENTER the trees sort by the middle of their pictures, so the walker
 * strolls in front of a tree it is clearly standing behind; with BOTTOM they sort by
 * the foot of the trunk, which is where they actually stand. The tree pictures are no
 * taller than what they draw - with CENTER the only fix would be to pad each one with
 * empty rows until its trunk reached the middle, at nearly twice the memory.
 */
public class DemoWorld extends World
{
    private Label anchorLabel = new Label(anchorText(ZSortAnchor.CENTER));

    public DemoWorld()
    {
        super(640, 540, 1);
        setSmoothRendering(true);
        setZSortByY(true);
        setPaintOrder(Label.class);   // labels always on top, whatever their y

        GreenfootImage bg = getBackground();
        bg.setColor(new Color(30, 30, 40));
        bg.fill();

        addObject(new Orbiter(false), 160, 130);   // integer movement
        addObject(new Orbiter(true), 480, 130);    // precise movement

        Label l1 = new Label("move(int) / turn(int)\nsteps");
        Label l2 = new Label("move(double) / turn(double)\nglides");
        addObject(l1, 160, 240);
        addObject(l2, 480, 240);

        for (int i = 0; i < 5; i++) {
            Walker w = new Walker(0.4 + i * 0.25);
            addObject(w, 60 + i * 120, 300 + (i % 2) * 40);
            addObject(new Shadow(w), w.getX(), w.getY() + 18);
        }
        addObject(new Label("y-sorted walkers with z = -1 shadows"), 320, 385);

        // The grove: unpadded tree pictures, sorted by the foot of the trunk.
        int[] heights = {104, 78, 118, 88, 96};
        for (int i = 0; i < heights.length; i++) {
            addObject(new Tree(heights[i]), 70 + i * 125, 430 + (i % 2) * 34);
        }
        addObject(new Walker(0.9, 410, 500), 320, 470);
        addObject(anchorLabel, 320, 528);
    }

    public void act()
    {
        if ("a".equals(Greenfoot.getKey())) {
            ZSortAnchor next = getZSortAnchor() == ZSortAnchor.BOTTOM
                    ? ZSortAnchor.CENTER : ZSortAnchor.BOTTOM;
            setZSortAnchor(next);
            removeObject(anchorLabel);
            anchorLabel = new Label(anchorText(next));
            addObject(anchorLabel, 320, 528);
        }
    }

    private static String anchorText(ZSortAnchor anchor)
    {
        return "A: sort anchor = " + anchor
                + (anchor == ZSortAnchor.BOTTOM ? "  (trees stand where their trunks are)"
                                                : "  (trees sort by the middle of the picture)");
    }
}
