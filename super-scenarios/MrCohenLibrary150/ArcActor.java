import greenfoot.*;

/**
 * This class extends the Actor class and provides a fillArc() 
 * method for drawing pie-shaped parts of circles on the screen.
 * <p>
 * <p>This is the <b>second</b> paragraph.</p>
 * @author Jordan Cohen
 * @version March 2025
 */
public class ArcActor extends Actor
{
    private GreenfootImage image;
    
    /**
     *  Construct a Act actor using the GPTDraw fill arc command.
     *  
     *  @param size     The size of the desired arc.
     */
    public ArcActor (int size) {
        image = GPTDraw.fillArc(0, 0, 100, 100, 120, 290);
        setImage (image);
    }
    
    
    /**
     * This method will do ... something. Not really.
     * 
     * @param age       The user's age
     * @param unused    Nothing. This is nonsense.
     * @return int      A nonsensical return value.
     */
    public int doSomething (int age, int unused){
        if (age < 43){
            return -1;
        }
        return 43;
    }
    
}
