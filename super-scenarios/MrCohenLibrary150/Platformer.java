import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * A Platformer is a Superclass which contains all of the relevant 
 * values to simulate the faux-physics for either a playter controlled
 * character or a computer controlled enemy.
 * 
 * @author Jordan Cohen
 * @version June 2025
 */
public class Platformer extends SuperSmoothMover
{
    protected GreenfootImage image;
    // If I'm on a vertical elevator, this will be it, otherwise null
    protected VerticalPlatform elevator;

    // values for movement / faux physics
    protected double hSpeed, vSpeed, accel, jumpStrength, maxVSpeed, maxHSpeed;
    protected static final double TOLERANCE = 3.0; // higher = better performance, but don't make platforms thinner than this
    // how fast horizontal speed is scrubbed 
    protected static final double H_DECAY = 0.03;

    // state - is jumping?
    protected boolean jumping;
    protected boolean justLanded;

    // horizontal direction (for choosing frame, and for movement calculations)
    protected int hDirection;

    protected void checkFalling () {
        // check for a platform within vSpeed
        Platform p = getPlatformInRange(vSpeed);

        // if I'm within vSpeed distance of the platform (in other words,
        // if I'm going to land on it this act).
        if (p != null){
            // Check if the platform is an elevator - which as movement implications
            // dealt with below
            if (p instanceof VerticalPlatform){
                elevator = (VerticalPlatform)p;
            }
            // place me directly and exactly upon the platform (rather than 
            // moving at my falling speed, which would make me fall into the platform)
            setLocation (getX(), p.getY() - p.getImage().getHeight() / 2 - this.getImage().getHeight()/2);
            // since I'm on a platform, now my vertical speed should be zero ...
            vSpeed = 0;

            // ...and I'm no longer jumping.
            jumping = false;
        }

        // Deal with vertical elevators
        if (elevator != null){
            setLocation (getX(), elevator.getY() - elevator.getImage().getHeight() / 2 - getImage().getHeight() / 2);
        }

        // Check below me to see if I'm not on a platform (still falling, or just waled
        // off a cliff)
        if (!onPlatform(0.5)){
            vSpeed += accel;
            elevator = null;
        } else if (vSpeed > 0) { // If I was falling, and I'm now on a platform, stop falling
            vSpeed = 0;     
        }
    }

    // ensure not already jumping and on platform, and if so, jump
    // by adding -jumpStrength to my vertical speed. Remember, it's negavtive
    // because y values get smaller as they move upward (so if jumpStrength is
    // 10.0, that means that the player will move -10 on y in their first act jumping
    protected void jump () {
        if (!jumping && onPlatform(0.5) && !justLanded){
            jumping = true;
            elevator = null;
            vSpeed = -jumpStrength;
        }
        
    }

    // Simple method to check if on a platform. Use 0.5 offset for best results.
    protected boolean onPlatform (double offset) {
        Platform pLeft = (Platform) getOneObjectAtOffset (-getImage().getWidth() / 2, getImage().getHeight()/2 + (int)(vSpeed + offset), Platform.class);
        Platform pCenter = (Platform) getOneObjectAtOffset (0, getImage().getHeight()/2 + (int)(vSpeed + offset), Platform.class);
        Platform pRight = (Platform) getOneObjectAtOffset (getImage().getWidth() / 2, getImage().getHeight()/2 +  (int)(vSpeed + offset), Platform.class);
        return pLeft != null || pCenter != null || pRight != null;
    }

    // Check in the given range for a platform, skipping pixels by TOLERANCE to save
    // extra processing
    protected Platform getPlatformInRange (double range){
        for (double offset = 0; offset < range; offset += TOLERANCE){
            Platform pLeft = (Platform) getOneObjectAtOffset (-getImage().getWidth() / 2, getImage().getHeight()/2 + (int)(vSpeed + offset), Platform.class);
            Platform pCenter = (Platform) getOneObjectAtOffset (0, getImage().getHeight()/2 + (int)(vSpeed + offset), Platform.class);
            Platform pRight = (Platform) getOneObjectAtOffset (getImage().getWidth() / 2, getImage().getHeight()/2 +  (int)(vSpeed + offset), Platform.class);
            if (pLeft != null) return pLeft;
            if (pRight != null) return pRight;
            if (pCenter != null) return pCenter;
        }
        return null;
    }
}

class Platform extends SuperSmoothMover
{
    private GreenfootImage image;
    public Platform (int width, int height) {
        image = new GreenfootImage (width, height);
        image.setColor(Color.BLACK);
        image.fill();
        setImage(image);
    }

    public int[] getBounds (){
        return new int[]{this.getX() - getImage().getWidth()/2, this.getX() + getImage().getWidth() / 2};
    }
}

class VerticalPlatform extends Platform
{
    private int yTop, yBot;
    private int direction;
    private boolean moving;
    private double speed;

    public VerticalPlatform (int width, int height, int yTop, int yBot, int direction, double speed){
        super (width, height);
        this.yTop = yTop;
        this.yBot = yBot;
        this.direction = direction; // should be -1 or 1, or 0 for not moving
        moving = (direction != 0);
        this.speed = speed;
    }

    public void act()
    {
        autoMove();

    }

    private void autoMove() {
        if (direction == 1){ // moving downward
            if (getY() + (speed * direction) >= yBot){
                setLocation (getX(), yBot);
                direction *= -1;
            }
            else {
                setLocation (getX(), getY() + (speed * direction));
            }
        } else if (direction == -1){
            if (getY() + (speed * direction) <= yTop){
                setLocation (getX(), yTop);
                direction *= -1;
            }
            else {
                setLocation (getX(), getY() + (speed * direction));
            }
        }
    }
}

