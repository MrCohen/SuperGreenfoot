import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * This Player demonstrates how to use the SuperSmoothMover class. See notes in 
 * SuperSmoothMover for details.
 * 
 * @author Jordan Cohen 
 * @version 1.10 (December 2021)
 */
public class Player extends SuperSmoothMover
{
    // Player size
    private final int WIDTH = 48; 
    private final int HEIGHT = 48;

    // Physics variables
    private double thrust = .08; 
    private double decay = 0.992;
    private double maxSpeed = 4.0;
    private double minSpeed = 0.1;
    private double turningSpeed = 10.0;
    private double speed;
    private double xSpeed, ySpeed;

    // Drawing variables
    private GreenfootImage normalImage, accelImage;
    private int armorRings;
    private boolean thrusting;

    private SuperStatBar stats;
    private SuperSpeechBubble bubble;
    
    public Player () {
        armorRings = 2;
        normalImage = drawShip(false);
        accelImage = drawShip(true);
        setImage (normalImage);
        stats = new SuperStatBar (400, 0, this, 48, 8, 42, Color.YELLOW, Color.GREEN, true);
        bubble = new SuperSpeechBubble(this, 100, 150, 50, 25, 30, "Hello World", true, false);
        //enableStaticRotation(); // test code --> will lock angle, doesn't look good! but my methods work.
    }

    public void addedToWorld (World w){
        // insert stat bar at 0,0 --> You don't have to specify a coordinate because the bar manages itself by
        // following this object around, adjusted by the given offset on the y axis
        w.addObject(stats, 0, 0);
        w.addObject(bubble, 0, 0);
    }

    /**
     * Act - do whatever the Player wants to do. This method is called whenever
     * the 'Act' or 'Run' button gets pressed in the environment.
     */
    public void act()
    {    
        checkKeys();

        setLocation (getPreciseX() + xSpeed, getPreciseY() - ySpeed);
    }

    private void checkKeys(){
        if (Greenfoot.isKeyDown("right")){
            rotateCW();
        } else if (Greenfoot.isKeyDown("left")){
            rotateCCW();
        }

        if (Greenfoot.isKeyDown("up")){
            thrustControl(true);
        } else {
            thrustControl(false);
        }
    }

    public void rotateCW (){
        setRotation (getPreciseRotation() + turningSpeed);
    }

    public void rotateCCW () {
        setRotation (getPreciseRotation() - turningSpeed);
    }

    public void thrustControl (boolean active){
        if (active){
            // Add thrust based on my current rotation
            ySpeed -= thrust * Math.sin(getPreciseRotation() * (Math.PI / 180.0));
            xSpeed += thrust * Math.cos(getPreciseRotation() * (Math.PI / 180.0));
            setImage(accelImage);

            speed = Math.sqrt((xSpeed * xSpeed) + (ySpeed * ySpeed));
            if (speed > maxSpeed){
                xSpeed *= maxSpeed / speed;
                ySpeed *= maxSpeed / speed;
            }

            thrusting = true; // for drawing
        } else {
            xSpeed *= decay;
            ySpeed *= decay;
            speed = Math.sqrt((xSpeed * xSpeed) + (ySpeed * ySpeed));
            if (speed < minSpeed){
                xSpeed = 0;
                ySpeed = 0;
            }
            setImage(normalImage);
            thrusting = false; // for drawing
        }
        stats.update ((int)(speed * 100));
    }

    /**
     * Draw a triangular ship facing right.
     */
    private GreenfootImage drawShip (boolean accel){
        GreenfootImage ship = new GreenfootImage (WIDTH, HEIGHT);

        int[] xCorners = {0, WIDTH, 0};
        int[] yCorners = {0, HEIGHT / 2, HEIGHT};

        // Draw on Weapons first, so they are overlapped
        ship.setColor(Color.GREEN);
        ship.fillRect (0, 0, WIDTH*3 / 4, HEIGHT/8);
        ship.fillRect (0, HEIGHT*7/8, WIDTH*3 / 4, HEIGHT/8);

        ship.setColor(Color.WHITE);
        ship.drawRect (0, 0, WIDTH*3 / 4, HEIGHT/8);
        ship.drawRect (0, HEIGHT*7/8, WIDTH*3 / 4, HEIGHT/8);

        ship.setColor (Color.BLUE);
        ship.fillPolygon(xCorners, yCorners, 3);

        for (int i = 0; i < armorRings; i++){
            int[] xC = {xCorners[0] + (4 * i), xCorners[1] - (8 * i),  xCorners[2] + (4 * i)};
            int[] yC = {yCorners[0] + (6 * i), yCorners[1], yCorners[2] - (6 * i)};
            ship.setColor (Color.GRAY);

            ship.drawPolygon(xC, yC, 3);
        }

        // Draw on "Thrust" if accelerating
        if (accel){
            ship.setColor (Color.RED);
            ship.fillRect (0,0,4, HEIGHT);
            ship.fillRect (4, 2, 4, HEIGHT - 4);
        }

        return ship;

    }
}
