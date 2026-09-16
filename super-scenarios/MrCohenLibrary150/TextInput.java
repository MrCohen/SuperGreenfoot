import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

import java.util.*;
/**
 * Joey Ma's Text Input Box ... Use at your own risk, it was not
 * made by me (Mr. Cohen) but it seems to work!
 * 
 * @author Joey Ma
 * @version March 2017
 */
public class TextInput extends Actor
{
    // Declare variables
    private GreenfootImage myImage;
    private GreenfootImage myAltImage;
    private Color textC,bg;
    private String buttonText;
    private int textSize;
    public boolean active;
    private String a;

    public TextInput (int textSize)
    {
        this(textSize,Color.GREEN,Color.WHITE);
    }
    
    public TextInput(int textSize, Color textC, Color bg){
        active=false;
        // Assign value to my internal String
        buttonText = "Input Text";
        this.textSize = textSize;
        // Draw a button with centered text:
        this.textC=textC;
        this.bg=bg;
        updateMe();
    }

    public String getText(){
        return buttonText;
    }

    public TextInput(){
        this(20);
    }

    public void act ()
    {
        if (Greenfoot.mouseClicked(this))        {
            active=!active;
            if(active){
                buttonText="";
                updateMe();
            }
        }
        if(active){
            String temp = Greenfoot.getKey();
            if(temp!=null){
                if(temp.length()==1){
                    if (Greenfoot.isKeyDown("shift")){
                        temp = temp.toUpperCase();
                    }
                    buttonText+=temp;
                }
                else if(temp.equals("space")){
                    buttonText+=" ";
                }
                else if(temp.equals("backspace")){
                    if(buttonText.length()!=0)
                        buttonText=buttonText.substring(0,buttonText.length()-1);
                }
                else if(temp.equals("enter")){
                    active=!active;
                    System.out.println(buttonText);
                }
                updateMe();
            }
        }
        if(active){
            setImage(myAltImage);
        }
        else
        {
            setImage (myImage);
        }
    }

    /**
     * Update current TextButton text
     */
    public void updateMe (String text)
    {
        buttonText = text;
        GreenfootImage tempTextImage = new GreenfootImage (text, textSize, textC, bg);
        int width = Math.max(tempTextImage.getWidth()+7,100);
        myImage = new GreenfootImage (width, tempTextImage.getHeight() + 8);
        myImage.setColor (bg);
        myImage.fill();
        myImage.drawImage (tempTextImage, 4, 4);
        myImage.setColor (Color.BLACK);
        myImage.drawRect (0,0,width, tempTextImage.getHeight() + 7);
        GreenfootImage finImage = new GreenfootImage(width*2, tempTextImage.getHeight() + 8);
        finImage.drawImage(myImage,width,0);
        myImage=new GreenfootImage(finImage);
        setImage(myImage);

        tempTextImage = new GreenfootImage (text, textSize, bg, textC);
        myAltImage = new GreenfootImage(width, tempTextImage.getHeight() + 8);
        myAltImage.setColor (bg);
        myAltImage.fill();
        myAltImage.drawImage (tempTextImage, 4, 4);

        myAltImage.setColor (Color.BLACK);
        myAltImage.drawRect (0,0,width, tempTextImage.getHeight() + 7);
        finImage = new GreenfootImage(width*2, tempTextImage.getHeight() + 8);
        finImage.drawImage(myAltImage,width,0);
        myAltImage = new GreenfootImage(finImage);
    }

    /**
     * Update current TextButton text
     */
    public void updateMe ()
    {
        updateMe(buttonText);
    }
}