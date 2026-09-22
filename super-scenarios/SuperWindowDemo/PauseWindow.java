import greenfoot.*;

/**
 * A modal pause window: while it is open, nothing behind it can be clicked.
 * Press P again, or click Resume, to close it.
 */
public class PauseWindow extends SuperWindow
{
    private final Button resume;

    public PauseWindow()
    {
        super(260, 120, "Paused");
        setModal(true);
        setClosable(false);
        setMinimizable(false);
        setBackgroundColor(new Color(50, 50, 60));
        addObject(new Label("The game is paused.", 18, Color.WHITE), 130, 35);
        resume = new Button("Resume");
        addObject(resume, 130, 85);
    }

    public void act()
    {
        if (Greenfoot.mouseClicked(resume)) {
            close();
        }
    }
}
