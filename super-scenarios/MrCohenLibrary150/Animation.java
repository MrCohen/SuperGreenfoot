import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * The Animation Class is controlled by AnimationController.
 * 
 * In some of my examples, you may find both baked together into a single class.
 * 
 * The Animation Class is mostly a container for images, can be based on 1D or 2D
 * array of images (for one-direction or multi-direction Animations) and also includes
 * an enumerator for Direction to keep direction variables clean and manageable.
 * 
 * @author Jordan Cohen
 * @since 2017
 * @version (unsure)
 */
public class Animation {
    private GreenfootImage[][] directionalImages;
    private GreenfootImage[] nonDirectionalImages;

    private boolean directional;

    private int directions;
    /**
     * Constructor for directional animations - should be a 2d array of GreenfootImage with
     * 4 directions (dimension 1) and at least one image per direction (dimension 2)
     *
     * @param images    2d array of images as described above
     * @param terminal  true if this animation is not intended to repeat
     */

    public Animation (GreenfootImage[][] images){
        this.directional = true;
        directionalImages = images;

        directions = images.length;
    }

    public Animation (GreenfootImage[] images){
        this.directional = false;
        nonDirectionalImages = images;

        directions = 1;
    }

    public void setImages (GreenfootImage[][] images){
        directionalImages = images;
    }

    public void setImages (GreenfootImage[] images){
        nonDirectionalImages = images;
    }

    public boolean isDirectional (){
        return this.directional;
    }

    public GreenfootImage getOneImage (Direction d, int frame){
        return directionalImages[d.getDirection()][frame];
    }

    public GreenfootImage[][] getDirectionalImages (){
        return directionalImages;
    }

    public GreenfootImage[] getNonDirectionalImages (){
        return nonDirectionalImages;
    }

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
    private static GreenfootImage getSlice (GreenfootImage spriteSheet, int xPos, int yPos, int frameWidth, int frameHeight)
    {
        if (frameWidth > spriteSheet.getWidth() || frameHeight > spriteSheet.getHeight()){

            System.out.println("Error in AnimationManager: GetSlice: You specified a SpriteSheet that was smaller than your desired output");
            return null;
        }
        GreenfootImage small = new GreenfootImage (64, 64);
        // negatively offset the larger sprite sheet image so that a correct, small portion
        // of it is drawn onto the smaller, resulting image.
        small.drawImage (spriteSheet, -xPos, -yPos);
        return small;
    }

}

// ENUM to keep direction related code clean.
enum Direction {
    RIGHT(0),

    LEFT(1),

    UP(2),

    DOWN(3);

    private final int dirCode;
    private Direction (int dirCode){
        this.dirCode = dirCode;
    }

    public int getDirection (){
        return this.dirCode;
    }

    public static Direction fromInteger(int x) {
        switch(x) {
            case 0:
                return RIGHT;
            case 1:
                return LEFT;
            case 2:
                return UP;
            case 3:
                return DOWN;
        }
        return null;
    }

    public static Direction randomDirection (){
        return fromInteger (Greenfoot.getRandomNumber(4));

    }

    public final static int size = Direction.values().length;
}


