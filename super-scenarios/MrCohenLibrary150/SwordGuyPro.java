import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * Write a description of class SwordGuyPro here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class SwordGuyPro extends Platformer
{
    private Animation swordAnimation;
    private Direction direction;

    private double animSpeed;
    private long actStart, lastAct;
    private long frameStart;
    private final double MAX_FRAME_DURATION = 0.12; // longest frame lenght for rendering purposes
    
    
    private int currentFrame;
    private int horizontalDirection;
    private int currDirection;
    private int halfHeight, halfWidth;
    

    private static final Class<?>[] platformClasses = {
            SuperWindow.class,
            SuperTextBox.class,
            Platform.class
        };

    public SwordGuyPro () {
        direction = Direction.RIGHT;  

        // Create an Animation object using the static method:
        swordAnimation = Animation.createAnimation(new GreenfootImage("sword_guy.png"), 9, 4, 9, 64, 64);
        swordAnimation = AnimationManager.trim(swordAnimation, 0, 0, 0, 2);
        // start idle
        currentFrame = 0; // 0 is idle frame
        setImage(swordAnimation.getOneImage(direction, currentFrame));
        vSpeed = 0;
        hSpeed = 1.5;        // 0.9 pixels per second (not act)
        animSpeed = 1/15.0; //  15 fps... each frame is 1/12 of a second
        actStart = System.nanoTime();

        halfHeight = getImage().getHeight() / 2;
        halfWidth = getImage().getWidth() / 2;

        accel = 0.8;
        jumpStrength = 14.5;
        maxVSpeed = 12.0;
        maxHSpeed = 5.0;
        jumping = false;
        justLanded = false;
    }

    public void act () {
        // timing
        lastAct = actStart;
        actStart = System.nanoTime();
        // calculate time, then enforce max frame duration for deciding movement / animation
        double secondsElapsed = Math.min (MAX_FRAME_DURATION, actStart - lastAct / 1000000000.0);
        // Remember previous direction
        currDirection = horizontalDirection;
        
        checkFalling();

        // Perform tasks
        checkKeys();
        applyMove();
        animate();
        justLanded = false;
    }

    protected void checkFalling() {
        Actor a = getPlatformActorInRange (vSpeed);
        if (a != null){
            // Maintain support for 'real' Platforms
            if (a instanceof Platform){
                Platform p = (Platform)a;
                if (p instanceof VerticalPlatform){
                    elevator = (VerticalPlatform)p;
                }
            }

            // place me directly and exactly upon the platform (rather than 
            // moving at my falling speed, which would make me fall into the platform)
            setLocation (getPreciseX(), a.getY() - a.getImage().getHeight() / 2 - halfHeight);
            // since I'm on a platform, now my vertical speed should be zero ...
            vSpeed = 0;

            // ...and I'm no longer jumping.
            jumping = false;
            justLanded = true;
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

    // Simple method to check if on a platform. Use 0.5 offset for best results.
    @Override
    protected boolean onPlatform(double offset) {
        int imgW = getImage().getWidth();
        int imgH = getImage().getHeight();
        int yOffset = imgH / 2 + (int)(vSpeed + offset);

        for (Class<?> cls : platformClasses) {
            if (getOneObjectAtOffset(-imgW / 2, yOffset, cls) != null) return true;
            if (getOneObjectAtOffset(0, yOffset, cls) != null) return true;
            if (getOneObjectAtOffset(imgW / 2, yOffset, cls) != null) return true;
        }

        return false;
    }


    private Actor getPlatformActorInRange(double range) {
        if (platformClasses == null || platformClasses.length == 0) return null;

        int imgW = getImage().getWidth();
        int imgH = getImage().getHeight();

        for (double offset = 0; offset < range; offset += TOLERANCE) {
            int yOffset = imgH / 2 + (int)(vSpeed + offset);

            for (Class<?> cls : platformClasses) {
                if (getOneObjectAtOffset(-imgW / 2, yOffset, cls) != null) return getOneObjectAtOffset(-imgW / 2, yOffset, cls);
                if (getOneObjectAtOffset(0, yOffset, cls) != null) return getOneObjectAtOffset(0, yOffset, cls);
                if (getOneObjectAtOffset(imgW / 2, yOffset, cls) != null) return getOneObjectAtOffset(imgW / 2, yOffset, cls);
            }
        }
        return null;
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

        if (Greenfoot.isKeyDown("up")){
            jump();
        }
    }

    private void applyMove(){
        setLocation (getPreciseX() + (horizontalDirection * hSpeed), getY() + vSpeed);
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
