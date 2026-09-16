//import java.awt.image.BufferedImage;
//import java.awt.Graphics2D;
import greenfoot.*;
import javax.swing.JOptionPane;
import java.util.HashMap;
/**
 * AnimationManager contains a number of useful, static methods to help import and manupulate Animations.
 * This includes the ability to import straight from a SpriteSheet, as well as adding layers on top of
 * each other (I.e. draw armor onto body) and cropping images (for tighter collision detection).
 *
 * Note that MatchLargest DOESN'T WORK smoothly for animations with LPC sprites because for some reason
 * a frame ends up off center despite my initial attempts to fix (TODO- FIX!)
 *
 * @author Jordan Cohen
 * @version 0.0.3
 * @since 0.0.1
 */
public class AnimationManager
{
    private static final Color CLEAR_PIXEL = new Color (0,0,0,0);

    /**  This will allow for importing armor etc to make the character dynamic! Without spritefoot work!
     *  Rows must be 4 (directional) or 1 (non-directional). This is designed to work with spritesheets from
     *  LPC but could be tailored to work with other source material.
     *
     *  @param spriteSheet  the Spritesheet to pull frames from
     *  @param startRow     the row on which the desired sprites are located (not x,y coordinate)
     *  @param numFrames    the number of frames in the animation
     *  @param terminal     is this a terminal animation? (One that ends after it plays a set number of times).
     *  @return Animation   an appropriate Animation object that is either 1 direction or 4 direction.
     *
     */
    protected static Animation createAnimation(GreenfootImage spriteSheet, int startRow, int numRows, int numFrames, int width, int height){
        if (numRows > 1){ // 4-way animation
            GreenfootImage[][] images = new GreenfootImage [numRows][numFrames];
            for (int row = 0; row < numRows; row++){
                int dir = -1;
                switch (row) { // translate between Direction values and the order in which the frames are organized in LPC sheets
                    case 0: dir = 2;  break;
                    case 1: dir = 1;  break;
                    case 2: dir = 3;  break;
                    case 3: dir = 0;  break;
                }
                if (dir == -1) return null;
                for (int frame = 0; frame < numFrames; frame++){
                    //System.out.println(spriteSheet + " Row: " + row + " dir: " + dir + " frame: " + frame);
                    images[dir][frame] = new GreenfootImage (getSlice(spriteSheet, frame * width, (startRow + row - 1) * height, width, height));
                }
            }
            Animation anim = new Animation (images);
            return anim;
        }
        else {
            // If this only has one dimension, create a 1 dimension Animation
            GreenfootImage[] img1d = new GreenfootImage[numFrames];
            for (int frame = 0; frame < numFrames; frame++){
                //System.out.println(spriteSheet + " Row: " + row + " dir: " + dir + " frame: " + frame);
                img1d[frame] = new GreenfootImage (getSlice(spriteSheet, frame * width, startRow * height, width, height));
            }
            Animation anim = new Animation(img1d);
            return anim;

        }
    }

    /**
     * Grabs a part of a sprite sheet (or any other GreenfootImage) and returns it as a new
     * GreenfootImage. The sprite sheet must be larger than the resulting image.
     *
     * @param spriteSheet   the larger spritesheet to pull images from
     * @param xPos  the x position (of the left) of the desired spot to draw from
     * @param yPos  the y position (of the top) of the desired spot to draw from
     * @param frameWidth     the horizontal tile size
     * @param frameHeight    the vertical tile size
     * @return GreenfootImage   the resulting image
     */
    private static GreenfootImage getSlice(GreenfootImage spriteSheet, int xPos, int yPos, int frameWidth, int frameHeight) {
        if (xPos < 0 || yPos < 0 || frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("Invalid crop dimensions");
        }
        if (xPos + frameWidth > spriteSheet.getWidth() || yPos + frameHeight > spriteSheet.getHeight()) {
            throw new IllegalArgumentException("Crop area exceeds sprite sheet bounds");
        }

        GreenfootImage slice = new GreenfootImage(frameWidth, frameHeight);
        slice.drawImage(spriteSheet, -xPos, -yPos);
        return slice;
    }

    // Add a layer of armor etc.
    public static void addLayer (GreenfootImage[][] base, GreenfootImage[][] overlays){

        for (int row = 0; row < overlays.length; row++){
            for (int frame = 0; frame < overlays[0].length; frame++){

                base[row][frame].drawImage(overlays[row][frame], 0, 0);

            }
        }
    }

    /**
     * The Tight Selection core. This is the algorithm that finds the tightest rectangle around a shape.
     * Result does not include a border - only the tighest possible selection.
     *
     * Note: This feature will not work on sprites that overlap each other. Sprites must be within their
     * own bounding rectangle. TODO: Improve This.
     *
     * @param source    The GreenfootImage that is the source for the resulting selection
     * @param targetX   The X coordinate that was selected, should be on the image, not on transparency
     * @param targetY   The Y coordinate that was selected, should be on the image, not on transparency
     *
     * @return GreenfootImage   Returns a GreenfootImage object that is cropped without excess transparency
     */
    public static GreenfootImage getCroppedImage (GreenfootImage source, int targetX, int targetY){
        int left = targetX - 1, right = targetX + 1, top = targetY - 1, bottom = targetY + 1;
        boolean leftDone = false, rightDone = false, topDone = false, bottomDone = false;
        boolean done = false;
        // initial adjustments to make a small square

        //BufferedImage awtSource = source.getAwtImage();
        Color temp = source.getColorAt(targetX, targetY);
        if (temp.getAlpha() == 0){ // If used did not click on a shape
            JOptionPane.showMessageDialog( null,
                "Clicked on Transparency - Please click on an image instead.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return null;
        } else { // if user has clicked on a shape

            while (!done){
                boolean stillGrowing = false;

                //Check on all sides for transparency, and if I've found it on all sides, then I'm done, otherwise continue to grow
                if (!checkTransparentVertical(new Coordinate(left, top), new Coordinate (left, bottom), source)){
                    left--;
                    stillGrowing = true;
                }

                if (!checkTransparentHorizontal(new Coordinate (left, top), new Coordinate (right, top), source)){
                    top--;
                    stillGrowing = true;
                }

                if(!checkTransparentVertical(new Coordinate(right, top), new Coordinate (right, bottom), source)){
                    right++;
                    stillGrowing = true;
                }

                if (!checkTransparentHorizontal(new Coordinate(left, bottom), new Coordinate (right, bottom), source)){
                    bottom ++;
                    stillGrowing = true;
                }

                if (!stillGrowing){
                    done = true;

                }

            }
        }
        // Offset, which mysteriously makes the above algorithm accurate ...
        left++;
        right++;
        top++;

        // Build a new GreenfootImage by cropping using the dimensions that were just calculated
        int offsetX = 0, offsetY = 0;
        if (right-left % 2 == 0){ offsetX = 1; }
        if (bottom-top % 2 == 0){ offsetY = 1; }
        return new GreenfootImage (getSlice(source, left, top, right-left + offsetX, bottom-top + offsetY));
    }

    /**
     * Check in a straight, vertical line to see if every pixel in that line is transparent. This will
     * help determine if the edge of a contiguous image has been reached. Edge safe.
     *
     * @param   top Coordinate (x,y pair) of the top of the line
     * @param   bottom  Coordinate (x,y pair) of the bottom of th eline
     * @param   bi  The image to scan, as a bufferedImage (not GreenfootImage!)
     */
    private static boolean checkTransparentVertical (Coordinate top, Coordinate bottom, GreenfootImage gi){
        boolean transparent = true;
        try{
            for (int i = top.getY(); i < bottom.getY(); i++){
                int trans = gi.getColorAt(top.getX(), i).getAlpha();
                if (trans != 0){
                    transparent = false;
                }

            }
        }
        catch (ArrayIndexOutOfBoundsException e){ // treat edges as transparent, because we can't go any further
            transparent = true;
        }
        return transparent;
    }

    /**
     * Check in a straight, vertical line to see if every pixel in that line is transparent. This will
     * help determine if the edge of a contiguous image has been reached. Edge safe.
     *
     * @param   top Coordinate (x,y pair) of the top of the line
     * @param   bottom  Coordinate (x,y pair) of the bottom of th eline
     * @param   bi  The image to scan, as a bufferedImage (not GreenfootImage!)
     */
    private static boolean checkTransparentHorizontal (Coordinate left, Coordinate right, GreenfootImage gi){
        boolean transparent = true;
        try{
            for (int i = left.getX(); i < right.getX(); i++){
                int trans = gi.getColorAt(i, left.getX()).getAlpha();
                if (trans != 0){
                    transparent = false;
                }

            }
        }
        catch (ArrayIndexOutOfBoundsException e){ // treat edges as transparent, because we can't go any further
            transparent = true;
        }
        return transparent;
    }

    /**
     * Trim a specified number of pixels off every frame in an animation equally. 
     */
    public static Animation trim_old (Animation anim, int leftTrim, int rightTrim, int topTrim, int bottomTrim){

        if (anim.isDirectional()){
            GreenfootImage[][] images = anim.getDirectionalImages();
            for (int direction = 0; direction < images.length; direction++){
                for (int frame = 0; frame < images[direction].length; frame++){
                    images[direction][frame] = getSlice(images[direction][frame], leftTrim, topTrim, images[direction][frame].getWidth() - rightTrim - leftTrim, images[direction][frame].getHeight() - bottomTrim - topTrim);
                }
            }

            return new Animation (images);
        } else {
            GreenfootImage[] images = anim.getNonDirectionalImages();
            for (int frame = 0; frame < images.length; frame++){
                images[frame] = getSlice(images[frame], leftTrim, topTrim, rightTrim - leftTrim, bottomTrim - topTrim);
            }
            return new Animation (images);
        }
    }

    /**
     * Trims a fixed number of pixels from each edge of every frame.
     *
     * @param anim        animation to trim
     * @param leftTrim    pixels to remove from the left
     * @param rightTrim   pixels to remove from the right
     * @param topTrim     pixels to remove from the top
     * @param bottomTrim  pixels to remove from the bottom
     * @return            a *new* Animation with trimmed frames
     */
    public static Animation trim(Animation anim, int leftTrim, int rightTrim, int topTrim,  int bottomTrim) {

        if (leftTrim < 0 || rightTrim < 0 || topTrim < 0 || bottomTrim < 0) {
            throw new IllegalArgumentException("Trim values must be non-negative");
        }

        if (anim.isDirectional()) {
            GreenfootImage[][] src = anim.getDirectionalImages();
            GreenfootImage[][] dst = new GreenfootImage[src.length][];

            for (int dir = 0; dir < src.length; dir++) {
                dst[dir] = new GreenfootImage[src[dir].length];

                for (int f = 0; f < src[dir].length; f++) {
                    GreenfootImage frame = src[dir][f];

                    int newW = frame.getWidth()  - leftTrim - rightTrim;
                    int newH = frame.getHeight() - topTrim  - bottomTrim;
                    if (newW <= 0 || newH <= 0) {
                        throw new IllegalArgumentException("Trim too large for frame");
                    }

                    dst[dir][f] = getSlice(frame, leftTrim, topTrim, newW, newH);
                }
            }
            return new Animation(dst);          // brand-new, immutable Animation
        }
        else {  // non-directional
            GreenfootImage[] src = anim.getNonDirectionalImages();
            GreenfootImage[] dst = new GreenfootImage[src.length];

            for (int f = 0; f < src.length; f++) {
                GreenfootImage frame = src[f];

                int newW = frame.getWidth()  - leftTrim - rightTrim;
                int newH = frame.getHeight() - topTrim  - bottomTrim;
                if (newW <= 0 || newH <= 0) {
                    throw new IllegalArgumentException("Trim too large for frame");
                }

                dst[f] = getSlice(frame, leftTrim, topTrim, newW, newH);
            }
            return new Animation(dst);
        }
    }

    public static Animation matchLargest (Animation anim, int leftPad, int rightPad, int topPad, int bottomPad){
        if (anim.isDirectional()){
            //public static TightSelection getTightSelection (GreenfootImage source, int targetX, int targetY){
            GreenfootImage[][] images = anim.getDirectionalImages();

            // This cropping caused issues because I think some images ended up odd sized and caused flicker.
            // Possibly fix this, or possibly just crop image to find hit box later.
            for (int direction = 0; direction < images.length; direction++){
                //TightSelection[] t = new TightSelection [images[direction].length];
                int maxWidth = 0, maxHeight = 0;
                for (GreenfootImage g : images[direction]){
                    GreenfootImage temp = getCroppedImage(g, g.getWidth()/2, g.getHeight()/2);
                    if (temp.getWidth() > maxWidth){
                        maxWidth = temp.getWidth();
                    }
                }

                for (int frame = 0; frame < images[direction].length; frame++){

                    images[direction][frame] = getCroppedImage(images[direction][frame],images[direction][frame].getWidth()/2, images[direction][frame].getHeight()/2);

                }
            }

            return new Animation (matchLargest(images, leftPad, rightPad, topPad, bottomPad));
        } else {
            return new Animation (matchLargest(anim.getNonDirectionalImages(), leftPad, rightPad, topPad, bottomPad));
        }
    }

    // Allows resizing a whole Animation set at once (4 directions). This makes all images within the same direction
    // the same, cropped size, but does not make all directions have the same maximum size - after experimentation, it
    // may be necessary to implement across-direction equalization.
    public static GreenfootImage[][] matchLargest (GreenfootImage[][] images, int leftPad, int rightPad, int topPad, int bottomPad){
        GreenfootImage[][] results = new GreenfootImage[images.length][images[0].length];

        for (int i = 0; i < images.length; i++){
            results [i] = matchLargest (images[i], leftPad, rightPad, topPad, bottomPad);
        }

        return results;
    }

    // Resizes all images to have the same size as the source image, with the supplied padding
    // Note that padding parameters are for extra padding on ALL images above and beyond making them
    // the same size as the largest one. Most of the time, these should be all zero.
    public static GreenfootImage[] matchLargest (GreenfootImage[] images, int leftPad, int rightPad, int topPad, int bottomPad){
        int largestX = 0;
        int largestY = 0;
        GreenfootImage[] results = new GreenfootImage[images.length];
        // find max width and height
        for (GreenfootImage i : images){
            if (i.getWidth() > largestX){
                largestX = i.getWidth();
            }
            if (i.getHeight() > largestY){
                largestY = i.getHeight();
            }

        }

        int index = 0;
        for (GreenfootImage i : images){

            int left = 0, right = 0, top = 0, bottom = 0;
            if (i.getWidth() < largestX){
                int d = Math.abs(largestX - i.getWidth ()); // find delta

                if (d % 2 == 0){ // if delta is even, split padding
                    left = leftPad + d / 2;
                } else { // if odd, add extra padding on LEFT, to be consistent
                    left = leftPad + ((d + 1) / 2);
                }
                //left = leftPad + d / 2;
                right = rightPad + d / 2;
            }
            if (i.getHeight() < largestY){
                int d = Math.abs(largestY - i.getHeight());

                if (d % 2 == 0){  // even delta
                    top = topPad + d / 2;
                } else {            // if odd, add extra padding on TOP to be consistent
                    top = topPad + ((d+1)/2);
                }
                //top = topPad + d / 2;
                bottom = bottomPad + d / 2;
            }

            //System.out.println("left: " + left + " right: " + right);
            results[index] = addPadding (i, left, right, top, bottom, largestX, largestY);
            index++;
        }
        return results;
    }

    public static GreenfootImage addPadding (GreenfootImage gi, int leftPad, int rightPad, int topPad, int bottomPad, int maxWidth, int maxHeight){
        int xSize = gi.getWidth() + leftPad + rightPad;
        int ySize = gi.getHeight() + topPad + bottomPad;

        // Temp image, to store pixels as we reverse everything
        //System.out.println("Making a new image size: " + xSize + ", " + ySize);
        //BufferedImage newBi = new BufferedImage (xSize, ySize, 3);
        GreenfootImage newGi = new GreenfootImage (xSize, ySize);

        //int empty = packagePixel (0, 0, 0, 0);

        for (int y = 0; y < ySize; y++)
        {
            for (int x = 0; x < xSize; x++)
            {
                if (y < topPad || y >= topPad + gi.getHeight() || x < leftPad || x >= rightPad + gi.getWidth()){
                    newGi.setColorAt(x, y, CLEAR_PIXEL);
                } else {
                    //System.out.println("x: " + x + ", y: " + y + " leftPad: " + leftPad + " topPad: " + topPad);
                    newGi.setColorAt(x, y, gi.getColorAt(x - leftPad, y - topPad));
                }
            }
        }
        return newGi;
    }

    /**
     * Takes in an rgb value - the kind that is returned from BufferedImage's
     * getRGB() method - and returns 4 integers for easy manipulation.
     *
     * By Jordan Cohen
     * Version 0.2
     *
     * @param rgbaValue The value of a single pixel as an integer, representing<br>
     *                  8 bits for red, green and blue and 8 bits for alpha:<br>
     *                  <pre>alpha   red     green   blue</pre>
     *                  <pre>00000000000000000000000000000000</pre>
     * @return int[4]   Array containing 4 shorter ints<br>
     *                  <pre>0       1       2       3</pre>
     *                  <pre>alpha   red     green   blue</pre>
     */
    public static int[] unpackPixel (int rgbaValue)
    {
        int[] unpackedValues = new int[4];
        // alpha
        unpackedValues[0] = (rgbaValue >> 24) & 0xFF;
        // red
        unpackedValues[1] = (rgbaValue >> 16) & 0xFF;
        // green
        unpackedValues[2] = (rgbaValue >>  8) & 0xFF;
        // blue
        unpackedValues[3] = (rgbaValue) & 0xFF;

        return unpackedValues;
    }

    /**
     * Takes in a red, green, blue and alpha integer and uses bit-shifting
     * to package all of the data into a single integer, which can be used to
     * set a pixel in a BufferedImage.
     *
     * @param   int red value (0-255)
     * @param   int green value (0-255)
     * @param   int blue value (0-255)
     * @param   int alpha value (0-255)
     *
     * @return int  Integer representing 32 bit integer pixel ready
     *              for BufferedImage
     */
    public static int packagePixel (int r, int g, int b, int a)
    {
        int newRGB = (a << 24) | (r << 16) | (g << 8) | b;
        return newRGB;
    }

}
