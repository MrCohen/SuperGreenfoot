import greenfoot.*;  // (World, Actor, GreenfootImage, Greenfoot and MouseInfo)
import java.util.ArrayList;

/**
 * <!-- ATTENTION: Switch to Documentation View (API) for a much better experience! -->
 * 
 * <p>A draggable (and lockable) window that can hold UI elements (any Actors).</p>
 * 
 * <h2>Instructions (0.4)</h2>
 * 
 * <p>There are two ways to use this SuperWindow - <u>the "easy" way and the "managed" way</u>.</p>
 * 
 * <p>If you are only going to use it to frame objects and won't be moving around windows, <b>use the easy way</b>. If you are using the Easy Way, 
 * set paint order in your World, as SuperWindow will not re-order anything for you.</p>
 * 
 * <p>The <b>Managed way</b> requires more overhead (setting a paint order, setting a window order, calling the init method after doing the 
 *  preceeding steps).</p>
 * 
 * <p>Basic adding and removing of Actors from SuperWindows: <p>
 * <ul>
 *  <li> Do not add the object to the World - add it to your SuperWindow object using the <b><pre>addObject</pre></b> method. 
 *  (If it's already in the World, you can still follow this method and control should switch to the SuperWindow)</li>
 *  <li> To remove the object, use SuperWindow's removeObject method, NOT the World removeObject method, as this will confuse
 *       SuperWindow, which expects that object to be there. While there is some handling of this situation, it can cause issues.</li>
 * </ul>

 * <br />
 * <h3>Steps to set up the "Managed" Way:</h3>
 * <ol>
 *  <li>At the very beginning of your World's constructor, call the SuperWindow.reset() static method. This will set managed status to false,
 *    which is important because we don't want any "management" until we finish the rest of the preparation steps.</li>
 *    
 *  <li>Continuing in your World's constructor, set a Paint Order. 
 *    To do so, create an ArrayList of Class<> and pass it to the static method setStaticPaintOrder as follows:<br/>
 * <pre><b>
 * //   Setup Paint order for SuperWindow
 * ArrayList&lt;Class&lt;?&gt;&gt; po = new ArrayList&lt;Class&lt;?&gt;&gt;();
 * <!-- For those not looking at the Documentation view, the code is: ArrayList<Class<?>> po = new ArrayList<Class<?>>(); * -->
 * // List Layers from Top (drawn closest to player, over top of everything else) to Bottom:
 * po.add(ShadeBlock.class); // The class drawn on top
 * po.add(Projectile.class);
 * po.add(Player.class);
 * po.add(Item.class);
 * po.add(Block.class); // The class at the bottom
 * // Any classes not listed will be inserted last and unordered
 * SuperWindow.setStaticPaintOrder(po);
 * </b></pre></li>
 *  <li>Continuing in your World's constructor, create your actual SuperWindow objects. To do this, check out the API and call the constructor
 *    that fits with what you're trying to do with your Windows. Create as many SuperWindows as you intend on using.</li>
 *    
 *  <li> Still in the World's constructor, build an ArrayList of all of the SuperWindows. Note that the order that you put them in will be the default
 *    draw order. The draw order is changed whenever a Window is opened or drawn, so this draw order is only for the beginning. For example:<br/>
 * <pre><b>
 * // Setup Window Order for SuperWindow and Initialize it
 * ArrayList<SuperWindow> windows = new ArrayList<SuperWindow>();
 * windows.add(inventoryWindow);
 * windows.add(gameWindow);
 * windows.add(mazeWindow);
 * </b></pre></li>
 *  <li> Finally, start "Managed" state by calling the static initWindowManager method with the list of Windows you just created:<br /><br />
 *      <pre>
 *      SuperWindow.initWindowManager(windows);
 *      </pre>
 *  <li>Your windows should now work as intended! </li>
 * </ol>
 * 
 * <p><b>A caution about the addedToWorld (World w) method:</b></p>
 * <p>SuperWindow relies on a workaround to create paint orders. Put simply, Greenfoot will display
 *    actors in the order in which they are added (first Actor added ends up on the bottom). Greenfoot
 *    does not have any built in methods for arranging actors of the same class in order. So, this
 *    SuperWindow class arranges Actors by frequently removing them, sorting them and re-adding them
 *    to the World. As a result, anything you put into your addedToWorld(World w) method will be called
 *    repeatedly. Make sure that your code works with this. If you need to do work in the addedToWorld
 *    method that you don't want repeated, consider using a boolean or null object check to avoid
 *    repeating commands in an unwanted manner.</p>
 *    
 *    <p>In short, your addedToWorld (World w) method will be called repeatedly if you're using managed
 *    Windows, draggable windows, minimized windows etc. Make sure your code is compatible with this.</p>
 * 
 * <h4>Version 0.3 (December 2022)</h4>
 * <ul>
 *  <li>Height and width are now canvas size, not total size, which is more
 *   intuitive (better coordinate system) and helped me fix tons of bugs.
 *   (Some previous projects will need slight adjustment</li>
 *  <li>Renamed addActor to addObject and made it's signature identical to
 *   World's addObject, for consistency</li>
 * </ul>
 * 
 * <h4>Version 0.4 (Jan 2023)</h4>
 *  <li>Additional Fixes to various contructors</li>
 *  <li>Added missing full detail (including colours) contstructor</li>
 *  <li>Now supports background textures (images) for windows, constrained by borders, compatible with existing feature set
 *   (Not to be confused with a full size Actor, which builds the Window based on the size of the Actor. In this case,
 *   if the image is larger than the bordered space, it will get cut off (which is a feature - so you can use a large texture)</li>
 * 
 * 
 * @author Jordan Cohen 
 * @version 0.4 (January 2023)
 */
public class SuperWindow extends Actor
{
    // class variables - protected to allow for inheritance in the future
    protected GreenfootImage image, framedImage;

    protected boolean locked;
    protected boolean isBeingDragged;
    protected boolean closable;
    protected boolean minable, minimized; // short for minimizable
    protected boolean dragBar;

    // Static values for Window Manager
    private static boolean managed = false;
    private static ArrayList<SuperWindow> windows;
    private static ArrayList<Class<?>> sPaintOrder;

    // Separate Paint Order for per-Window paint ordering
    private ArrayList<Class<?>> paintOrder;

    protected boolean bounded;
    protected boolean readd;

    protected int dragBarHeight, halfDragBarHeight;
    protected int dragOriginX, dragOriginY;
    protected int width, height, totalHeight, currentHeight;
    protected int localX, localY;
    //protected int minimizedX, minimizedY;
    protected int borderThickness;
    protected int minButtonLeft, minButtonRight, closeButtonLeft, closeButtonRight;

    protected String title;

    protected Color borderColor, backgroundColor;

    private Actor temp;
    private GreenfootImage tempImage;

    private MouseInfo mouseInfo;

    private ArrayList<ActorContent> contents;
    private ArrayList<ActorContent> orderedContents;
    private ArrayList<Class<?>> order;

    // constants
    public static final Color WINDOW_BACK_COLOR = new Color (200, 200, 200);
    public static final Color WINDOW_TEXT_COLOR = new Color (0, 0, 15);
    public static final Color WINDOW_BORDER_COLOR_ACTIVE = new Color (255,204,0);
    public static final Color WINDOW_BORDER_COLOR_INACTIVE = Color.WHITE;
    public static final Color WINDOW_DRAGBAR_COLOR = new Color (90, 90, 90);

    public static final int DEFAULT_DRAGBAR_HEIGHT = 18;
    public static final int WINDOW_BORDER_THICKNESS = 2;

    /** 
     * Default Constructor - a closable, minimizable window that can be dragged around
     *
     * @param width the desired width, including border
     * @param height the desired height of the window area NOT including drag bar or border
     * @param title the 
     */
    public SuperWindow (int width, int height, String title) {
        this (width, height,  DEFAULT_DRAGBAR_HEIGHT, WINDOW_BORDER_THICKNESS ,title, new boolean[] {true, false, true, true, false});
    }

    /**
     * <p>Create a SuperWindow. Flags are in an array to avoid very long param list.</p>
     * 
     * <b>flags:</b>
     * <pre>
     * 0 - closable - can close
     * 1 - locked - prevent movement
     * 2 - Draw the dragBar
     * 3 - minable - can be minimized
     * 4 - spawn minimized</pre>
     */
    public SuperWindow (int width, int height, int dragBarHeight, int borderThickness, String title, boolean[] flags){
        this (width, height, dragBarHeight, borderThickness, title, flags, WINDOW_BORDER_COLOR_ACTIVE, Color.LIGHT_GRAY);
    }

    public SuperWindow (int width, int height, int dragBarHeight, int borderThickness, String title, boolean[] flags, Color borderColor, Color backgroundColor){
        // Dimensions and Title
        this.width = width;
        this.height = height;
        currentHeight = height;
        this.title = title;

        
        totalHeight = height + (WINDOW_BORDER_THICKNESS * 2) + dragBarHeight;

        // Colors
        this.borderColor = borderColor;
        this.borderThickness = borderThickness;
        this.backgroundColor = backgroundColor;

        // Flags
        closable = flags[0];
        locked = flags[1];
        dragBar = flags[2];
        minable = flags[3];
        minimized = flags[4];

        // Booleans
        isBeingDragged = false;
        bounded = true; 

        // Draw
        this.dragBarHeight = dragBarHeight;
        halfDragBarHeight = dragBarHeight / 2;
        image = drawWindow (width, height, dragBarHeight, backgroundColor, borderColor, borderThickness, title);
        setImage(image);

        contents = new ArrayList<ActorContent>();

        // The settable background image -- Same size, minus borders
        framedImage = new GreenfootImage (width, height);
        readd = false;
    }

    public SuperWindow (GreenfootImage fullSizeImage, int dragBarHeight, int borderThickness, String text, boolean flags[]){
        this (fullSizeImage.getWidth() + borderThickness * 2, fullSizeImage.getHeight() + borderThickness *2 + dragBarHeight, dragBarHeight, borderThickness, text, flags);
        tempImage = fullSizeImage;
    }
    
    
    /**
     * <p>Create a SuperWindow sized to fit around an Actor. This is useful if you 
     * want one Actor to take up all of the space within a SuperWindow. For example,
     * the imageHolder object in the Image Manipulation Project where the Actor is the whole
     * image and will fill the whole Window.</p>
     * 
     * <b>flags:</b>
     * <pre>
     * 0 - closable - can close
     * 1 - locked - prevent movement
     * 2 - Draw the dragBar
     * 3 - minable - can be minimized
     * 4 - spawn minimized</pre>
     */
    public SuperWindow (Actor fullSizeActor, int dragBarHeight, int borderThickness, String text,  boolean flags[]){
        this (fullSizeActor.getImage().getWidth() + WINDOW_BORDER_THICKNESS * 2, fullSizeActor.getImage().getHeight() + WINDOW_BORDER_THICKNESS *2 + dragBarHeight, dragBarHeight, borderThickness, text, flags);
        temp = fullSizeActor;

    }

    public static void reset () {
        managed = false;
    }

    public void addedToWorld (World w){
        if (managed && !readd){
            sendWindowToFront(this);
        }
        if (temp != null){
            addObject(temp, WINDOW_BORDER_THICKNESS, WINDOW_BORDER_THICKNESS + dragBarHeight);
        }
        if (tempImage != null){
            setFramedImage (tempImage);
        }
    }

    public void setFramedImage (GreenfootImage newImage){
        framedImage.drawImage(newImage, 0, 0);
        image.drawImage(framedImage, borderThickness, borderThickness + (dragBar? dragBarHeight : 0));
    }

    public GreenfootImage getFramedImage(){
        return framedImage;
    }

    public void act()
    {
        mouseInfo = Greenfoot.getMouseInfo(); // see World for corresponding method and note
        // Offsets so that top left corner of window is 0, 0
        int xOffset = getX() - (width / 2);
        int yOffset = getY() - (currentHeight / 2);

        //updatePositions();

        if (mouseInfo != null){
            // Clicks on the Window
            if (Greenfoot.mouseClicked(this)){
                // Calculate where mouse in within Window
                localX = mouseInfo.getX() - xOffset;
                localY = mouseInfo.getY() - yOffset;
                //System.out.println("localX: " + localX + " localY: " + localY);
                if (dragBar && (closable || minable)){
                    if (localX >= closeButtonLeft && localX <= closeButtonRight && localY < dragBarHeight + WINDOW_BORDER_THICKNESS){
                        closeButton();
                        return;

                    } else if (localX > minButtonLeft && localY <= minButtonRight && localY < dragBarHeight + WINDOW_BORDER_THICKNESS) {
                        minimizeButton ();
                        //return;
                    }
                }
            }

            // Functionality for dragBar enabled Windows
            if (dragBar){
                // Drag Start
                if (!locked && !isBeingDragged && Greenfoot.mouseDragged(this)){
                    // is on dragbar?
                    localX = mouseInfo.getX() - xOffset;
                    localY = mouseInfo.getY() - yOffset;
                    if (localY <= dragBarHeight + WINDOW_BORDER_THICKNESS){
                        // Begin drag
                        isBeingDragged = true;
                        // System.out.println("Sending Window to Front ... total actor count = " + getWorld().getObjects(Actor.class).size());
                        if (managed)
                            sendWindowToFront(this);
                    }
                }
                // Drag End
                if (isBeingDragged && Greenfoot.mouseDragEnded(null)){
                    isBeingDragged = false;
                }
                // Drag in Progress
                if (isBeingDragged && Greenfoot.mouseDragged(null)){
                    // Save positions based on previous location
                    
                    arrangeWindows(); // also updates positions
                    // Move window
                    setLocation (mouseInfo.getX() - localX + (width / 2), mouseInfo.getY() - localY + (currentHeight / 2));

                    // Move all contained Actors
                    for (ActorContent ac : contents){
                        Actor a = ac.getActor();
                        a.setLocation (getX() - (width / 2) + ac.getX(), getY() - (height / 2) + ac.getY() + halfDragBarHeight);
                    }
                }
            }
        }
    }

    /**
     * Returns the INTERNAL width. Use getTotalWidth() to get the width including the borders
     * 
     * @return int  the width of the internal canvas of this SuperWindow
     */
    public int getWidth(){
        return width;
    }

    /**
     * Returns the TOTAL width, including the borders. Use getWidth() instead to get the
     * internal width inside the borders.
     * 
     * @return int  the width of this entire window, including the borders
     */
    public int getTotalWidth(){
        return getImage().getWidth();
    }

    public int getHeight () {
        return height;
    }

    public int getTotalHeight(){
        //return height + borderThickness * 2 + dragBarHeight;
        return getImage().getHeight();
    }

    protected void minimizeButton () {
        // toggle minimized
        minimized = !minimized;
        updatePositions(); // Added in 1191 --> Not tested, will this cause issues? TODO- TEST THIS
        if (minimized){ // minimize
            currentHeight = dragBarHeight + (2 * WINDOW_BORDER_THICKNESS);
            // shift position because of new  smaller size
            setLocation (getX(), getY() - (height / 2));
            for (ActorContent ac : contents){
                Actor a = ac.getActor();
                getWorld().removeObject(a);
            } 

        } else { // ... unminimize?
            currentHeight = height;
            // shift position because of new larger size
            setLocation (getX(), getY() + (height / 2));
            for (ActorContent ac : contents){
                Actor a = ac.getActor();
                getWorld().addObject ( a, getX() - (width / 2) + ac.getX(), getY() - (height / 2) + ac.getY() + dragBarHeight/2);
            }

        }
        // redraw appropriately
        image = drawWindow (width, height, dragBarHeight, backgroundColor, borderColor, borderThickness, title);
        if (!minimized)
            image.drawImage(framedImage, borderThickness, borderThickness + (dragBar? dragBarHeight : 0));
        setImage(image);
    }

    // Close button has been clicked
    public void closeButton () {
        // Delete all contained Actors from the World
        for (ActorContent ac : contents){
            Actor a = ac.getActor();
            getWorld().removeObject(a);
        }
        // remove self
        getWorld().removeObject(this);
    }

    /**
     * Still working on how often to call this
     * 
     * Updates the ActorContent contents based on new positions (because otherwise they were saved at their initial positions).
     * I'm trying to avoid having any requirements to call this from outside, but it's public so it can be called however you prefer. 
     * This should be called after movement, but most importantly, before
     * any Windows move or shift or change draw priority.
     * 
     * Put another way, move your Actors using normal setLocation / move() methods, and then after all Actors have moved,
     * you can optionally call this. Don't call it every act, though, as it's heavy. The SuperWindow will call this automatically
     * when the window is minized
     * 
     */
    public void updatePositions (){
        // TODO - Cache offsets each time Window is placed or moved
        int xOffset = getX() - (width / 2); //- borderThickness;
        int yOffset = getY() + halfDragBarHeight - (height / 2);// - borderThickness;
        for (ActorContent ac : contents){
            Actor a = ac.getActor();
            int currentLocalX, currentLocalY;
            //System.out.println(a);
            if (a.getWorld() != null){
                currentLocalX = a.getX() - xOffset;
                currentLocalY = a.getY() - yOffset;
            } else {
                currentLocalX = ac.getX();
                currentLocalY = ac.getY();
            }
            if (currentLocalX != ac.getX() || currentLocalY != ac.getY()){
                ac.setLocation (currentLocalX, currentLocalY);
            }
        }
    }

    /**
     * Add object, but don't add it to contents
     */
    private void pAddObject (ActorContent ac){
        Actor a = ac.getActor();
        int xx = ac.getX();
        int yy = ac.getY();
        if (a.getWorld() == null){
            getWorld().addObject(a, getX() - (width / 2) + xx, getY() - (height / 2) + yy + dragBarHeight/2);
        } else {
            a.setLocation (getX() - (width / 2) + xx, getY() - (height / 2) + yy + dragBarHeight / 2);
        }
    }

    /**
     * Add a new object to this window at relative coordinates xx, yy. 
     * 
     * If this is done in a managed setup, it triggers and update of positions
     * and order, so don't do this too many times per act. Either do it all
     * at the beginning, or do it gradually. 
     */
    public void addObject (Actor a, int xx, int yy){
        //bounded prevents Actors from being drawn over the edge

        reAddObject(a, xx, yy);
        //updateTopWindow(this);
        if (managed){
            arrangeWindows();
        }
    }

    public void removeWindow () {
        for (ActorContent ac : contents){
            Actor a = ac.getActor();
            getWorld().removeObject(a);
        } 
    }

    /**
     * The actual function for adding new contents to the Window
     */
    private void reAddObject (Actor a, int xx, int yy){
        if (bounded){
            xx = Math.min(Math.max (xx, a.getImage().getWidth()/2), width - a.getImage().getWidth()/2);
            yy = Math.min(Math.max(yy, a.getImage().getHeight()/2), height - a.getImage().getHeight()/2);
        }

        contents.add (new ActorContent (a, xx, yy));

        if (!minimized && getWorld()!= null){
            if (a.getWorld() == null){

                getWorld().addObject(a, getX() - (width / 2) + xx, getY() - (height / 2) + yy + dragBarHeight/2);
            } else {
                a.setLocation (getX() - (width / 2) + xx, getY() - (height / 2) + yy + dragBarHeight / 2);
            }
        } 
    }

    public void addObjectOld (Actor a, int xx, int yy){
        contents.add (new ActorContent (a, xx, yy));
        //if (a instanceof Player){
        //    System.out.println("Adding Player at : " + xx + ", " + yy);
        //}
        if (a.getWorld() == null){
            getWorld().addObject(a, getX() - (width / 2)  + xx + a.getImage().getWidth()/2, getY() - (height / 2) + yy + a.getImage().getHeight()/2 + dragBarHeight/2);
        } else {
            a.setLocation (getX() - (width / 2) + xx + a.getImage().getWidth()/2, getY() - (height / 2) + yy + a.getImage().getHeight()/2 + dragBarHeight / 2);
        }
    }

    public void removeObject (Actor a){
        for (ActorContent ac : contents){
            if (ac.getActor() == a){
                removeObject(ac);
                return;
            }
        }
        if (a.getWorld()!=null){
            getWorld().removeObject(a);
        }
    }

    public void removeObject (ActorContent a){
        if (contents.contains(a)){
            contents.remove(a);
        }
        if (a.getActor().getWorld() != null){
            getWorld().removeObject(a.getActor());
        }
    }

    protected GreenfootImage drawWindow(int width, int height, int dragBarHeight, Color backgroundColor, Color borderColor, int borderThickness, String title){
        if (minimized){
            height = 0;
        }

        GreenfootImage temp = new GreenfootImage (width + (2 * borderThickness), (minimized? dragBarHeight : height + dragBarHeight) + (2 * borderThickness) );

        temp.setColor (backgroundColor);
        temp.fill();
        temp.setColor (borderColor);

        for (int i = 0; i < borderThickness; i++){
            temp.drawRect (i, i, temp.getWidth() - (i*2) - 1, temp.getHeight() - (i*2) - 1);
        }

        if (dragBar && dragBarHeight > 0){
            temp.setColor (WINDOW_DRAGBAR_COLOR);
            temp.fillRect (borderThickness, borderThickness, width, dragBarHeight + 1);

            // Draw title
            Font windowFont = new Font ("Arial", false, false, dragBarHeight - 4);
            temp.setFont (windowFont);
            temp.setColor (WINDOW_TEXT_COLOR);
            temp.drawString (title, WINDOW_BORDER_THICKNESS + 4, dragBarHeight - 2);
        }

        if (closable && minable){
            // close
            temp.setColor (new Color (255, 70, 70)); // red-ish

            closeButtonLeft = width - dragBarHeight - WINDOW_BORDER_THICKNESS - 1 ; 
            closeButtonRight  = closeButtonLeft + (2 * dragBarHeight-3);
            temp.fillOval (closeButtonLeft, WINDOW_BORDER_THICKNESS + 1, dragBarHeight-3 , dragBarHeight-3);

            // minimize
            temp.setColor (new Color (255, 255, 150)); // yellow-ish
            minButtonLeft = closeButtonLeft - dragBarHeight-3;
            minButtonRight = minButtonLeft + dragBarHeight - 3 ;
            temp.fillOval (minButtonLeft, WINDOW_BORDER_THICKNESS + 1, dragBarHeight-3 , dragBarHeight-3);

        } 
        else if (closable){
            minButtonLeft = -1;
            minButtonRight = -1;
            // close
            temp.setColor (new Color (255, 70, 70)); // red-ish

            closeButtonLeft = width - dragBarHeight - WINDOW_BORDER_THICKNESS - 1 ; 
            closeButtonRight  = closeButtonLeft + (2 * dragBarHeight-3);
            temp.fillOval (width - dragBarHeight - WINDOW_BORDER_THICKNESS - 1, WINDOW_BORDER_THICKNESS + 1, dragBarHeight-3 , dragBarHeight-3);

        } 
        else if (minable) {
            minButtonLeft = width - dragBarHeight - WINDOW_BORDER_THICKNESS - 1 ; ;
            minButtonRight = minButtonLeft + (2 * dragBarHeight-3);
            temp.fillOval (minButtonLeft, WINDOW_BORDER_THICKNESS + 1, dragBarHeight-3 , dragBarHeight-3);
            closeButtonLeft = -1; 
            closeButtonRight  = -1;
        } else {
            minButtonLeft = -1;
            minButtonRight = -1;
            closeButtonLeft = -1; 
            closeButtonRight  = -1;
        }

        return temp;
    }

    public int getXOffset () {
        return getX() - getImage().getWidth() / 2 + borderThickness;
    }

    public int getYOffset () {
        return getY() - getImage().getHeight() / 2 + borderThickness + dragBarHeight;
    }

    public ArrayList<ActorContent> getContents () {
        return contents;
    }

    public static void initWindowManager(ArrayList<SuperWindow> currentWindows){
        managed = true;
        windows = currentWindows;
        arrangeWindows();

    }

    public static void setStaticPaintOrder (ArrayList<Class<?>> paintOrder){
        sPaintOrder = paintOrder;
    }

    public static void sendWindowToFront (SuperWindow s){
        windows.remove(s);
        windows.add(s);
        arrangeWindows();
    }

    private static void updateTopWindow(SuperWindow requester) {
        if (!managed) return; // Window Manager not yet initialized
        SuperWindow s = windows.get(windows.size()-1);
        if (requester != s){
            s.updatePositions();
            s.applyPaintOrder();
        }
    }

    public static void arrangeWindows (){
        if (sPaintOrder == null){
            if (managed){
                System.out.println("ERROR - need to set static paint order before calling arrangeWindows()");
                System.out.println("This may be triggered because you tried to drag a Window before setting a static paint order.");
            }
            return;
        }

        // for (SuperWindow s : windows){
        for (int i = 0; i < windows.size(); i++){
            SuperWindow s = windows.get(i);
            if (s.getWorld()!=null){
                if (!s.isMinimized() && s.getWorld() !=null){
                    s.updatePositions();                  
                    s.applyPaintOrder();
                }
                else {
                    s.applyMinPaintOrder();
                }
            }
        }
    }

    public boolean isMinimized () {
        return minimized;
    }

    public void applyPaintOrder (ArrayList<Class<?>> paintOrder){
        this.paintOrder = paintOrder;
        applyPaintOrder();
    }

    public ArrayList<ActorContent> getOrderedContents () {
        return orderedContents;
    }

    public void applyPaintOrder (){
        if (paintOrder == null){
            applyPaintOrder(true);
        }  else {
            applyPaintOrder(false);
        }
    }

    // Work in progress- does not work!
    public void updatePaintOrder (){

        if (orderedContents.size() == 0){
            System.out.println("Error - trying to update Paint order before calling applyPaintOrder, or no objects in this SuperWindow.");
            //refreshPaintOrder();
            World w = getWorld();
            int tempX = getX();
            int tempY = getY();
            w.removeObject(this);
            //System.out.println("about to process " + orderedContents.size());
            w.addObject(this, tempX, tempY); 
            return;
        }
        ArrayList<ActorContent> newContents = new ArrayList<ActorContent>();
        int contentIndex = 0;
        ActorContent next = orderedContents.get(contentIndex);
        for (ActorContent ac : contents){
            if (!orderedContents.contains(ac)){
                newContents.add(ac);
            }
        }
        for (Class c : order){
            while (next.getActor().getClass() == c || next.getActor().getClass().getSuperclass() == c){
                contentIndex++;
                if (contentIndex < orderedContents.size() - 1){
                    next = orderedContents.get(contentIndex);
                } else {
                    // should only be very last item that triggers this
                    break;
                }
            }
            for (ActorContent ac : newContents){
                if (ac.getActor().getClass() == c || ac.getActor().getClass().getSuperclass() == c){
                    orderedContents.add(contentIndex, ac);
                }
            }
        }
        refreshPaintOrder();
    }

    public void applyMinPaintOrder (){
        World w = getWorld();
        int tempX = getX();
        int tempY = getY();
        readd = true;
        w.removeObject(this);
        //System.out.println("about to process " + orderedContents.size());
        w.addObject(this, tempX, tempY); 
        readd = false;
    }

    public void applyPaintOrder (boolean useStatic){
        if (getWorld() == null){ 
            return;
        }

        if (useStatic){
            order = sPaintOrder;
        } else {
            order = paintOrder;
        }
        orderedContents = new ArrayList<ActorContent>();
        ArrayList<ActorContent> temp = new ArrayList<ActorContent>();
        for (ActorContent a : contents){
            temp.add (a);
        }
        for (Class c : order){
            // System.out.println("c: " + c);
            ArrayList<ActorContent> removeList = new ArrayList<ActorContent>();
            for (ActorContent ac : temp){
                Actor a = ac.getActor();
                // System.out.println("a.getClass(): " + a.getClass());
                // System.out.println("c: " + c);

                if (a.getClass() == c || a.getClass().getSuperclass() == c){
                    // System.out.print("Identified " + a + " as a " + c + " Temp size before: " + temp.size());
                    orderedContents.add(0, ac);
                    removeList.add(ac);
                    // System.out.println("... temp size after: " + temp.size());
                }

            }
            for (ActorContent ac : removeList){
                temp.remove(ac);
            }
        }
        // Actors not in paint order go last (to be drawn first)
        for (ActorContent a : temp){
            orderedContents.add(0, a);
        }

        refreshPaintOrder();

    }

    private void refreshPaintOrder (){
        World w = getWorld();
        for (ActorContent a : orderedContents){
            //System.out.println("Removing: " + a);
            //a.setLocation(a.getActor().getX(), a.getActor().getY());

            w.removeObject(a.getActor());

        }
        int tempX = getX();
        int tempY = getY();
        readd = true;
        w.removeObject(this);
        //System.out.println("about to process " + orderedContents.size());
        w.addObject(this, tempX, tempY); 
        readd = false;
        for (int i = 0; i < orderedContents.size(); i++){
            ActorContent ac = orderedContents.get(i);
            //System.out.println(""  + i + ac + "");

            pAddObject (ac);
        }
    }
}

/**
 * Container to hold and Actor and an LOCAL position (position x, y on the WINDOW's Canvas, 0,0 in top left)
 */
class ActorContent implements Comparable <ActorContent> {
    private Actor actor;
    private int xx, yy;
    public ActorContent(Actor actor, int xx, int yy){
        this.actor = actor;
        this.xx = xx;
        this.yy = yy;
    }

    public void setLocation (int x, int y){
        xx = x;
        yy = y;
    }

    public int getX() {
        return xx;
    }

    public int getY() {
        return yy;
    }

    public Actor getActor(){
        return actor;
    }

    public String toString () {
        return "Actor: " + actor + " at " + xx + ", " + yy;
    }
    
    // for z-sort, implemented in OOPLessonInBugs 2023
    public int compareTo (ActorContent a){
        return this.getY() - a.getY();
    }
    
}