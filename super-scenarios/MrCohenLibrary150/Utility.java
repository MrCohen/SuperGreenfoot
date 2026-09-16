import greenfoot.*;
/**
 * An assortment of useful static methods.
 * 
 * Some of these are also embedded into the individual classes so that those classes
 * can function as standalone, but you can use these instead  by calling them from your classes.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class Utility  
{

    // =============== TEXT CENTERING METHODS ===============================================

    /**
     * <h3>Finally, draw centered text in Greenfoot!</h3>
     * <p> This method requires an image (set Font first! it's attached to your image when you pass it),
     *     as well as some text to center, the X coordinate about which you want the text centered, and
     *     the y coordinate for the baseline that this text should be written on.</p>
     * <p>
     * <b>IMPORTANT:</b> Set your Font in your GreenfootImage before you send it here.
     * </p>
     * <p>Use this instead of Greenfoot.drawString to center your text, or just call 
     *      <code>Utility.getStringWidth()</code> (see below) directly and draw it yourself 
     *      if you prefer the control over the ease of use.</p>
     * 
     * @param canvas    The GreenfootImage that you want to draw onto, often the background of a World, but
     *                  could also be an Actor's image or any other image.
     * @param text      The text to be drawn.
     * @param middleX   the x Coordinate that the text should be centered on
     * @param bottomY   the y Coordinate at the baseline of the text (similar to GreenfootImage.drawString)
     * 
     * @since June 2021
     */
    public static void drawCenteredText (GreenfootImage canvas, String text, int middleX, int bottomY){
        canvas.drawString (text, middleX - (getStringWidth(canvas.getFont(), text)/2), bottomY);
    }

    /**
     * <p> Center a String on a Canvas via the getStringWidth method. This version will center
     *      text on the GreenfootImage passed in, so no x coordinate is required.
     * <p>
     * <b>IMPORTANT:</b> Set your Font in your GreenfootImage before you send it here.
     * </p>
     * <p>Similar to the method above, except it always centers the text on the whole image
     *    instead of a specified x position. UNTESTED!</p>
     * 
     * @param canvas    The GreenfootImage that you want to draw onto, often the background of a World, but
     *                  could also be an Actor's image or any other image.
     * @param text      The text to be drawn.
     * @param bottomY   the y Coordinate at the baseline of the text (similar to GreenfootImage.drawString)
     * 
     * @since June 2021
     */
    public static void drawCenteredText (GreenfootImage canvas, String text, int bottomY){
        canvas.drawString (text, canvas.getWidth()/2 - (getStringWidth(canvas.getFont(), text)/2), bottomY);
    }

    /**
     * <h3>Mr. Cohen's Text Centering Algorithm</h3>
     * 
     * <p>Get the Width of a String, if it was printed out using the drawString command in a particular
     * Font.</p>
     * <p>There is a performance cost to this, although it is more significant on the Gallery, and 
     * especially on the Gallery when browsed on a mobile device. It is appropriate to call this in the 
     * constructor, and in most cases it is ideal NOT to call it from an act method, especially
     * every act.</p>
     * 
     * <p>In cases where values are pre-determined, it may be ideal to cache the values (save them) so 
     * you don't have to run this repeatedly on the same text. If you do this in the World constructor,
     * there is no performance cost while running.</p>
     * 
     * <h3>Performance & Compatibility:</h3>
     * <ul>
     *  <li> Locally, performance should be sufficient on any moderate computer (average call 0.1-0.2ms on my laptop)</li>
     *  <li> To be compatible with Greenfoot Gallery, removed use of getAwtImage() and replaced with getColorAt() on a GreenfootImage</li>
     *  <li> On Gallery, performance is about 10x slower than locally (4ms on Gallery via Computer). For reference, an act() should be
     *       less than 16.6ms to maintain 60 frames/acts per second. </li>
     *  <li> HUGE performance drop on Gallery via Mobile devices - not sure why, going to ignore for now. (Average update duration 34ms, more
     *       than 2 optimal acts)</li>
     * </ul>
     * 
     * @param font the GreenFoot.Font which is being used to draw text
     * @param text the actual text to be drawn
     * @return int  the width of the String text as draw in Font font, in pixels.
     * 
     * @since June 2021
     * @version December 2021 - Even more Efficiency Improvement - sub 0.06ms per update on setSpeed(100)!
     */
    public static int getStringWidth (Font font, String text){

        // Dividing font size by 1.2 should work for even the widest fonts, as fonts are
        // taller than wide. For example, a 24 point font is usually 24 points tall 
        // height varies by character but even a w or m should be less than 20 wide
        // 24 / 1.2 = 20
        int maxWidth = (int)(text.length() * (font.getSize()/1.20));//1000; 
        int fontSize = font.getSize();
        int marginOfError = fontSize / 6; // how many pixels can be skipped scanning vertically for pixels?
        int checkX;

        GreenfootImage temp = new GreenfootImage (maxWidth, fontSize);
        temp.setFont(font);
        temp.drawString (text, 0, fontSize);

        //int testValue = 1000;
        boolean running = true;

        checkX = maxWidth - 1;
        while(running){
            boolean found = false;
            for (int i = fontSize - 1; i >= 0 && !found; i-=marginOfError){

                if (temp.getColorAt(checkX, i).getAlpha() != 0){
                    // This lets me only look at every other pixel on the first run - check back one to the right
                    // when I find a pixel to see if I passed the first pixel or not. This should almost half the 
                    // total calls to getColorAt().
                    if (temp.getColorAt(checkX + 1, i).getAlpha() != 0){
                        checkX++;
                        if (temp.getColorAt(checkX + 1, i).getAlpha() != 0){
                            checkX++;
                        }
                    }
                    found = true;
                }
            }
            if (found){
                return checkX;
            }
            checkX-=3; // shift 3 pixels at a time in my search - above code will make sure I don't miss anything
            if (checkX <= marginOfError)
                running = false;
        }
        return 0;

    }

    // =============== DRAWING METHODS ===============================================

    public static GreenfootImage drawGridLines (int width, int height, int distApart){
        GreenfootImage temp = new GreenfootImage (width, height);
        temp.setColor (Color.WHITE);
        temp.fill();
        temp.setColor (Color.BLACK);
        int totalLines = height / distApart;
        for (int i = 0; i < totalLines; i++){
            temp.drawLine(0, i * distApart, width, i * distApart);
        }
        return temp;
    }

    /**
     * density should be 1-100. 100 will be almost completely white
     */
    public static GreenfootImage drawSpace (int width, int height, int density){

        Color[] swatch = new Color [64];
        int red = 128;
        int blue = 192;

        // Build a color pallete out of shades of near-white yellow and near-white blue      
        for (int i = 0; i < swatch.length/2; i++){ // first half blue tones
            swatch[i] = new Color (red, 240, 255);
            red += 2;
        }
        for (int i = swatch.length/2; i < swatch.length; i++){ // second half yellow tones
            swatch[i] = new Color (255, 255, blue);
            blue ++;
        }

        // The temporary image, my canvas for drawing
        GreenfootImage temp = new GreenfootImage (width, height);
        temp.setColor (Color.BLACK);
        temp.fill();

        // Run this loop one time per "density"

        for (int i = 0; i < density; i++){
            for (int j = 0; j < 100; j++){ // draw 100 circles
                int randSize;
                // Choose a random colour from my swatch, and set its tranparency randomly
                int randColor = Greenfoot.getRandomNumber(swatch.length);
                int randTrans = Greenfoot.getRandomNumber(220) + 35; // around half transparent
                temp.setColor (swatch[randColor]);

                //setTransparency(randTrans);
                // random locations for our dot
                int randX = Greenfoot.getRandomNumber (width);
                int randY = Greenfoot.getRandomNumber (height);

                int tempVal = Greenfoot.getRandomNumber(250);
                if (tempVal >= 1){
                    //randSize = 2;
                    temp.drawRect (randX, randY, 0, 0);
                }else{
                    randSize = Greenfoot.getRandomNumber (2) + 2;
                    temp.fillOval (randX, randY, randSize, randSize);
                }
                // silly way to draw a dot..
            }
        }

        return temp;
    }

    // =============== UTILITY & MATH METHODS ===============================================
    /**
     * Static method that gets the distance between the x,y coordinates of two Actors
     * using Pythagorean Theorum.
     *
     * @param a     First Actor
     * @param b     Second Actor
     * @return distance The distance from the center of a to the center of b.
     */
    public static double getDistance (Coordinate a, Coordinate b)
    {
        double distance;
        double xLength = a.getX() - b.getX();
        double yLength = a.getY() - b.getY();
        distance = Math.sqrt(Math.pow(xLength, 2) + Math.pow(yLength, 2));
        return distance;
    }

    /**
     * Very useful math function that is missing from Java - enforces a max
     * and a min.
     * 
     * @param val   The value to be clamped
     * @param min   The minimum to be enforced
     * @param max   The maximum to be enforced
     * @return int  The value, now guaranteed to be between min and max, inclusive
     */
    public int clamp (int val, int min, int max){
        return Math.max(min, Math.min(max, val));
    }
}
