/**
 * A simple way to manage x,y pairs with precision. 
 * 
 * Now with built in (optional) angle variable.
 * 
 * All values are stored as double, but can be set or retrieved as 
 * int or double.
 * 
 * (Graphics2D's Point2D class is similar, but not supported
 * on the Greenfoot Gallery)
 * 
 * @author Jordan Cohen
 * @version November 2021
 */
public class Coordinate {
    private double x;
    private double y;
    private double angle; // in degrees

    /**
     * Create a new coordinate using integers.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Coordinate (int x, int y){
        this.x = (double)x;
        this.y = (double)y;
        angle = 0;
    }

    /**
     * Create a new coordinate using integers.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Coordinate (int x, int y, int angle){
        this.x = (double)x;
        this.y = (double)y;
        this.angle = angle;
    }

    /**
     * Create a new coordinate using doubles.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Coordinate (double x, double y){
        this.x = x;
        this.y = y;
        angle = 0;
    }

    /**
     * Create a new coordinate using doubles.
     * 
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Coordinate (double x, double y, double angle){
        this.x = x;
        this.y = y;
        this.angle = angle;
    }

    /**
     * Set a location precisely using doubles
     * 
     * @param x the new x location
     * @param y the new y location
     */
    public void setLocation (double x, double y){
        this.x = x;
        this.y = y;
    }

    /**
     * Set a location using integers
     * 
     * @param x the new x location
     * @param y the new y location
     */
    public void setLocation (int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    /**
     * Set a new rotation with an integer
     * 
     * @param angle the new angle, in degrees
     */
    public void setRotation (int angle){
        this.angle = angle;
    }

    /**
     * Set a new rotation precisely with a double
     * 
     * @param angle the new angle, in degrees
     */
    public void setRotation (double angle){
        this.angle = angle;
    }

    /** 
     * Set a new x coordinate with an integer
     * 
     * @param x the new x coordinate
     */
    public void setX(int x){
        this.x = x;
    }

    /** 
     * Set a precise new x coordinate with a double
     * 
     * @param x the new x coordinate
     */
    public void setX(double x){
        this.x = x;
    }

    /** 
     * Set a new y coordinate with an integer
     * 
     * @param y the new y coordinate
     */
    public void setY (int y ){
        this.y = y;
    }

    /** 
     * Set a precise new y coordinate with a double
     * 
     * @param y the new y coordinate
     */
    public void setY (double y ){
        this.y = y;
    }

    /**
     * Retrieve the precise angle, in degrees
     * 
     * @return double   the precise angle, in degrees
     */
    public double getPreciseRotation (){
        return angle;
    }

    /**
     * Retrieve the angle, rounded to the nearest int. Note that this does not
     * round the internal angle variable, only the returned value.
     * 
     * @return int   the angle, in degrees
     */
    public int getRotation () {
        return (int) (angle + 0.5);
    }

    /**
     * Retrieve the rounded x coordinate. Note that this does not
     * round the internal angle variable, only the returned value.
     * 
     * @return int the x coordinate, rounded to a whole number
     */
    public int getX(){
        return (int)(x  + 0.5);
    }

    /**
     * Retrieve the rounded x coordinate. Note that this does not
     * round the internal angle variable, only the returned value.
     * 
     * @return int the x coordinate, rounded to a whole number
     */
    public int getY(){
        return (int)(y + 0.5);
    }

    /**
     * Retrieve the precise x coordinate.
     * 
     * @return double the precise x coordinate
     */
    public double getPreciseX(){
        return x;
    }

    /**
     * Retrieve the precise y coordinate.
     * 
     * @return double the precise y coordinate
     */
    public double getPreciseY(){
        return y;
    }

    /**
     * Change x by a given amount.
     * 
     * @param value the value by which to change x, can be positive or negative
     */
    public void dX (double value){
        this.x += value;
    }

    /**
     * Change y by a given amount.
     * 
     * @param value the value by which to change y, can be positive or negative
     */
    public void dY (double value){
        this.y += value;
    }

    /**
     * Rotate the angle by a given amount.
     * 
     * @param value the value by which to rotate the angle, can be positive or negative
     */
    public void rotate (int degrees){
        angle += degrees;
    }

    /**
     * Rotate the angle by a given precise amount.
     * 
     * @param value the value by which to rotate the angle, can be positive or negative
     */
    public void rotate (double degrees){
        angle += degrees;
    }

    /**
     * toString method. Returns a String containing all three internal variables. This 
     * can be useful for troubleshooting, and is not intended to be callled by live code.
     * 
     * @return String   a string representation with x, y and angle
     */
    public String toString () {
        
        return "x: " + x + ", y: " + y + " angle: " + angle;
    }
}
