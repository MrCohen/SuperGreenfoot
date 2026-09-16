import greenfoot.*;
/**
 * This was my first attempt at getting an AI (ChatGPT) to write
 * code. It was NOT successful on it's first, second or third try!
 * It was willing to accept feedback and improve it's code through
 * multiple iterations. It got most of the math right but I had
 * to fix the code in multiple places to make it work.
 * 
 * @author Jordan Cohen and ChatGPT(!)
 * @version Jan 2023
 */
public class GPTDraw  
{
    /**
     * Fun times generating code with ChatGPT -- it got
     * the trig right, but many bugs in the code. I fixed
     * some, and now this works as a "Pie" drawer.
     */
    public static final double RESOLUTION = 0.1;
    public static void drawArc(GreenfootImage img, int x, int y, int startAngle, int arcAngle)
    {
        int width = img.getWidth();
        int height = img.getHeight();
        // Convert the start and end angles to radians
        double startRadians = Math.toRadians(startAngle);
        double endRadians = Math.toRadians(startAngle + arcAngle);

        // Calculate the center of the bounding rectangle
        int centerX = x + (width / 2);
        int centerY = y + (height / 2);

        // Calculate the starting and ending points of the arc
        int startX = centerX + (int)(Math.cos(startRadians) * (width / 2));
        int startY = centerY + (int)(Math.sin(startRadians) * (height / 2));
        int endX = centerX + (int)(Math.cos(endRadians) * (width / 2));
        int endY = centerY + (int)(Math.sin(endRadians) * (height / 2));

        // Create a new GreenfootImage
        //GreenfootImage img = new GreenfootImage(width, height);

        // Set the color to green
        //img.setColor(Color.GREEN);

        // Draw the arc on the image
        img.drawLine(startX, startY, endX, endY);
        img.drawLine(centerX, centerY, startX, startY);
        img.drawLine(centerX, centerY, endX, endY);

        // Set the actor's image to the GreenfootImage
       // return img;
    }

    public static GreenfootImage fillArc(int x, int y, int width, int height, int startAngle, int arcAngle)
    {
        // Draw the pie slice
        GreenfootImage img = new GreenfootImage(width, height);
        img.setColor(Color.GREEN);
        //drawArc(img, x, y,startAngle, arcAngle);

        // Calculate the number of steps to use for the loop
        int steps = (int)(Math.abs(arcAngle) / (double)RESOLUTION);

        // Loop through the steps and draw lines to fill in the pie slice
        for (int i = 0; i < steps; i++)
        {
            
            double angle1 = Math.toRadians(startAngle + (i * RESOLUTION));
            double angle2 = Math.toRadians(startAngle + ((i + 1) * RESOLUTION));

            int x1 = x + (width / 2) + (int)(Math.cos(angle1) * (width / 2));
            int y1 = y + (height / 2) + (int)(Math.sin(angle1) * (height / 2));
            int x2 = x + (width / 2) + (int)(Math.cos(angle2) * (width / 2));
            int y2 = y + (height / 2) + (int)(Math.sin(angle2) * (height / 2));

            img.drawLine(x1, y1, x2, y2);
            img.drawLine(x1, y1, img.getWidth()/2, img.getHeight()/2);
        }

        return img;
    }

}
