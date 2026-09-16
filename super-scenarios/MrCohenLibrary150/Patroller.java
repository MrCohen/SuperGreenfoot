import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * An example follower for the {@link Path} class.
 *
 * A Patroller is handed a Path and a speed, and every act it simply follows that
 * path. Several Patrollers can share the same Path object at different speeds -
 * each one keeps its own progress.
 *
 * This version turns to face the way it is travelling. For a version that does
 * NOT rotate at all, swap the line in act() for:  path.follow(this, speed);
 *
 * @author Jordan Cohen
 * @version June 2026
 */
public class Patroller extends SuperSmoothMover
{
    private SuperPath path;
    private double speed;

    /**
     * Create a Patroller that will follow the given path at the given speed.
     *
     * @param path   the Path to follow
     * @param speed  how many pixels to travel along the path each act
     */
    public Patroller (SuperPath path, double speed)
    {
        this.path = path;
        this.speed = speed;
    }

    /**
     * Each act, follow the path. followAndTurn moves the Patroller along the
     * path and turns it to face the direction of travel.
     */
    public void act ()
    {
        path.followAndTurn(this, speed);

        // Other things you could use instead:
        //   path.follow(this, speed);              // move only, never rotate
        //   path.followAndTurn(this, speed, 3.0);  // move and turn smoothly (<= 3 deg/act)
    }
}
