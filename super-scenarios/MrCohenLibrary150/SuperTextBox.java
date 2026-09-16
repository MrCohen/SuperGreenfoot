import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * A flexible single- or multi-line Text Box.
 * 
 * <h2>Version 1.24 - Height Improvements!</h2>
 * 
 * <h3>1.24 (Jan 2024) Version Notes:</h3>
 * <ul>
 *  <li> Allowing for the text box to center itself <b>vertically</b> within it's space. This only works if
 *       the height specified is greater than the required height for the number of lines specified</li>
 *       
 * 
 * </ul>
 * 
 * <h3>(Old) 1.0 Notes</h3>
 * <ul>
 *  <li> Height is set automatically to fit. See below for a detailed description of how it's calculated. The easiest way to 
 *       get the height in order to position it is to have it build itself, and then call getHeight() on the created object.</li>
 *  <li> Border Thickness is optional --> Set it to zero (0) to create a box with no border</li>
 *  <li> If you turn on centered, the processing cost goes up dramatically upon updates. This is really only an issue if you plan
 *       to publish to the Greenfoot Gallery. If you are, please consider not using centered, or updating very sparingly. </li>
 * </ul>
 * 
 * <p> To understand <b>height</b> examine the following equation:</p>
 * <code>height = (padding * 2) + (vSpace * (numLines)) + (fontSize * numLines) + (2 * borderThickness);</code>
 * 
 * 
 * 
 * @author Jordan Cohen
 * @since 1.0 (November 30, 2021)
 * @version 1.24 
 */
public class SuperTextBox extends Actor
{

    // TODO --> Add Dialogue class to represent a pre-cached String or String[] with font and cached width and height and an optional
    //          cache in SuperTextBox

    private GreenfootImage image;
    private String[] text;
    private boolean centered, centeredVertically;
    private boolean bordered;
    private Color backColor;
    private Color foreColor;
    private Color borderColor;
    private Font font;
    private int[] centeredXs;
    private int[] centeredYs;
    private int numLines;
    private int vSpace;
    private int width, height;
    private int calculatedHeight, forcedHeight;
    private int padding;
    private int fontSize, fontHeight;
    private int borderThickness;

    /**
     *  Simple Constructor - One line text box
     *  
     *  @param line     The line of text to display
     *  @param font     The font to display
     *  @param width    The desired width of the text box in pixels
     */
    public SuperTextBox (String line, Font font, int width){
        this (new String[]{line}, font, width);
    }

    /**
     *  Simple Constructor - Multi-line text box
     *  
     *  @param line     The line of text to display
     *  @param font     The font to display
     *  @param width    The desired width of the text box in pixels
     */
    public SuperTextBox (String[] text, Font font, int width){
        this (text, Color.BLACK, Color.WHITE, font, false, width, 2, new Color (255, 255, 204), -1);
    }

    /**
     *  Simple Constructor - Multi-line text box
     *  
     *  @param line     The line of text to display
     *  @param font     The font to display
     *  @param width    The desired width of the text box in pixels
     *  @param centered Set to true to center the text (performance cost)
     */
    public SuperTextBox (String[] text, Font font, int width, boolean centered){
        this (text, Color.BLACK, Color.WHITE, font, centered, width, 2, new Color (255, 255, 204), -1);
    }

    /**
     * A simple constructor for a BLANK text box with a specified number of lines.
     * 
     * @param lines         The number of lines for text in this BLANK text box
     * @param font          The (Greenfoot) Font to display
     * @param width         The width, in pixels, for this text box
     * @param centered      Should the text be centered? (Note - this has a performance cost, especially for the Greenfoot
     *                      Gallery and doubly so for accessing the Gallery via mobile device)
     * @param borderThickness   The thickness of the border. Setting this to zero will make a borderless text box
     * @param borderColor   The color of the border
     * @param forcedHeight  If this value is greater than the calculated height, it will make this text box taller, or <b>-1<b> for none.
     */
    public SuperTextBox(int lines, Font font, int width, boolean centered, int borderThickness, Color borderColor, int forcedHeight){
        this(new String[lines],Color.BLACK, Color.WHITE, font, centered, width, 2, new Color (255, 255, 204), forcedHeight);
    }

    /**
     * The detailed constructor for ONE-LINE text boxes.
     * 
     * @param line          A single String, which will be the starting text for this one-line text box
     * @param backColor     The background colour of the text box
     * @param foreColor     The text colour of the text box
     * @param font          The (Greenfoot) Font to display
     * @param centered      Should the text be centered? (Note - this has a performance cost, especially for the Greenfoot
     *                      Gallery and doubly so for accessing the Gallery via mobile device)
     * @param width         The width, in pixels, for this text box
     * @param borderThickness   The thickness of the border. Setting this to zero will make a borderless text box
     * @param borderColor   The color of the border
     */
    public SuperTextBox(String line, Color backColor, Color foreColor, Font font, boolean centered, int width, int borderThickness, Color borderColor){
        this(new String[]{line}, backColor, foreColor, font, centered, width, borderThickness, borderColor, -1);
    }

    /**
     * The detailed constructor for a BLANK text box with a specified number of lines.
     * 
     * @param line          A single String, which will be the starting text for this one-line text box
     * @param backColor     The background colour of the text box
     * @param foreColor     The text colour of the text box
     * @param font          The (Greenfoot) Font to display
     * @param centered      Should the text be centered? (Note - this has a performance cost, especially for the Greenfoot
     *                      Gallery and doubly so for accessing the Gallery via mobile device)
     * @param width         The width, in pixels, for this text box
     * @param borderThickness   The thickness of the border. Setting this to zero will make a borderless text box
     * @param borderColor   The color of the border
     * @param forcedHeight  If this value is greater than the calculated height, it will make this text box taller, or <b>-1<b> for none.
     */
    public SuperTextBox(int lines, Color backColor, Color foreColor, Font font, boolean centered, int width, int borderThickness, Color borderColor, int forcedHeight){
        this(new String[lines], backColor, foreColor, font, centered, width, borderThickness, borderColor, forcedHeight);
    }

    /**
     * Primary Constructor.
     * 
     * @param text          The array of text. Number of lines will be equal to the size of this array.
     * @param backColor     The background colour of the text box
     * @param foreColor     The text colour of the text box
     * @param font          The (Greenfoot) Font to display
     * @param centered      Should the text be centered? (Note - this has a performance cost, especially for the Greenfoot
     *                      Gallery and doubly so for accessing the Gallery via mobile device)
     * @param width         The width, in pixels, for this text box
     * @param borderThickness   The thickness of the border. Setting this to zero will make a borderless text box
     * @param borderColor   The color of the border
     * @param forcedHeight  If this value is greater than the calculated height, it will make this text box taller, or <b>-1<b> for none.
     */
    public SuperTextBox(String[] text, Color backColor, Color foreColor, Font font, boolean centered, int width, int borderThickness, Color borderColor, int forcedHeight){

        init (text);

        this.numLines = text.length;

        centeredXs = new int[numLines];

        this.backColor = backColor;
        this.foreColor = foreColor;
        this.borderColor = borderColor;
        this.font = font;
        this.centered = centered;

        centeredVertically = false;

        this.borderThickness = borderThickness;
        this.forcedHeight = forcedHeight;

        if (borderThickness > 0)
            bordered = true;
        else
            bordered = false;

        this.width = width;
        fontSize = font.getSize();

        // FIX

        fontHeight = getFontHeight(font);

        // Spacing is a factor of font size - Font sizes typically describe the max height of 
        // the characters in that font. I.e. 24pt font is 24 pixels tall
        vSpace = (int)(fontSize / 3.0); //6, or 1/4 of the font's height between lines
        // padding is above and below entire text box
        padding = (int)(fontSize / 2.0); // 8

        // Padding top and bottom, vSpace between rows and bottom to make up for extra space on top
        calculatedHeight = (borderThickness + padding) * 2 + (numLines * fontHeight) + ((numLines-1) * vSpace);

        // Actual height will be the higher of the requested heioht and the minimum size required to show
        // the number of lines in the font specified.
        height = Math.max (calculatedHeight, forcedHeight);

        image = new GreenfootImage (width, height);//generateImage(width, height);

        update (text);
        setImage(image);

    }

    private void init (String[] text) {
        if (text[0] == null){
            for (int i = 0; i < text.length; i++){
                text[i] = "";
            }
        }
    }

    /**
     * Update by providing a new line of text.
     * 
     * For text boxes with more than one line, this will insert the text at the BOTTOM, bumping
     * existing text upward.
     * 
     * For text boxes with just a single line, this will replace the existing text and, if
     * centering is turned on, recenter. For more precise control, use update (String[]) or
     * updateLine(String, boolean).
     * 
     * @param text the line of text to add. 
     */
    public void update (String textLine){
        if (text.length == 1){
            update (new String[] {textLine}); 
        } else { 
            updateLine (textLine, true);
        }
    }

    /**
     * Update with a whole new array of text. 
     * 
     * 1.25 - TODO Can now be a different length than the original, which will trigger a resize?
     * 
     * Note that if you have centering turned on this has significant CPU cost because it will
     * call the getStringWidth method as many times as there are Strings in String[] text.
     * For example, if there are four lines of text, it would call the method four times,
     * which in the best case is 12-16ms (on a fast local computer) which will almost certainly
     * slow your acts below 60 fps. On the Greenfoot Gallery, the time for this method to run
     * is about 10x slower, or about 120-160ms, which is enough for perceptible lag. 
     * 
     * It is better to call updateLine for centered text, as 
     * it maintains the previously calculated values for the existing lines and only calls
     * the getStringWidth method once for the new text inserted.
     * 
     * If you're not centering your text, you can ignore the above.
     */
    public void update (String[] text)
    {
        if (text == null || (this.text != null && text.length != this.text.length)){

        }
        this.text = text;

        recenter();
        update();
    }

    private void recenter() {
        if (centered){
            for (int i = 0; i < text.length; i++){
                // The drawing position for the left of the String, not the actual center
                centeredXs[i] = (width  - getStringWidth(font, text[i]))/2;
            }
        }
    }
    
    /**
     * Update the Multi Line Text box by adding a new line. This can add a line
     * at the top or bottom, and will move the other lines accordingly.
     * 
     * @param textIn    The new String to add to the display
     * @param fromBottom    Should the line go at the bottom (scroll up), or false to insert it at the top (scroll down)
     */
    public void updateLine (String textIn, boolean fromBottom){
        // Bump values in the arrays in the appropriate direction:
        for (int i = 0; i < text.length - 1; i++){
            if (fromBottom){ // inserting to bottom, move text up (text[0] is displayed at the top)
                // move the text
                text[i] = text[i + 1];
                // moved the cached size value to avoid recalculation
                centeredXs[i] = centeredXs[i+1];
            } else { // inserting at the top, move everything else down
                text[text.length-(i+1)] = text[text.length-i-2];
                centeredXs[text.length-(i+1)] = centeredXs[text.length-i-2];
            }
        }
        // Insert the new value, and if centered, calculate it's position:
        if (fromBottom){
            text[text.length - 1] = textIn;
            if (centered){
                centeredXs[text.length - 1] = (width  - getStringWidth(font, text[text.length - 1]))/2;
            }
        } else {
            text[0] = textIn;
            if (centered){
                centeredXs[0] = (width  - getStringWidth(font, text[0]))/2;
            }
        }
        update();

    }

    public void update (){

        int xPos, yPos;

        image.setColor(backColor);
        image.fill();

        // ===== DRAW BORDER =======

        if (bordered){
            image.setColor (borderColor);
            for (int i = 0; i < borderThickness; i++){
                image.drawRect (0 + i, 0 + i, width - 1 - (i * 2), height - 1 - (i*2));
            }
        }

        // ====== DRAW TEXT ========
        image.setColor(foreColor);
        // Check for empty Strings and return from this method if any Strings are empty
        // which will cause this not to draw text - and fill them with an empty String instead
        for (String s : text){
            if (s == null){
                s = "";
            }
        }

        image.setFont(font);

        for (int i = 0; i < numLines; i++){
            // Calculate yPos - for centering text vertically.
            yPos = linePositionY (font, numLines, i, borderThickness, fontHeight, padding, vSpace, height);
            if (centered){
                xPos = centeredXs[i];
            } else{
                xPos = padding + borderThickness;
            }
            image.drawString(text[i], xPos , yPos);
        }

    }


    // Used to calculate Y position for text within a text box for a particular line
    private static int linePositionY (Font font, int numLines, int lineNumber, int borderThickness, int fontHeight, int padding, int vSpace, int boxHeight){

        // height of all lines
        int textHeight = (fontHeight * numLines) + vSpace * (numLines - 1);    
        int topY = (boxHeight - textHeight) / 2;// + borderThickness;
        // Y position for the first line of text
        int topYLinePosition = topY + fontHeight - 1;
        int heightPerLine = fontHeight + vSpace;

        // Since fonts are drawn based on their base, not actually bottom
        // (meaning letters like jyg that dangle below the line will be below
        // the y position specified. So, this method compares the height of
        // non-danglers against the full height of the font to see what the 
        // necessary offset. You can set this to zero if you'd rather it use
        // the traditional method of placement.
        int offset = getDanglerOffsetHeight(font);

        return topYLinePosition + (lineNumber * heightPerLine) + offset;

    }

    /**
     * Set or change Colors.
     * 
     * @param backColor     The Background colour
     * @param foreColor     The text colour
     * @param borderColor   the border colour
     */
    public void setColors (Color backColor, Color foreColor, Color borderColor){
        this.backColor = backColor;
        this.foreColor = foreColor;
        this.borderColor = borderColor;
        update();
    }

    public void forceHeight (int height){
        this.height = height;
        update();
    }

    /**
     * Convenient method to set vertical and horizontal centering in one command.
     * Note that this ONLY works if the forcedHeight is greater
     * than the calculated height.
     * 
     * @param vertical      Should text be centered vertically, if possible
     * @param horizontal    Should text be centered horizontally? Rememeber there is a 
     *                      small performance hit for centering.
     */
    public void setCentered (boolean vertical, boolean horizontal){
        centerVertically (vertical);
        centered = horizontal;
    }

    /**
     *  Centers the text within the box vertically. This ONLY works if the forcedHeight is greater
     *  than the calculated height.
     */
    public void centerVertically (boolean c){
        if (c && forcedHeight >= calculatedHeight){
            centeredVertically = true;
        } else {
            centeredVertically = false;
        }
        update();
    }

    public void toggleCentered (){
        centered = !centered;
        recenter();
        update();
    }

    /**
     * <p>Danglers are letters that go below the line.  Greenfoot draws based on the baseline,
     * not the actual bottom of the text. So, this method runs the font size check on a set
     * of letters and numbers that do not dangle, and then compares that to the overall font height.
     * Creating the offset was not simple - the mathematical center does not look right. This 
     * calculation attempts to place the text in a comfortable center-appearance that considers
     * about half of the "dangle" when determining the height. Since this is a fairly heavy caculation
     * that calls getFontHeight twice, it is recommended that the result of this method
     * be cached (run once, saved in a variable) rather than repeatedly run each act.</p>
     * 
     * <p>Feel free to modify the offset formula -- if you find a better formula I'd love to see it!</p>
     * 
     * @param font  The font to measure.
     * @return int the difference between the full height of the font and the non-danglers - effectively 
     * measuring how far the danglers dangle below the line, then adjusted for visual appropriateness.
     */
    public static int getDanglerOffsetHeight (Font font){
        return -((getFontHeight(font) - getFontAboveLineHeight(font)-1))/2;
    }

    public static int getFontAboveLineHeight(Font font){
        return getFontHeight (font, "abc123ABC");
    }

    public static int getFontHeight (Font font){
        return getFontHeight (font, "gjpqyT1^\\/!$0OMGILACY{[|`(");
    }

    /**
     * 
     * <p>A method that gets the height of a particlar object of Font (which includes a size,
     * as well as whether bold or italics - See Class greenfoot.Font API)</p>
     *  
     * <p><b>Purpose</b>If you want to center text vertically, and do it in a dynamic way, you need to calculate the
     * height of your text. Typically, a font of a particular size is about that height in pixels. So, 24 
     * point font is about 24 pixels tall. However, this can vary quite a bit by font, and some fonts have
     * characters that reach higher or lower.
     * 
     * <p>This method draws the 
     * 
     * All the danglers and a sample of tall guys - "gjpqyT1^\\/!$0OMGILACY{[|`("
     *     (note the \\ is an escape character and a backlash)
     * 
     * @param font  The Font object whose maximum height you would like to measure. 
     */
    public static int getFontHeight(Font font, String testString){
        // a string with lots of tall and shorrt letters
        String test = testString;
        GreenfootImage testImage = new GreenfootImage((int)(font.getSize() * test.length() * 1.2), font.getSize() * 2);
        // draw the string at the bottom;

        testImage.setColor(new Color(255, 255, 255));
        testImage.setFont(font);
        testImage.drawString(test, 0, testImage.getHeight()/2);

        int firstClearAbove, firstClearBelow;
        int middle = testImage.getHeight();

        boolean topFound = false, bottomFound = false;
        int topScan = 0, bottomScan = testImage.getHeight()-1;
        // top scan
        while (!(topFound && bottomFound)){

            if (!topFound){

                for (int i = 0; i < testImage.getWidth() && !topFound; i++){
                    if (testImage.getColorAt(i, topScan).getAlpha() != 0){
                        topFound = true;
                    } else {
                        //testImage.setColorAt(i,topScan, Color.RED);
                    }
                 }
                if (!topFound){
                    topScan++;
                    //testImage.setColor(Color.RED);
                    //testImage.drawLine(0, topScan, testImage.getWidth()-1, topScan);

                }
            }
            if (!bottomFound){

                for (int i = 0; i < testImage.getWidth() && !bottomFound; i++){
                    if (testImage.getColorAt(i, bottomScan).getAlpha() != 0){
                        bottomFound = true;
                    } else {
                        //testImage.setColorAt(i,bottomScan, Color.GREEN);
                    }
                }
                if (!bottomFound){
                    bottomScan--;
                    //testImage.setColor(Color.GREEN);
                    //testImage.drawLine(0, bottomScan, testImage.getWidth()-1, bottomScan);

                }
            }

        }

        //System.out.println("font: " + font + "top " + topScan + " bottom: " + bottomScan + " size: " + (bottomScan - topScan));

        //return testImage;
        return bottomScan - topScan;
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
        // (now also works for empty Strings - thanks to Math.max to ensure non-zero 1.21)
        // 24 / 1.2 = 20
        int maxWidth = Math.max((int)(text.length() * (font.getSize()/1.20)), 1);//1000; 
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

