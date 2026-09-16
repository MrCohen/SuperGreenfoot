import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * Write a description of class Line here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class Line extends Actor
{
    private GreenfootImage image;
    private static final Color DEFAULT_COLOR = Color.WHITE;
    
    public Line (boolean vertical, int length, int thickness){
        
        this (vertical, length, thickness, DEFAULT_COLOR);
        
        
    }
    
    public Line (boolean vertical, int length, int thickness, Color color){
        
        int wid, hgt;
        wid = vertical ? thickness : length;
        hgt = vertical ? length : thickness;
        
        image = new GreenfootImage (wid, hgt);
        image.setColor(color);
        image.fill();
        setImage(image);
    }
    
    /**
     * Act - do whatever the Line wants to do. This method is called whenever
     * the 'Act' or 'Run' button gets pressed in the environment.
     */
    public void act()
    {
        // Add your action code here.
    }
}
