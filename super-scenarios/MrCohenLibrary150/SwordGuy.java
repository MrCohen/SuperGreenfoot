import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * Write a description of class SwordGuy here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class SwordGuy extends SuperSmoothMover
{
    private Animation swordAnimation;
    private Direction direction;
    private double speed;
    private double animSpeed;
    private long actStart, lastAct;
    private long frameStart;
    private final double MAX_FRAME_DURATION = 0.12; // longest frame lenght for rendering purposes
    private int currentFrame;
    private int horizontalDirection;
    private int currDirection;

    public SwordGuy () {
        direction = Direction.RIGHT;  

        swordAnimation = Animation.createAnimation(new GreenfootImage("sword_guy.png"), 9, 4, 9, 64, 64);

        currentFrame = 0; // 0 is idle frame
        setImage(swordAnimation.getOneImage(direction, currentFrame));

        speed = 0.9;        // 0.9 pixels per second (not act)
        animSpeed = 1/12.0; //  12 fps... each frame is 1/12 of a second
        actStart = System.nanoTime();
    }
    public void act () {
        // timing
        lastAct = actStart;
        actStart = System.nanoTime();
        // calculate time, then enforce max frame duration for deciding movement / animation
        double secondsElapsed = Math.min (MAX_FRAME_DURATION, actStart - lastAct / 1000000000.0);
        // Remember previous direction
        currDirection = horizontalDirection;
        
        // Perform tasks
        checkKeys();
        applyMove();
        animate();
    }

    private void checkKeys(){

        if (Greenfoot.isKeyDown("left")){
            horizontalDirection = -1;
            direction = Direction.LEFT;
        } else if (Greenfoot.isKeyDown("right")){
            horizontalDirection = 1;
            direction = Direction.RIGHT;
        } else {
            horizontalDirection = 0;
        }
    }

    private void applyMove(){
        setLocation (getX() + (horizontalDirection * speed), getY());
    }

    private void animate(){
        long theTime = System.nanoTime();
        if (horizontalDirection == 0){
            currentFrame = 0; // idle frame when not moving
            setImage(swordAnimation.getOneImage(direction, currentFrame));
        }
        else if (currDirection != horizontalDirection){
            currentFrame = 1; // reset when changing direction
            frameStart = System.nanoTime();
            setImage(swordAnimation.getOneImage(direction, currentFrame));
        }
        else if ((theTime - frameStart)/1000000000.0 > animSpeed){
            currentFrame++;
            if (currentFrame > 8){
                currentFrame = 1;
            }
            frameStart = System.nanoTime();
            setImage(swordAnimation.getOneImage(direction, currentFrame));
        }
    }

}
