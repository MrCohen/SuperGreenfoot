import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * <p>A useful label to display game score, stats, or other texts.</p>
 * 
 * <p>This is effectively a one-line text box that automatically fills the width of the World</p>
 * <p>Height can be specified directly, but otherwise is calculated based on the Font Size and the HEIGHT_RATIO -- for example
 *    20 point font with a HEIGHT_RATIO of 2.0 results in a height of 2.0 * 20 = 40 pixels</p>
 * <h3><b>Version 1.2<.b></h3>
 * <ul>
 *   <li>Improved API - more update methods for greater flexibility</li>
 *   <li>More Constructors - for more customizability</li>
 *   <li>Auto-width - now fits itself to the Width of the World (for other size, consider SuperTextBox instead)</li>
 *   <li>Added constants for DEFAULTS</li>
 * </ul>
 *   
 * 
 * @author Jordan Cohen
 * @since November 2015 (formerly ScoreBar)
 * @version 1.2 (Jan 2023)
 */
public class SuperDisplayLabel extends Actor
{
    // Ratio of Height : FontSize
    // I.e. 1.4 means if font size is 10, height would be 14
    private static final double HEIGHT_RATIO = 2.0;

    private static final Color DEFAULT_BACKGROUND_COLOR = new Color (167, 240, 91);
    private static final Color DEFAULT_TEXT_COLOR = Color.BLACK;

    private static final Font DEFAULT_FONT = new Font ("Comic Sans MS", false, false, 20);

    private GreenfootImage image;
    private Color backColor;
    private Color foreColor;
    private Font textFont;
    private String text;
    private String lastOutput;
    private String[] labels;
    //private String[] stringValues;
    private int[] intValues;
    private int centeredX;
    private int bottomY, height;

    /**
     * The Detailed Constructor
     * <p>This constructor presents the option to specifying a size. Ensure that the size is large enough to fit
     * the font otherwise this will not look right</p>
     */
    public SuperDisplayLabel(Color backColor, Color foreColor, Font font, int height){

        //System.out.println(bottomY);
        // Declare colour objects for use within this class (red and white)
        this.backColor = backColor;
        this.foreColor = foreColor;
        // Initialize the font - chose Courier because it's evenly spaced
        textFont = font;
        this.height = height;
        text = "";

    }

    /**
     * Font and Colour Choice Constructor 
     * <p>Note that the Font will be used to set the height when using this constructor</p>
     * @param backColor the Background Colour
     * @param foreColor the Text Colour
     * @param font      the Text Font, also used to calculate Height
     */
    public SuperDisplayLabel(Color backColor, Color foreColor, Font font){
        this (backColor, foreColor, font, (int)(font.getSize() * HEIGHT_RATIO));
    }

    /**
     * Font Only Constructor.
     * <p>Note that the Font will be used to set the height when using this constructor</p>
     */
    public SuperDisplayLabel (Font font)
    {
        this ( DEFAULT_BACKGROUND_COLOR, DEFAULT_TEXT_COLOR , font);      
    }

    /**
     * Simple constructor - Uses default fonts and colours and auto-height
     */
    public SuperDisplayLabel (){
        this (DEFAULT_FONT);
    }

    /**
     * Set labels without providing values.
     * 
     * @param labels    a set of Strings to represent the stats to be shown
     */
    public void setLabels (String[] labels){
        this.labels = labels;
    }

    /**
     * This is called automatically when the SuperDisplayLabel is added to the World.
     * 
     * Uses the size of the World to determine the width, and then draws itself and placed itself centered
     * and along the top edge.
     */
    public void addedToWorld (World w){
        image = new GreenfootImage (w.getWidth(), height);

        bottomY = image.getHeight() - (int)((image.getHeight() - textFont.getSize())/1.8);
        // Set the colour to red and fill the background of this rectangle
        image.setColor(backColor);
        image.fill();
        image.setFont(textFont);
        // Assign the image we just created to be the image representing THIS actor
        this.setImage (image);
        // Prepare the font for use within the code
        setLocation (w.getWidth() / 2, getImage().getHeight() / 2);
    }

    /**
     * Method to update the value shown on the score board. The size of both arrays must be the same.
     * 
     * @param labels    an array of Strings to label the various stats to be shown
     * @param newValues an array of ints to show as values
     */
    public void update (String[] labels, int[] newValues)
    {
        if (labels.length != newValues.length){
            System.out.println("ERROR - Both arrays must be the same size");
        }
        this.labels = labels;
        this.intValues = newValues;
        // Arrays should be the same size, but just in case, only loop to the lower length to avoid crashing
        int loops = Math.min (labels.length, newValues.length);
        String text = "";
        for (int i = 0; i < loops; i++){
            text += labels[i] + " " + newValues[i];
            if (i < loops - 1){
                text += " "; // extra space, but not for the very last loop
            }

        }
        update (text, true);
    }

    /**
     * Method to update the value shown on the TextBox. Must be the same length as the labels array.
     * 
     * @param newValues an array of ints that is the same length as the labels that were set
     */
    public void update (int[] newValues)
    {
        update (labels, newValues);
    }

    /**
     * If you used this Display to show a String of text and want to go back to label: value style, use this method
     */
    public void update () {
        update (labels, intValues);
    }

    /**
     * Simplest Update method - used to display a centered String.
     * 
     * @param output    The String to be displayed in the center of this SuperDisplayLabel.
     */
    public void update (String output){
        update (output, true);
    }

    /**
     * Takes a String and displays it centered to the screen. Note this gets called by the other
     * update() method, but can also be called separate when you want to display a String rather 
     * than a bunch of labels and values. This is the only method that actually updates the Image.
     * 
     * @param   output  A string to be output, centered on the screen.
     * @param   recenter    if true, this will recalculate and center the text. Use this
     *                      sparingly, as the recalculation is fairly demanding on the CPU.
     */
    public void update (String output, boolean recenter)
    {
        // Skip this method if the text is the same as before, to avoid extra cycles
        if (output.equals(lastOutput)){
            return;
        }
        // Refill the background with background color
        image.setColor(backColor);
        image.fill();

        // Write text over the solid background
        image.setColor(foreColor);  
        image.setFont (textFont);
        if (recenter){
            // Smart piece of code that centers text
            centeredX = getImage().getWidth()/2 - getStringWidth(textFont, output)/2;

        }

        // Draw the text onto the image
        image.drawString(output, centeredX, bottomY);

        lastOutput = output;
    }

    /**
     * <h3>Finally, draw centered text in Greenfoot!</h3>
     * <p>
     * <b>IMPORTANT:</b> Set your Font in your GreenfootImage before you send it here.
     * </p>
     * <p>Use this instead of Greenfoot.drawString to center your text, or just call getStringWidth
     *    directly and draw it yourself if you prefer the control over the ease of use.</p>
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
}
