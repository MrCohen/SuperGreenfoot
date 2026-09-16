import greenfoot.*;

/**
 * SuperGreenfoot Phase 1 demo: smooth rendering, fractional movement and
 * rotation, per-actor z, y-sorting, and centred text.
 *
 * Press Run. The left orbiter uses whole-pixel Greenfoot movement (it steps);
 * the right orbiter uses move(double)/turn(double) with smooth rendering (it glides).
 * The walkers at the bottom are y-sorted: whichever is lower on screen is in front.
 * The shadow under each walker uses z = -1 so it always draws beneath its walker.
 */
public class DemoWorld extends World
{
    public DemoWorld()
    {
        super(640, 400, 1);
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
    }
}
