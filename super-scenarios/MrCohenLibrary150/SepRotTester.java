import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * A Test Class to help improve SuperSmoothMover to implement
 * image rotation.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class SepRotTester extends SuperSmoothMover
{
    private Actor target;
    private boolean started;
    public void setTarget (Actor a){
        target = a;
        started = false;
    }

    public void addedToWorld (World w){

        if (!started){
            enableStaticRotation();
            turnTowards(target);
            started = true;
        }

    }

    public void testClickMe() {
        turnTowards(target);
    }

    public void act()
    {
        move(2);
        rotateImage(8);
        if (isAtEdge()){
            turnTowards(target);
        }
    }
}
