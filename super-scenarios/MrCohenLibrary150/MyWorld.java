import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)

/**
 * The World that demonstrates my Library of Resources.
 * 
 * 1.01
 * -----
 * - Multibox basic functionality working
 * - Chooses padding and spacing based on font size
 * - Allows text to be added from top or bottom, knocking extra line off appropriately
 * 
 * 1.02
 * -----
 * - Multibox now has scrolling
 * - Started improving efficiency - maintain CenteredXs array better to avoid reprocessing same text
 * 
 * 1.03
 * -----
 * - Attempting to reverse getCenteredX to start at the right (end) of the image and look backwards
 * - IT WORKED! Leaving 1.03 with both algorithms just in case, will remove in 1.04 and just use the new one. 
 *   (It was about 100% faster locally, and 200% faster on the Gallery!)
 * 
 * 1.04
 * -----
 * - Vastly improved centering algorithm, now caches the results from previous runs to avoid repetition,
 *   also slightly improved the drawing methods. 
 *   
 * 1.05
 * -----
 * - Tweaked algorithm as it was missing small characters due to too much margin of error, slowed it down
 *   a tiny bit though.
 * - Renamed SuperMultiBox to SuperTextBox, and improved support for single-line text boxes, including
 *   a number of new constructors
 * - Renamed SuperTextBox to SuperDisplayLabel (as it's now intended to be as wide as the World as an easy
 *   way to display some stats). 
 * - Documentation is now more complete
 * - More tweaks to the centering algorithm including ignoring colours and just looking for alpha, and more 
 *   constructors added and tested. 
 * 
 * 1.10
 * -----
 * - Leaving SuperTextBox (formerly SuperMultiBox) alone now - it's as good as it's going to get. 
 * 
 * 1.16
 * -----
 * - Fixed SuperWindow - coordinate system now works better, uses similar method names to World
 * - Improved SuperStatBar - now has better control of hideAtMax values
 * 
 * 1.19
 * -----
 * - Numerous major improvements to SuperWindow
 * - * Now allows a custom paint order to be set (TODO: Add example of implementation)
 * - * Can be set to "managed" which means it's managing not only paint order within
 *     Windows, but also the overall paint order of Windows
 *   * See MazeGame 0.7.2 for current example of implementation (TODO --> Add to this scenario!)
 * - Fixed additional constructors for SuperStatBar --> especially hideAtMax
 * 
 * 1.24
 * ----
 * - SuperTextBox getting some updates!
 * - b: fixedHeight working. Updates much better.
 * - c: Improve update methods
 * 
 * 
 * @author Jordan Cohen 
 * @version 1.24
 */
public class MyWorld extends World
{
    
   
    private SuperTextBox testBox, dialogStyleTextBox, volumeText;
    private SuperWindow testWidget, imageWindow;

    private MouseInfo m;
    private Player player;
    private Patroller patroller;    
    private Timer timer;

    private GreenfootImage gridLines;
    private Font funFont, boringFont;

    private SwordGuyPro swordGuy;
    
    private int counter, maxCount, countdown;

    private float[] results;
    private long start, current, elapsed;
    private int total;
    private long seconds;

    private int actCount, maxVol, sumVol;
    
    
    public static final Class<?>[] platformClasses = {
        SuperWindow.class,
        SuperTextBox.class,
        // Add more platform-like classes here
    };


    /**
     * Constructor for objects of class MyWorld.
     * 
     */
    public MyWorld()
    {    
        // Create a new world with 600x400 cells with a cell size of 1x1 pixels.
        super(800, 600, 1, false); 

        StaticTimer.start();

        setPaintOrder ( Player.class, SuperStatBar.class, Line.class, SuperTextBox.class, SuperWindow.class, Patroller.class);
        setActOrder (World.class);

       // Reset speed slider to default (middle)
        Greenfoot.setSpeed(50);

        swordGuy = new SwordGuyPro();
        addObject(swordGuy, 600, 50);
        
        gridLines = Utility.drawSpace (800, 600, 100);
        setBackground(gridLines);

        funFont = new Font ("Comic Sans MS", false, false, 16);
        boringFont = new Font ("Times New Roman", false, false, 18);

        player = new Player ();
        //addObject(player, 400, 300);

        // Create a looping Path and add points to form it. No resolution and no
        // calculateRotationVectors() call needed any more - just add points.
        SuperPath path = new SuperPath(true); // true = loop forever
        path.addPoint(100, 200);
        path.addPoint(200, 100);
        path.addPoint(300, 200);
        path.addPoint(400, 300);
        path.addPoint(500, 200);
        path.addPoint(400, 100);
        path.addPoint(300, 0);
        path.addPoint(200, 100);

        // Draw the path onto the background so we can see it.
        path.drawOnto(getBackground(), new Color(120, 120, 120), 2);

        // Two Patrollers share the SAME path at different speeds. Each keeps its
        // own progress, so they no longer interfere with one another.
        Patroller fastVehicle = new Patroller(path, 5); // fast
        addObject(fastVehicle, 100, 200);

        Patroller slowVehicle = new Patroller(path, 1); // slow
        addObject(slowVehicle, 100, 200);

        actCount =0;

        counter = 0;
        countdown = 0;
        maxCount = 1;

        maxVol = 0;

        results = new float [20000];
        String[] tempText = {"Welcome to the Library of Useful Code!","By Jordan Cohen, Pierre Elliott Trudeau HS", "Markham, Ontario, Canada", "Questions?  jordan.cohen@yrdsb.ca"};
        String[] tempText2 = {"Welcome to the Library of Useful Code!","By Jordan Cohen, Pierre Elliott Trudeau HS"};//, "Questions?  jordan.cohen@yrdsb.ca"};

        testBox = new SuperTextBox(tempText, Color.BLACK, Color.WHITE, boringFont, true, 500, 3, Color.YELLOW, -1);
        SuperTextBox testBox2 = new SuperTextBox(tempText2, Color.BLACK, Color.WHITE, boringFont, true, 500, 3, Color.YELLOW, 120);
        //SuperTextBox testBox3 = new SuperTextBox(tempText2, Color.RED, Color.WHITE, boringFont, true, 500, 0, null, 120);

        dialogStyleTextBox = new SuperTextBox (2, new Color (33, 33, 33), new Color(188, 88, 0), new Font ("Brush Script MT", false ,false,24 ), true, 400, 4, Color.WHITE, 200);
        testBox.update();
        int tempY = getHeight() - testBox.getImage().getHeight()/2;
        int tempX = testBox.getImage().getWidth()/2;
        addObject(testBox, tempX, tempY);

        addObject(testBox2, tempX,  tempY - testBox.getImage().getHeight()/2 - testBox2.getImage().getHeight()/2 - 20);
        // addObject(testBox3, getWidth() - testBox3.getImage().getWidth()/2, testBox2.getY());

       
        
        testWidget = new SuperWindow (240, 240, 24,2, "Test Window 0.2", new boolean[]{true, false, true, true, false});
        addObject (testWidget, 650, 250);

        
        imageWindow = new SuperWindow (new GreenfootImage ("alien.jpg"), 0, 0, "", new boolean[]{false, true, false, false, false});
        addObject(imageWindow, 672, 490);

        volumeText = new SuperTextBox ("V[/0jgy|!", Color.GREEN, Color.WHITE, new Font ("Arial", false, false, 24), true, 230, 2, Color.WHITE);
        //System.out.println(SuperTextBox.getFontHeight(new Font ("Arial", false, false, 24)));
        
        // Troubleshooting - need to alter getFontHeight to return an image instead of a size to use it
        //getBackground().drawImage(SuperTextBox.getFontHeight(new Font ("Arial", false, false, 24)), 100, 100);
        testWidget.addObject (volumeText, 120, 160);

        testBox2.update("Arial 24 => " + SuperTextBox.getFontHeight(new Font ("Arial", false, false, 24)));
        testBox2.update("Brush Script MT 24 => " + SuperTextBox.getFontHeight(new Font ("Brush Script MT", false ,false,24 )));
        
        
        
        Actor topObj = volumeText;
        int linesToDraw = 40;
        int lineSpacing = 10;
        int topOfText = topObj.getY() - topObj.getImage().getHeight()/2;

        /**
        Line[] lines = new Line[linesToDraw];
        for (int i = 0; i < lines.length; i++){
            lines[i] = new Line (false, 800, 1);
            addObject(lines[i], 400, topOfText + (lineSpacing * i));
        }
        */        

        //SepRotTester barrel = new SepRotTester();
        //barrel.setTarget(player);
        //barrel.enableStaticRotation();
        //addObject (barrel, 700, 700);
    }

    /**
     * Sharing mouseInfo is important.
     * 
     * Greenfoot can only poll Greenfoot.getMouseInfo() once per act. I suggest
     * putting this in your World so that your Actors can share this. Otherwise,
     * literally only one object can access the mouse data per act, which is not ideal.
     * Note that this World's act method contains m = Greenfoot.getMouseInfo, and that
     * the act order sets the World to act first.
     * 
     * @return MouseInfo the current state of the mouse as captured by World at the start of this act
     */
    public MouseInfo getMouseInfo() {
        if (m == null){
            m = Greenfoot.getMouseInfo();
        }
        return m;
    }

    public void started (){

        total = 20;
    }

    public void act () {

        m = Greenfoot.getMouseInfo();

    }

   
}
