import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * WORK IN PROGRESS - INCOMPLETE!
 * 
 * @author Jordan Cohen
 * @version Feb 2022
 */
public class SuperSpeechBubble extends Actor
{
    // Static Variables
    private static final String STANDARD_FONT_NAME = "Comic Sans MS";
    private static final int STANDARD_FONT_SIZE = 18;
    private final Font STANDARD_FONT = new Font (STANDARD_FONT_NAME, true, false, STANDARD_FONT_SIZE);
    
    // Class Variables
    private GreenfootImage image;
    private String text;
    private boolean isThought;
    private Actor owner;
    private int offset;
    private boolean autoFit;
    private Font font;
    
    public SuperSpeechBubble (Actor owner, int offset, int width, int height, int stemHeight, int stemStart, String text, boolean facingRight, boolean isThought){
        
        font = STANDARD_FONT;
        image = drawBubble (width, height, stemHeight, stemStart, text, facingRight, isThought, true, font);
        setImage(image);
        this.owner = owner;
        this.offset = offset;
        
    }
    
    public void addedToWorld (World w){
        setLocation (owner.getX(), owner.getY() - offset);
    }
    
    public void act()
    {
        // Add your action code here.
    }
    
    private static GreenfootImage drawBubble (int width, int height, int stemHeight, int stemStart, String text, boolean facingRight, boolean isThought, boolean autoFit, Font font){
        GreenfootImage bubble = new GreenfootImage(width, height + stemHeight);
        
        int stemWidth = width / 4;
        // draw the Stem
        int[] xCoords = new int[3];
        int[] yCoords = new int[3];
        xCoords[0] = stemStart;
        yCoords[0] = height/2;
        yCoords[2] = height/2;
        if (facingRight){
            xCoords[1] = stemStart + (stemWidth/2);
            yCoords[1] = stemHeight + height;
            
            xCoords[2] = stemStart + stemWidth;        
        }
        
        // draw the Stem + it's outline
        
        
        bubble.setColor (Color.BLACK);
        bubble.drawPolygon (xCoords, yCoords, 3);
        
        // draw the oval + it's outline
        bubble.setColor (Color.BLACK);
        bubble.drawOval (0, 0, width, height);
        
        bubble.setColor (Color.WHITE);
        bubble.fillOval (0, 0, width, height);
        
        bubble.setColor (Color.WHITE);
        bubble.fillPolygon (xCoords, yCoords, 3);
        
        // Draw Text
        bubble.setFont (font);
        int lines = 2;
        //int xStart = ;
        if (autoFit){
            
        }
        
        
        return bubble;
    }
}
