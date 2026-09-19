/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2017,2018,2019,2020,2021,2022,2023,2024  Poul Henriksen and Michael Kolling
 Copyright (C) 2026 SuperGreenfoot contributors

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 This file is subject to the Classpath exception as provided in the
 LICENSE.txt file that accompanied this code.
 */
package greenfoot.guifx.superide;

import bluej.Boot;
import bluej.Config;
import bluej.Main;
import bluej.compiler.CompileType;
import bluej.compiler.Diagnostic;
import bluej.debugger.DebuggerObject;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.Reflective;
import bluej.editor.Editor;
import bluej.extensions2.SourceType;
import bluej.parser.SourceLocation;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.DependentTarget.State;
import bluej.pkgmgr.target.DependentTarget.TargetListener;
import bluej.pkgmgr.target.EditableTarget;
import bluej.pkgmgr.target.Target;
import bluej.pkgmgr.target.actions.ConvertToJavaAction;
import bluej.pkgmgr.target.actions.ConvertToStrideAction;
import bluej.pkgmgr.target.actions.InspectAction;
import bluej.pkgmgr.target.role.UnitTestClassRole;
import bluej.prefmgr.PrefMgr;
import bluej.terminal.Terminal;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.FileUtility;
import bluej.utility.Utility;
import bluej.utility.javafx.AbstractOperation;
import bluej.utility.javafx.FXPlatformConsumer;
import bluej.utility.javafx.FXPlatformFunction;
import bluej.utility.javafx.JavaFXUtil;
import bluej.views.ConstructorView;
import bluej.views.View;
import bluej.views.ViewFilter;
import bluej.views.ViewFilter.StaticOrInstance;
import greenfoot.core.ProjectManager;
import greenfoot.guifx.ExecutionTwirler;
import greenfoot.guifx.GreenfootStage;
import greenfoot.guifx.NewClassDialog;
import greenfoot.guifx.SetPlayerDialog;
import greenfoot.guifx.WorldDisplay;
import greenfoot.guifx.classes.ImportClassDialog;
import greenfoot.guifx.controller.ActorFields;
import greenfoot.guifx.controller.ClassImages;
import greenfoot.guifx.controller.GreenfootProjectController;
import greenfoot.guifx.controller.IdeWindow;
import greenfoot.guifx.controller.ProjectRegistry;
import greenfoot.guifx.controller.SimulationState;
import greenfoot.guifx.controller.UiMode;
import greenfoot.guifx.export.ExportDialog;
import greenfoot.guifx.export.ExportException;
import greenfoot.guifx.images.NewImageClassFrame;
import greenfoot.guifx.images.SelectImageFrame;
import greenfoot.guifx.soundrecorder.SoundRecorderControls;
import greenfoot.guifx.superide.folders.ClassFolders;
import greenfoot.guifx.superide.folders.ProjectSettingsFile;
import greenfoot.util.GreenfootUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The SuperGreenfoot IDE's main window, showing one project through its
 * {@link GreenfootProjectController} (or no project: a welcome panel).
 *
 * <p>The layout and look are the shell's ({@link SuperStage}); this class
 * connects it to the project: the world and its input, the run controls, the
 * Classes panel (with the scenario's virtual folders), the Inspector for the
 * selected class, the menus, and the window's own settings, which live in the
 * scenario's supergreenfoot.properties file rather than in project.greenfoot.
 */
@OnThread(Tag.FXPlatform)
public class SuperIdeWindow extends SuperStage implements IdeWindow
{
    /** The user preference for the dark palette. */
    private static final String DARK_PREF = "supergreenfoot.ui.dark";

    // This window's settings in supergreenfoot.properties:
    private static final String KEY_X = "ui.window.x";
    private static final String KEY_Y = "ui.window.y";
    private static final String KEY_WIDTH = "ui.window.width";
    private static final String KEY_HEIGHT = "ui.window.height";
    private static final String KEY_CLASSES_OPEN = "ui.panel.classes.open";
    private static final String KEY_INSPECTOR_OPEN = "ui.panel.inspector.open";
    private static final String KEY_OUTPUT_OPEN = "ui.panel.output.open";
    private static final String KEY_CLASS_VIEW = "ui.classes.view";
    private static final String KEY_ZOOM = "ui.world.zoom";

    // The Classic window's settings in project.greenfoot, which must survive our saves:
    private static final String[] CLASSIC_WINDOW_KEYS = {"width", "height", "xPosition", "yPosition"};

    // A world that has not sent an image yet is laid out at this size (so that an
    // ask prompt during its construction can be seen):
    private static final double PLACEHOLDER_WORLD_WIDTH = 600;
    private static final double PLACEHOLDER_WORLD_HEIGHT = 400;

    private static boolean lookInitialised = false;

    /** How a class relates to Greenfoot's built-in classes. */
    private enum ClassKind { WORLD, ACTOR, OTHER }

    private GreenfootProjectController controller;
    private Project project;
    private final BooleanProperty hasNoProject = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty showingDebugger = new SimpleBooleanProperty(false);
    private final BooleanProperty actDisabled = new SimpleBooleanProperty(true);
    private final BooleanProperty runDisabled = new SimpleBooleanProperty(true);
    private final BooleanProperty pauseDisabled = new SimpleBooleanProperty(true);
    private final BooleanProperty resetDisabled = new SimpleBooleanProperty(true);
    private final Menu recentProjectsMenu = new Menu(Config.getString("menu.openRecent"));
    private final Menu welcomeRecentMenu = new Menu();

    private final WorldDisplay worldDisplay = new WorldDisplay();
    private final ExecutionTwirler executionTwirler;
    private final SoundRecorderControls soundRecorder;
    private final Node welcomePanel;
    private boolean worldVisible = false;
    private double worldWidth = 0;
    private double worldHeight = 0;
    private boolean worldAttached = false;
    private double attachedWidth = 0;
    private double attachedHeight = 0;
    private ContextMenu worldContextMenu;
    private ContextMenu classMenu;
    private Point2D lastMousePosInScene = new Point2D(0, 0);
    private PlacingActor placingActor;
    private boolean compiling = false;
    /** The actor shown in the Inspector (clicked in the world), or null. */
    private DebuggerObject selectedActor;
    private String selectedActorName;
    /** Re-reads the selected actor shortly after the world changes while paused. */
    private final PauseTransition actorRefreshDelay = new PauseTransition(Duration.millis(120));
    /** The compiler's errors and warnings from each class's latest compile, by class name. */
    private final Map<String, List<Diagnostic>> diagnostics = new HashMap<>();
    // Flag indicating Greenfoot is being exited by the user
    private boolean isQuittingRequest = false;

    // The scenario's classes, as last read from the project:
    private final Map<String, ClassTarget> classTargets = new HashMap<>();
    private final Map<String, ClassKind> classKinds = new HashMap<>();
    private final Map<String, String> superclasses = new HashMap<>();
    private final Map<ClassTarget, TargetListener> targetListeners = new HashMap<>();

    // This window's per-scenario settings (null while no project is shown):
    private ProjectSettingsFile settings;
    private ClassFolders folders = new ClassFolders();
    private final ClassFolders.Listener foldersSaver = this::saveFolders;

    /**
     * A new actor being placed into the world (after construction, or while
     * shift is held for a quick add), shown under the mouse until the user clicks.
     */
    private static final class PlacingActor
    {
        final Region previewNode;
        final BooleanProperty cannotDrop = new SimpleBooleanProperty(true);
        // The actor, if it has already been constructed:
        final DebuggerObject actorObject;
        // The class name, for a quick add (shift-click) of a yet-to-be-constructed actor:
        final String typeName;
        final InvokerRecord invokerRecord;
        final JavaType[] paramTypes;

        PlacingActor(ImageView imageView, String caption, DebuggerObject actorObject, String typeName,
                     InvokerRecord invokerRecord, JavaType[] paramTypes)
        {
            this.actorObject = actorObject;
            this.typeName = typeName;
            this.invokerRecord = invokerRecord;
            this.paramTypes = paramTypes;

            ImageView cannotDropIcon = new ImageView(getClass().getClassLoader().getResource("noParking.png").toExternalForm());
            cannotDropIcon.visibleProperty().bind(cannotDrop);
            Text label = new Text(caption);
            label.getStyleClass().add("actor-preview-text");
            StackPane.setAlignment(label, Pos.TOP_CENTER);
            StackPane.setAlignment(cannotDropIcon, Pos.CENTER);
            StackPane stackPane = new StackPane(imageView, label, cannotDropIcon);
            stackPane.setEffect(new DropShadow(10.0, 3.0, 3.0, Color.BLACK));
            stackPane.setCache(true);
            stackPane.setCacheShape(true);
            stackPane.setCacheHint(CacheHint.QUALITY);
            stackPane.setMouseTransparent(true);
            this.previewNode = stackPane;
        }
    }

    /**
     * Get the SuperGreenfoot look ready (fonts, the user's light or dark choice)
     * and make it the application's look.
     */
    public static void activateLook()
    {
        if (!lookInitialised)
        {
            lookInitialised = true;
            SuperTheme.init(Config.getBlueJLibDir());
            SuperTheme.darkProperty().set(Config.getPropBoolean(DARK_PREF, false));
            JavaFXUtil.addChangeListenerPlatform(SuperTheme.darkProperty(),
                    dark -> Config.putPropString(DARK_PREF, Boolean.toString(dark)));
        }
        SuperTheme.activate();
    }

    /**
     * Go back to JavaFX's default look, as the Classic IDE expects.
     */
    public static void deactivateLook()
    {
        if (SuperTheme.isActive())
        {
            SuperTheme.deactivate();
        }
    }

    /**
     * Make a window showing the given project (or none, if the controller is null).
     * The window registers itself with the ProjectRegistry.
     */
    public SuperIdeWindow(GreenfootProjectController controller)
    {
        super();
        activateLook();
        ProjectRegistry.windowOpened(this);

        Project startProject = controller == null ? null : controller.getProject();
        soundRecorder = new SoundRecorderControls(startProject);
        executionTwirler = new ExecutionTwirler(startProject, controller == null ? null : controller.getDebugHandler());
        executionTwirler.getStyleClass().add("sg-twirler");
        setTwirler(executionTwirler);
        executionTwirler.setWhileTwirling(twirling -> {
            // As in the Classic IDE: show the hung text if we are twirling and either
            // awaiting a reset (greyed out, not asking) or awaiting a pause:
            setHungMessage(Config.getString("centrePanel.message.hung"), twirling
                    && ((worldDisplay.isGreyedOut() && !worldDisplay.isAsking())
                        || getState() == SimulationState.RUNNING_REQUESTED_PAUSE));
        });

        setMenuBar(makeMenuBar());
        setScenarioMenu(makeScenarioMenu());
        setOnAct(() -> {
            if (this.controller != null)
            {
                this.controller.act();
            }
        });
        setOnRunPause(() -> {
            if (this.controller != null)
            {
                this.controller.doRunPause();
            }
        });
        setOnReset(() -> {
            if (this.controller != null)
            {
                this.controller.userReset();
            }
        });
        setOnSwitchToClassic(() -> ProjectRegistry.switchMode(UiMode.CLASSIC));
        setOnShare(this::doShare);
        setOnOpenClass(this::openClass);
        JavaFXUtil.addChangeListenerPlatform(speedProperty(), speed -> {
            if (this.controller != null)
            {
                this.controller.setSpeedFromSlider(speed.intValue());
            }
        });

        ClassBrowserPane browser = getClassBrowser();
        browser.setOnShowClassMenu(this::showClassMenu);
        browser.setOnShowBuiltInMenu(this::showBuiltInMenu);
        browser.setOnNewClass(this::showNewClassMenu);
        JavaFXUtil.addChangeListenerPlatform(browser.selectedClassProperty(), name -> {
            if (name != null)
            {
                clearActorSelection();
                showClassInInspector(name);
            }
            else if (selectedActor == null)
            {
                clearInspector();
            }
        });
        actorRefreshDelay.setOnFinished(e -> refreshSelectedActor());

        getOutput().setPlaceholderText("Nothing printed yet. System.out.println shows up here.");
        getOutput().setOnOpenTerminal(this::showTerminal);
        welcomePanel = makeWelcomePanel();
        setupWorldDisplay();
        setupPlacingActor();
        setupKeys();

        setOnCloseRequest(e -> {
            isQuittingRequest = true;
            ProjectRegistry.closeWindow(this, false);
        });
        // When the window moves to another screen, Greenfoot.getScreenWidth() must follow:
        JavaFXUtil.addChangeListenerPlatform(xProperty(), x -> {
            if (this.controller != null)
            {
                this.controller.screenMayHaveChanged();
            }
        });
        JavaFXUtil.addChangeListenerPlatform(yProperty(), y -> {
            if (this.controller != null)
            {
                this.controller.screenMayHaveChanged();
            }
        });
        JavaFXUtil.addChangeListenerPlatform(focusedProperty(), focused -> {
            if (focused && this.controller != null)
            {
                this.controller.windowActivated();
            }
        });

        fitToScreen();
        if (controller != null)
        {
            showProject(controller);
        }
        else
        {
            clearProject();
        }
    }

    // ------------------------------------------------------------ the project

    @Override
    @OnThread(Tag.FXPlatform)
    public UiMode getUiMode()
    {
        return UiMode.SUPER;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public GreenfootProjectController getController()
    {
        return controller;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Project getProject()
    {
        return project;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showProject(GreenfootProjectController controller)
    {
        Project project = controller.getProject();
        // Is the project already live (moving here from another window), or newly opened?
        boolean alreadyLive = controller.isStarted();

        this.controller = controller;
        this.project = project;
        hasNoProject.set(false);
        if (!alreadyLive)
        {
            ProjectRegistry.projectOpened();
        }
        setWelcome(null);
        setScenarioName(project.getProjectName());
        // Printed output comes to the Output panel instead of popping up the terminal
        // window (which still appears when the scenario reads keyboard input):
        getOutput().clear();
        Terminal terminal = project.getTerminal();
        terminal.setOutputListener(terminalListener);
        terminal.setShowOnOutput(false);
        showingDebugger.bindBidirectional(project.debuggerShowing());
        soundRecorder.setProject(project);
        executionTwirler.setProject(project, controller.getDebugHandler());

        loadSettings(project);
        refreshClasses();

        // A new project: the controller starts looking after it.  A live project: the
        // controller brings this window up to date.
        controller.attachView(this);
        if (!alreadyLive)
        {
            controller.start();
        }
        restoreWindowSettings();
        if (!alreadyLive)
        {
            controller.viewReady();
        }
        updateWorldMessage();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showNoProject()
    {
        clearProject();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void detachProject()
    {
        hideWorldContextMenu();
        setPlacingActor(null);
        executionTwirler.stopTwirling();
        clearProject();
    }

    /**
     * Stop showing any project: the window becomes the empty welcome window.
     */
    private void clearProject()
    {
        if (project != null)
        {
            showingDebugger.unbindBidirectional(project.debuggerShowing());
            // Hand the terminal back (the Classic IDE shows output in the terminal window):
            Terminal terminal = project.getTerminal();
            if (terminal.getOutputListener() == terminalListener)
            {
                terminal.setOutputListener(null);
                terminal.setShowOnOutput(true);
            }
        }
        for (Map.Entry<ClassTarget, TargetListener> entry : targetListeners.entrySet())
        {
            entry.getKey().removeListener(entry.getValue());
        }
        targetListeners.clear();
        classTargets.clear();
        classKinds.clear();
        superclasses.clear();
        diagnostics.clear();
        clearActorSelection();
        folders.removeListener(foldersSaver);
        folders = new ClassFolders();
        settings = null;
        controller = null;
        project = null;
        hasNoProject.set(true);
        worldDisplay.setImage(null);
        worldWidth = 0;
        worldHeight = 0;
        setWorldVisible(false);
        setClassFolders(folders);
        setClasses(new ArrayList<>());
        clearInspector();
        stateChanged(SimulationState.NO_PROJECT, false);
        setCompileState(CompileState.COMPILED, 0);
        getOutput().setProblems(new ArrayList<>(), 0);
        setScenarioName("No scenario");
        setTitle("Super Greenfoot");
        setWelcome(welcomePanel);
    }

    /**
     * Shows the scenario's printed output (and interactive calls) in the Output panel.
     */
    private final Terminal.OutputListener terminalListener = new Terminal.OutputListener()
    {
        @Override
        @OnThread(Tag.FXPlatform)
        public void output(String text, boolean isError)
        {
            getOutput().appendPrinted(text, isError);
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void cleared()
        {
            getOutput().clear();
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void interactiveCall(String callString)
        {
            getOutput().appendCall(callString.endsWith(";")
                    ? callString.substring(0, callString.length() - 1) : callString);
        }
    };

    /**
     * Open the project's terminal window (for keyboard input, or its own options).
     */
    private void showTerminal()
    {
        if (project != null)
        {
            project.getTerminal().showHide(true);
        }
    }

    private SimulationState getState()
    {
        return controller == null ? SimulationState.NO_PROJECT : controller.getState();
    }

    // ------------------------------------------------ the window's own settings

    /**
     * Read this window's settings and the class folders from the scenario's
     * supergreenfoot.properties.
     */
    private void loadSettings(Project project)
    {
        settings = ProjectSettingsFile.load(project.getProjectDir());
        folders.removeListener(foldersSaver);
        folders = ClassFolders.load(settings);
        folders.addListener(foldersSaver);
        setClassFolders(folders);

        leftOpenProperty().set(!"false".equals(settings.get(KEY_CLASSES_OPEN)));
        rightOpenProperty().set(!"false".equals(settings.get(KEY_INSPECTOR_OPEN)));
        bottomOpenProperty().set(!"false".equals(settings.get(KEY_OUTPUT_OPEN)));
        getClassBrowser().viewProperty().set("inheritance".equals(settings.get(KEY_CLASS_VIEW))
                ? ClassBrowserPane.View.INHERITANCE : ClassBrowserPane.View.FOLDERS);
        getWorldHost().zoomProperty().set("pixel".equals(settings.get(KEY_ZOOM))
                ? WorldHost.Zoom.PIXEL_PERFECT : WorldHost.Zoom.FIT);
    }

    /**
     * Put the window where it was when the scenario was last saved in this IDE.
     */
    private void restoreWindowSettings()
    {
        if (settings == null)
        {
            return;
        }
        int width = settings.getInt(KEY_WIDTH, -1);
        int height = settings.getInt(KEY_HEIGHT, -1);
        if (width > 0 && height > 0)
        {
            setWidth(width);
            setHeight(height);
        }
        int x = settings.getInt(KEY_X, Integer.MIN_VALUE);
        int y = settings.getInt(KEY_Y, Integer.MIN_VALUE);
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE)
        {
            Point2D location = Config.ensureOnScreen(x, y);
            setX(location.getX());
            setY(location.getY());
        }
        fitToScreen();
    }

    /**
     * Keep the window within the screen it is on (the default size is large).
     */
    private void fitToScreen()
    {
        List<Screen> screens = Screen.getScreensForRectangle(getX(), getY(), Math.max(1, getWidth()), Math.max(1, getHeight()));
        Rectangle2D bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
        double width = Double.isNaN(getWidth()) ? getScene().getWidth() : getWidth();
        double height = Double.isNaN(getHeight()) ? getScene().getHeight() : getHeight();
        if (width > bounds.getWidth())
        {
            setWidth(Math.max(getMinWidth(), bounds.getWidth()));
        }
        if (height > bounds.getHeight())
        {
            setHeight(Math.max(getMinHeight(), bounds.getHeight()));
        }
    }

    /**
     * Write this window's settings (position, size, panels, views) and the class
     * folders to the scenario's supergreenfoot.properties, if they changed.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void saveWindowSettings()
    {
        if (settings == null)
        {
            return;
        }
        if (!Double.isNaN(getX()) && !Double.isNaN(getWidth()))
        {
            settings.put(KEY_X, Integer.toString((int) Math.max(getX(), 0)));
            settings.put(KEY_Y, Integer.toString((int) Math.max(getY(), 0)));
            settings.put(KEY_WIDTH, Integer.toString((int) getWidth()));
            settings.put(KEY_HEIGHT, Integer.toString((int) getHeight()));
        }
        settings.put(KEY_CLASSES_OPEN, Boolean.toString(leftOpenProperty().get()));
        settings.put(KEY_INSPECTOR_OPEN, Boolean.toString(rightOpenProperty().get()));
        settings.put(KEY_OUTPUT_OPEN, Boolean.toString(bottomOpenProperty().get()));
        settings.put(KEY_CLASS_VIEW, getClassBrowser().viewProperty().get() == ClassBrowserPane.View.INHERITANCE
                ? "inheritance" : "folders");
        settings.put(KEY_ZOOM, getWorldHost().zoomProperty().get() == WorldHost.Zoom.PIXEL_PERFECT ? "pixel" : "fit");
        folders.writeTo(settings, classTargets.isEmpty() ? null : classTargets.keySet());
        writeSettingsFile();
    }

    /**
     * The folders changed: save them straight away.
     */
    private void saveFolders()
    {
        if (settings == null)
        {
            return;
        }
        folders.writeTo(settings, null);
        writeSettingsFile();
    }

    private void writeSettingsFile()
    {
        try
        {
            settings.save();
        }
        catch (IOException e)
        {
            Debug.reportError("Could not save " + settings.getFile(), e);
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void writeViewProperties(Properties p)
    {
        // The project file is rewritten from scratch on every save; keep the Classic
        // window's position and size in it, so that the Classic IDE finds them again:
        if (project != null)
        {
            Properties last = project.getUnnamedPackage().getLastSavedProperties();
            for (String key : CLASSIC_WINDOW_KEYS)
            {
                String value = last.getProperty(key);
                if (value != null)
                {
                    p.put(key, value);
                }
            }
        }
        // This window's own settings go in supergreenfoot.properties instead:
        saveWindowSettings();
    }

    // ------------------------------------------------------------- the world

    private void setupWorldDisplay()
    {
        worldDisplay.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && getState() == SimulationState.RUNNING)
            {
                worldDisplay.requestFocus();
            }
        });
        JavaFXUtil.addFocusListener(worldDisplay, focused -> {
            if (controller != null)
            {
                controller.notifyWorldFocus(focused);
            }
        });
        worldDisplay.addEventFilter(KeyEvent.ANY, e -> {
            if (controller != null)
            {
                controller.forwardWorldKeyEvent(e);
            }
        });
        worldDisplay.setOnContextMenuRequested(e -> {
            if (controller != null)
            {
                Point2D worldPos = worldDisplay.sceneToWorld(new Point2D(e.getSceneX(), e.getSceneY()));
                controller.requestWorldContextMenu(worldPos);
            }
        });
        // The world is scaled to fit, but sceneToWorld maps through the scale, so the
        // scenario sees world pixel coordinates:
        worldDisplay.getImageView().addEventFilter(MouseEvent.ANY, e -> {
            if (controller != null)
            {
                Point2D worldPos = worldDisplay.sceneToWorld(new Point2D(e.getSceneX(), e.getSceneY()));
                controller.forwardWorldMouseEvent(e, worldPos, true);
            }
        });
    }

    /**
     * Show the world display in the centre (at the size of the last world image), or not.
     * The node is only added once: re-adding it would break a mouse press in progress
     * (the world is redrawn many times a second).
     */
    private void updateWorldNode()
    {
        if (worldVisible)
        {
            double w = worldWidth > 0 ? worldWidth : PLACEHOLDER_WORLD_WIDTH;
            double h = worldHeight > 0 ? worldHeight : PLACEHOLDER_WORLD_HEIGHT;
            if (!worldAttached)
            {
                setWorld(worldDisplay, w, h, currentWorldName());
                worldAttached = true;
                attachedWidth = w;
                attachedHeight = h;
            }
            else if (w != attachedWidth || h != attachedHeight)
            {
                setWorldSize(w, h);
                attachedWidth = w;
                attachedHeight = h;
            }
        }
        else if (worldAttached)
        {
            setWorld(null, 0, 0, currentWorldName());
            worldAttached = false;
        }
    }

    private String currentWorldName()
    {
        ClassTarget currentWorld = controller == null ? null : controller.getCurrentWorld();
        return currentWorld == null ? "" : currentWorld.getBaseName();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void setWorldVisible(boolean visible)
    {
        // (This is called for every world image, so only do the work on a change.)
        boolean changed = visible != worldVisible;
        worldVisible = visible;
        updateWorldNode();
        if (changed)
        {
            updateWorldMessage();
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void worldImageSizeChanged(double width, double height)
    {
        worldWidth = width;
        worldHeight = height;
        updateWorldNode();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showWorldImage(Image image)
    {
        if (image != null && (image.getWidth() != worldWidth || image.getHeight() != worldHeight))
        {
            worldWidth = image.getWidth();
            worldHeight = image.getHeight();
            updateWorldNode();
        }
        worldDisplay.setImage(image);
        if (selectedActor != null && getState() == SimulationState.PAUSED)
        {
            // Something may have moved (act, a drag, a method call): look again shortly.
            actorRefreshDelay.playFromStart();
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void greyOutWorld()
    {
        worldDisplay.greyOutWorld();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showAsk(String prompt, FXPlatformConsumer<String> onAnswer)
    {
        worldDisplay.ensureAsking(prompt, onAnswer);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void hideAsk()
    {
        worldDisplay.cancelAsk();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void setExecutionTwirling(boolean twirling)
    {
        if (twirling)
        {
            executionTwirler.startTwirling();
        }
        else
        {
            executionTwirler.stopTwirling();
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void requestWorldFocus()
    {
        worldDisplay.requestFocus();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void clearActorHighlight()
    {
        worldDisplay.clearActorHighlight();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showActorHighlight(int x, int y, int width, int height, int rotation)
    {
        worldDisplay.setActorHighlight(x, y, width, height, rotation);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Point2D worldToScreen(Point2D worldPos)
    {
        return worldDisplay.worldToScreen(worldPos);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showWorldContextMenu(List<MenuItem> items, Point2D worldPos)
    {
        hideWorldContextMenu();
        ContextMenu menu = new ContextMenu();
        menu.setOnHidden(e -> {
            if (worldContextMenu == menu)
            {
                worldContextMenu = null;
            }
        });
        menu.getItems().addAll(items);
        Point2D screenLocation = worldDisplay.worldToScreen(worldPos);
        worldContextMenu = menu;
        menu.show(worldDisplay, screenLocation.getX(), screenLocation.getY());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void hideWorldContextMenu()
    {
        if (worldContextMenu != null)
        {
            worldContextMenu.hide();
            worldContextMenu = null;
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void vmTerminated()
    {
        // Reset the debug VM related state ready for the new debug VM:
        clearActorSelection();
        worldDisplay.setImage(null);
        worldDisplay.cancelAsk();
    }

    /**
     * The message shown in place of the world when there is no world to show, as in
     * the Classic IDE (no world class, the world could not be made, compile first...).
     */
    private void updateWorldMessage()
    {
        SimulationState state = getState();
        ClassTarget currentWorld = controller == null ? null : controller.getCurrentWorld();
        String message;
        if (state == SimulationState.NO_PROJECT)
        {
            // The welcome panel says it all:
            message = "";
        }
        else if (state == SimulationState.NO_WORLD && !hasUserWorld())
        {
            message = Config.getString("superide.message.createWorldClass");
        }
        else if (worldVisible)
        {
            message = "";
        }
        else if (state == SimulationState.NO_WORLD)
        {
            String possibleWorld;
            if (controller != null && controller.hasWorldInstantiationError())
            {
                message = Config.getString("centrePanel.message.error1") + " " + Config.getString("centrePanel.message.error2");
            }
            else if (controller != null && controller.isConstructingWorld())
            {
                message = Config.getString("centrePanel.message.initialising");
            }
            else if ((possibleWorld = getInstantiatableWorld()) != null)
            {
                Properties props = new Properties();
                props.put("exampleWorld", possibleWorld);
                message = Config.getString("superide.message.createWorldObject", null, props, false);
            }
            else if (hasUserWorld())
            {
                message = Config.getString("centrePanel.message.missingWorldConstructor1")
                        + " " + Config.getString("centrePanel.message.missingWorldConstructor2");
            }
            else
            {
                message = Config.getString("superide.message.createWorldClass");
            }
        }
        else if (currentWorld != null && !currentWorld.isCompiled())
        {
            message = Config.getString("centrePanel.message.compile1")
                    + " " + Config.getString("centrePanel.message.compile2");
        }
        else
        {
            message = "";
        }
        setWorldMessage(message);
    }

    private boolean hasUserWorld()
    {
        return classKinds.containsValue(ClassKind.WORLD);
    }

    /**
     * Is there a World subclass that we could instantiate using a package-visible
     * no-args constructor?  If so, return its name.  If not, return null.
     */
    private String getInstantiatableWorld()
    {
        if (project == null)
        {
            return null;
        }
        for (Map.Entry<String, ClassKind> entry : classKinds.entrySet())
        {
            if (entry.getValue() != ClassKind.WORLD)
            {
                continue;
            }
            ClassTarget target = classTargets.get(entry.getKey());
            Class<?> cl = target == null ? null : project.loadClass(target.getQualifiedName());
            if (cl == null || Modifier.isAbstract(cl.getModifiers()))
            {
                continue;
            }
            ViewFilter filter = new ViewFilter(StaticOrInstance.INSTANCE, "");
            boolean hasNoArgs = Arrays.stream(View.getView(cl).getConstructors())
                    .filter(filter)
                    .anyMatch(cv -> !cv.hasParameters());
            if (hasNoArgs)
            {
                return cl.getName();
            }
        }
        return null;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void worldStatusChanged()
    {
        updateWorldMessage();
        setWorldName(currentWorldName());
    }

    // ---------------------------------------------------- run controls, state

    @Override
    @OnThread(Tag.FXPlatform)
    public void stateChanged(SimulationState state, boolean atBreakpoint)
    {
        boolean noProject = state == SimulationState.NO_PROJECT;
        actDisabled.set(state != SimulationState.PAUSED || atBreakpoint);
        runDisabled.set(state != SimulationState.PAUSED || atBreakpoint);
        pauseDisabled.set(state != SimulationState.RUNNING || atBreakpoint);
        resetDisabled.set(noProject);
        setControlsEnabled(!actDisabled.get(), !runDisabled.get() || !pauseDisabled.get(), !noProject);
        setSpeedEnabled(!noProject);
        runningProperty().set(state == SimulationState.RUNNING || state == SimulationState.RUNNING_REQUESTED_PAUSE);
        updateWorldMessage();
        setWorldName(currentWorldName());
        if (state == SimulationState.NO_WORLD || noProject)
        {
            // The world has gone (reset, compile, closed), and its actors with it:
            clearActorSelection();
        }
        else if (selectedActor != null)
        {
            if (state == SimulationState.PAUSED)
            {
                actorRefreshDelay.playFromStart();
            }
            else
            {
                // The actor moves while running; the ring comes back once paused:
                worldDisplay.clearSelectionRing();
            }
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void actorClicked(DebuggerObject actor)
    {
        if (controller == null)
        {
            return;
        }
        if (actor == null)
        {
            clearActorSelection();
            return;
        }
        selectedActor = actor;
        selectedActorName = controller.nameObject(actor);
        // The Inspector now shows the actor rather than a class:
        getClassBrowser().selectedClassProperty().set(null);
        refreshSelectedActor();
    }

    /**
     * Stop showing an actor in the Inspector (and its ring in the world).
     */
    private void clearActorSelection()
    {
        actorRefreshDelay.stop();
        worldDisplay.clearSelectionRing();
        if (selectedActor != null)
        {
            selectedActor = null;
            selectedActorName = null;
            if (getClassBrowser().selectedClassProperty().get() == null)
            {
                clearInspector();
            }
        }
    }

    /**
     * Read the selected actor's fields (off the FX thread) and show them in the
     * Inspector, with the ring around it in the world.  If it has left the world, the
     * selection is cleared.
     */
    private void refreshSelectedActor()
    {
        DebuggerObject actor = selectedActor;
        if (actor == null || controller == null)
        {
            return;
        }
        Utility.runBackground(() -> {
            ActorFields fields = ActorFields.read(actor);
            Platform.runLater(() -> {
                if (selectedActor != actor)
                {
                    return; // Selection changed while we were reading
                }
                if (fields == null)
                {
                    clearActorSelection();
                    return;
                }
                showActorDetails(actor, fields);
            });
        });
    }

    private void showActorDetails(DebuggerObject actor, ActorFields fields)
    {
        String qualified = actor.getClassName();
        String className = qualified.substring(qualified.lastIndexOf('.') + 1);
        InspectorPane.ActorDetails details = new InspectorPane.ActorDetails();
        details.variableName = selectedActorName;
        details.className = className;
        details.image = classImage(className);
        details.worldName = currentWorldName();
        details.location = "(" + fields.x + ", " + fields.y + ")";
        details.preciseLocation = Double.isNaN(fields.preciseX) ? "-"
                : String.format("(%.2f, %.2f)", fields.preciseX, fields.preciseY);
        details.rotation = Double.isNaN(fields.preciseRotation) ? fields.rotation + "°"
                : String.format("%.1f°", fields.preciseRotation);
        details.z = Double.isNaN(fields.z) ? "-" : String.format("%.1f", fields.z);

        List<String> inheritedFrom = new ArrayList<>();
        for (MenuItem item : controller.makeMethodItems(actor))
        {
            if (item instanceof Menu)
            {
                // A submenu of methods inherited from one class ("inherited from Actor"):
                String text = item.getText() == null ? "" : item.getText().trim();
                String from = text.substring(text.lastIndexOf(' ') + 1);
                if (from.equals("Object"))
                {
                    continue; // equals, hashCode... are no use here
                }
                inheritedFrom.add(from);
                for (MenuItem inherited : ((Menu) item).getItems())
                {
                    InspectorPane.MethodEntry entry = methodEntry(inherited);
                    if (entry != null)
                    {
                        details.inheritedMethods.add(entry);
                    }
                }
            }
            else
            {
                InspectorPane.MethodEntry entry = methodEntry(item);
                if (entry != null)
                {
                    details.ownMethods.add(entry);
                }
            }
        }
        details.inheritedFrom = String.join(", ", inheritedFrom);
        details.onInspect = () -> {
            if (controller != null)
            {
                controller.inspectObject(actor, selectedActorName);
            }
        };
        details.onRemove = () -> {
            if (controller != null)
            {
                controller.removeActor(actor);
                actorRefreshDelay.playFromStart();
            }
        };
        showActor(details);

        double w = Math.max(fields.imageWidth, 8);
        double h = Math.max(fields.imageHeight, 8);
        if (getState() == SimulationState.PAUSED)
        {
            worldDisplay.setSelectionRing(fields.pixelX(), fields.pixelY(), w, h, fields.drawnRotation());
        }
    }

    /**
     * A button for the Inspector from one of the actor's method menu items
     * (e.g. "void move(double)"); choosing it calls the method as the menu would.
     */
    private InspectorPane.MethodEntry methodEntry(MenuItem item)
    {
        if (item instanceof SeparatorMenuItem || item instanceof Menu || item.getText() == null)
        {
            return null;
        }
        String text = item.getText().trim();
        int paren = text.indexOf('(');
        int space = paren < 0 ? -1 : text.lastIndexOf(' ', paren);
        String returnType = space > 0 ? text.substring(0, space) : "";
        String signature = space > 0 ? text.substring(space + 1) : text;
        return new InspectorPane.MethodEntry(signature, returnType, () -> {
            item.fire();
            actorRefreshDelay.playFromStart();
        });
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showSpeed(int speed)
    {
        speedProperty().set(speed);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Stage getWindow()
    {
        return this;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public boolean isWindowFocused()
    {
        return isFocused();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void compileStarted()
    {
        compiling = true;
        updateCompileState();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void compileFinished(boolean successful)
    {
        compiling = false;
        updateCompileState();
    }

    /**
     * Show whether the classes are compiled (or have errors) in the top bar and the
     * Problems tab.
     */
    private void updateCompileState()
    {
        List<OutputPane.Problem> problems = new ArrayList<>();
        int errors = 0;
        boolean needsCompile = false;
        List<String> names = new ArrayList<>(classTargets.keySet());
        names.sort(null);
        for (String name : names)
        {
            ClassTarget target = classTargets.get(name);
            State state = target.getState();
            List<Diagnostic> reported = diagnostics.getOrDefault(name, List.of());
            if (state == State.HAS_ERROR)
            {
                boolean listed = false;
                for (Diagnostic d : reported)
                {
                    if (d.getType() == Diagnostic.ERROR)
                    {
                        problems.add(problemFor(target, d));
                        errors++;
                        listed = true;
                    }
                }
                if (!listed)
                {
                    // Compiled before this window was watching, so we don't have the details:
                    problems.add(new OutputPane.Problem(name, "has compile errors. Open it to see them.",
                            true, target::open));
                    errors++;
                }
            }
            else if (state == State.NEEDS_COMPILE)
            {
                needsCompile = true;
            }
            for (Diagnostic d : reported)
            {
                if (d.getType() == Diagnostic.WARNING)
                {
                    problems.add(problemFor(target, d));
                }
            }
        }
        if (compiling)
        {
            setCompileState(CompileState.COMPILING, 0);
        }
        else if (errors > 0)
        {
            setCompileState(CompileState.ERRORS, errors);
        }
        else if (needsCompile)
        {
            setCompileState(CompileState.NEEDS_COMPILE, 0);
        }
        else
        {
            setCompileState(CompileState.COMPILED, 0);
        }
        getOutput().setProblemList(problems, classTargets.size());
    }

    /**
     * A row for the Problems tab; clicking it opens the class at the problem.
     */
    private OutputPane.Problem problemFor(ClassTarget target, Diagnostic d)
    {
        boolean isJava = target.getSourceType() == SourceType.Java;
        String location = target.getBaseName() + (isJava ? ".java" : "")
                + (isJava && d.getStartLine() > 0 ? ":" + d.getStartLine() : "");
        String message = d.getMessage() == null ? "" : d.getMessage().trim();
        return new OutputPane.Problem(location, message, d.getType() == Diagnostic.ERROR, () -> {
            target.open();
            Editor editor = target.getEditor();
            if (editor != null && isJava && d.getStartLine() > 0)
            {
                int line = (int) d.getStartLine();
                int column = (int) Math.max(1, d.getStartColumn());
                int endLine = d.getEndLine() >= line ? (int) d.getEndLine() : line;
                int endColumn = endLine == line ? (int) Math.max(column, d.getEndColumn()) : (int) Math.max(1, d.getEndColumn());
                try
                {
                    editor.assumeText().setSelection(new SourceLocation(line, column),
                            new SourceLocation(endLine, endColumn));
                }
                catch (RuntimeException e)
                {
                    // The position is no longer in the file (it has been edited since); the
                    // editor is open, which is the main thing.
                }
            }
        });
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void compilingFiles(List<File> sourceFiles)
    {
        for (File file : sourceFiles)
        {
            diagnostics.remove(baseName(file));
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void compilerMessage(Diagnostic diagnostic, CompileType type)
    {
        if (diagnostic.getType() == Diagnostic.NOTE || diagnostic.getFileName() == null)
        {
            return;
        }
        diagnostics.computeIfAbsent(baseName(new File(diagnostic.getFileName())), k -> new ArrayList<>())
                .add(diagnostic);
        updateCompileState();
    }

    /** "Player" for .../Player.java (or Player.stride). */
    private static String baseName(File file)
    {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ------------------------------------------------------------- the classes

    @Override
    @OnThread(Tag.FXPlatform)
    public void classesChanged()
    {
        refreshClasses();
    }

    /**
     * Read the scenario's classes again (after a compile, or a class was added,
     * removed or renamed) and show them.
     */
    private void refreshClasses()
    {
        if (project == null)
        {
            return;
        }
        Package pkg = project.getUnnamedPackage();
        List<ClassTarget> targets = new ArrayList<>(pkg.getClassTargets());

        // Listen to each class (as the Classic class diagram does), and only once:
        Map<ClassTarget, TargetListener> stillThere = new HashMap<>();
        for (ClassTarget target : targets)
        {
            TargetListener listener = targetListeners.remove(target);
            if (listener == null)
            {
                listener = makeTargetListener(target);
                target.addListener(listener);
            }
            stillThere.put(target, listener);
        }
        for (Map.Entry<ClassTarget, TargetListener> gone : targetListeners.entrySet())
        {
            gone.getKey().removeListener(gone.getValue());
        }
        targetListeners.clear();
        targetListeners.putAll(stillThere);

        classTargets.clear();
        superclasses.clear();
        for (ClassTarget target : targets)
        {
            classTargets.put(target.getBaseName(), target);
            superclasses.put(target.getBaseName(), findSuperclass(target));
        }
        classKinds.clear();
        for (String name : classTargets.keySet())
        {
            classKinds.put(name, kindOf(name));
        }

        List<ClassEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ClassTarget> entry : classTargets.entrySet())
        {
            String name = entry.getKey();
            String sup = superclasses.get(name);
            entries.add(new ClassEntry(name, "java.lang.Object".equals(sup) ? null : sup, classImage(name)));
        }
        setClasses(entries);

        String selected = getClassBrowser().selectedClassProperty().get();
        if (selected != null && !classTargets.containsKey(selected))
        {
            getClassBrowser().selectedClassProperty().set(null);
        }
        else if (selected != null)
        {
            showClassInInspector(selected);
        }
        updateCompileState();
        updateWorldMessage();
    }

    private TargetListener makeTargetListener(ClassTarget target)
    {
        return new TargetListener()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public void editorOpened()
            {
                setEditorHeaderImage(target);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public void stateChanged(State newState)
            {
                // If a class becomes uncompiled, the world is out of date (as in the Classic IDE):
                if (newState != State.COMPILED && controller != null)
                {
                    controller.classModified();
                }
                updateCompileState();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public void renamed(String newName)
            {
                String oldName = null;
                for (Map.Entry<String, ClassTarget> entry : classTargets.entrySet())
                {
                    if (entry.getValue() == target)
                    {
                        oldName = entry.getKey();
                    }
                }
                if (oldName != null && controller != null)
                {
                    String image = controller.getClassImages().get(oldName);
                    if (image != null)
                    {
                        controller.saveAndMirrorClassImageFilename(newName, image);
                    }
                    folders.classRenamed(oldName, newName);
                }
                refreshClasses();
            }
        };
    }

    /**
     * The superclass named in a class's source (or, failing that, its compiled class).
     */
    private static String findSuperclass(ClassTarget target)
    {
        bluej.parser.symtab.ClassInfo info = target.analyseSource();
        if (info != null)
        {
            return info.getSuperclass();
        }
        try
        {
            Class<?> javaClass = target.getBClass().getJavaClass();
            if (javaClass != null && javaClass.getSuperclass() != null)
            {
                return javaClass.getSuperclass().getName();
            }
        }
        catch (Exception e)
        {
            // Not compiled or not loadable: no superclass known.
        }
        return null;
    }

    /**
     * Whether a class is a World or an Actor (directly or not), following superclasses
     * through the scenario's own classes.
     */
    private ClassKind kindOf(String name)
    {
        String current = name;
        for (int steps = 0; current != null && steps < 100; steps++)
        {
            String sup = superclasses.get(current);
            if ("greenfoot.World".equals(sup) || ("World".equals(sup) && !classTargets.containsKey("World")))
            {
                return ClassKind.WORLD;
            }
            if ("greenfoot.Actor".equals(sup) || ("Actor".equals(sup) && !classTargets.containsKey("Actor")))
            {
                return ClassKind.ACTOR;
            }
            current = classTargets.containsKey(sup) ? sup : null;
        }
        return ClassKind.OTHER;
    }

    /**
     * The image file set for a class, or else for its nearest superclass with one.
     */
    private String classImageFileName(String name)
    {
        if (controller == null)
        {
            return null;
        }
        ClassImages images = controller.getClassImages();
        String current = name;
        for (int steps = 0; current != null && steps < 100; steps++)
        {
            String image = images.get(current);
            if (image != null)
            {
                return image;
            }
            current = classTargets.containsKey(superclasses.get(current)) ? superclasses.get(current) : null;
        }
        return null;
    }

    private File imageFile(String imageFileName)
    {
        return imageFileName == null ? null : new File(new File(project.getProjectDir(), "images"), imageFileName);
    }

    /**
     * The picture shown for a World or Actor class (null for other classes).
     */
    private Image classImage(String name)
    {
        if (classKinds.get(name) == ClassKind.OTHER)
        {
            return null;
        }
        File file = imageFile(classImageFileName(name));
        return file == null ? null : JavaFXUtil.loadImage(file);
    }

    private void setEditorHeaderImage(ClassTarget target)
    {
        Editor editor = target.getEditorIfOpen();
        if (editor != null)
        {
            editor.setHeaderImage(classImage(target.getBaseName()));
        }
    }

    private ClassTarget targetFor(String name)
    {
        return name == null ? null : classTargets.get(name);
    }

    private void openClass(String name)
    {
        ClassTarget target = targetFor(name);
        if (target != null)
        {
            target.open();
        }
    }

    /**
     * Show a class's details in the Inspector (or clear it).
     */
    private void showClassInInspector(String name)
    {
        ClassTarget target = targetFor(name);
        if (target == null)
        {
            clearInspector();
            return;
        }
        InspectorPane.ClassDetails details = new InspectorPane.ClassDetails();
        details.name = name;
        String sup = superclasses.get(name);
        details.superName = "java.lang.Object".equals(sup) ? null : sup;
        details.image = classImage(name);
        for (Map.Entry<String, String> entry : superclasses.entrySet())
        {
            if (name.equals(entry.getValue()))
            {
                details.subclasses.add(entry.getKey());
            }
        }
        details.subclasses.sort(null);
        ConstructorView noArgs = noArgConstructor(target);
        if (noArgs != null)
        {
            details.constructorLabel = "new " + name + "()";
            details.onConstruct = () -> {
                if (controller != null)
                {
                    controller.callStaticMethodOrConstructor(noArgs);
                }
            };
        }
        details.onOpenEditor = target::open;
        details.onSetImage = classKinds.get(name) == ClassKind.OTHER ? null : () -> setImageFor(target);
        details.onDuplicate = target.hasSourceCode() ? () -> duplicateClass(target) : null;
        details.onDelete = () -> deleteClass(target);
        showClass(details);
    }

    /**
     * A class's visible constructor without parameters (if it is compiled and not abstract).
     */
    private ConstructorView noArgConstructor(ClassTarget target)
    {
        if (!target.isCompiled() || project == null)
        {
            return null;
        }
        Class<?> cl = project.loadClass(target.getQualifiedName());
        if (cl == null || Modifier.isAbstract(cl.getModifiers()))
        {
            return null;
        }
        ViewFilter filter = new ViewFilter(StaticOrInstance.INSTANCE, "");
        return Arrays.stream(View.getView(cl).getConstructors())
                .filter(filter)
                .filter(cv -> !cv.hasParameters())
                .findFirst().orElse(null);
    }

    // ------------------------------------------------------ class menus, operations

    /**
     * A class as the target of the class menu's operations.
     */
    private final class ClassItem implements AbstractOperation.ContextualItem<ClassItem>
    {
        private final ClassTarget target;

        ClassItem(ClassTarget target)
        {
            this.target = target;
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public List<? extends AbstractOperation<ClassItem>> getContextOperations()
        {
            return classOperations(target);
        }
    }

    /**
     * The operations the Classic IDE offers on a class: constructors and static
     * methods, open, set image, inspect, duplicate, delete, convert, new subclass.
     */
    private List<AbstractOperation<ClassItem>> classOperations(ClassTarget target)
    {
        String name = target.getBaseName();
        ClassKind kind = classKinds.getOrDefault(name, ClassKind.OTHER);
        Class<?> cl = target.isCompiled() ? target.getPackage().loadClass(target.getQualifiedName()) : null;
        List<AbstractOperation<ClassItem>> ops = new ArrayList<>();
        if (cl != null)
        {
            for (AbstractOperation<? super ClassTarget> op : target.getRole().getClassConstructorOperations(target, cl))
            {
                ops.add(proxy(op));
            }
            for (AbstractOperation<? super ClassTarget> op : target.getRole().getClassStaticOperations(target, cl))
            {
                ops.add(proxy(op));
            }
        }
        else
        {
            ops.add(proxy(new UnitTestClassRole.DummyDisabledOperation(Config.getString("classPopup.needsCompile"),
                    AbstractOperation.MenuItemOrder.COMPILE)));
        }
        if (target.hasSourceCode() || target.getDocumentationFile().exists())
        {
            ops.add(inbuiltOp("open", Config.getString(target.hasSourceCode() ? "edit.class" : "show.apidoc"),
                    AbstractOperation.MenuItemOrder.EDIT, target::open));
        }
        if (kind != ClassKind.OTHER)
        {
            ops.add(inbuiltOp("setImage", Config.getString("select.image"),
                    AbstractOperation.MenuItemOrder.SET_IMAGE, () -> setImageFor(target)));
        }
        if (cl != null)
        {
            ops.add(proxy(new InspectAction(getClassBrowser())));
        }
        if (target.hasSourceCode())
        {
            ops.add(inbuiltOp("duplicate", Config.getString("duplicate.class"),
                    AbstractOperation.MenuItemOrder.DUPLICATE, () -> duplicateClass(target)));
        }
        ops.add(inbuiltOp("remove", Config.getString("remove.class"),
                AbstractOperation.MenuItemOrder.REMOVE, () -> deleteClass(target)));
        if (target.getSourceType() == SourceType.Stride)
        {
            ops.add(proxy(new ConvertToJavaAction(this)));
        }
        else if (target.getSourceType() == SourceType.Java && target.getRole() != null && target.getRole().canConvertToStride())
        {
            ops.add(proxy(new ConvertToStrideAction(this)));
        }
        boolean isFinal = false;
        if (cl != null)
        {
            isFinal = Modifier.isFinal(cl.getModifiers());
        }
        else if (target.getTypeReflective() != null)
        {
            isFinal = target.getTypeReflective().isFinal();
        }
        if (!isFinal)
        {
            ops.add(inbuiltOp("newSubclass", Config.getString("new.sub.class"),
                    AbstractOperation.MenuItemOrder.NEW_SUBCLASS, () -> newSubclassOf(name, kind)));
        }
        return ops;
    }

    private AbstractOperation<ClassItem> inbuiltOp(String identifier, String text,
            AbstractOperation.MenuItemOrder order, Runnable action)
    {
        return new AbstractOperation<ClassItem>(identifier, AbstractOperation.Combine.ONE, null)
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public void activate(List<ClassItem> items)
            {
                action.run();
            }

            @Override
            public List<String> getStyleClasses()
            {
                return List.of(EditableTarget.MENU_STYLE_INBUILT);
            }

            @Override
            public List<ItemLabel> getLabels()
            {
                return List.of(new ItemLabel(new ReadOnlyStringWrapper(text), order));
            }
        };
    }

    private AbstractOperation<ClassItem> proxy(AbstractOperation<? super ClassTarget> op)
    {
        return new AbstractOperation<ClassItem>(op.getIdentifier(), op.combine(), op.getShortcut())
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public void activate(List<ClassItem> items)
            {
                op.activate(Utility.mapList(items, i -> i.target));
            }

            @Override
            public List<ItemLabel> getLabels()
            {
                return op.getLabels();
            }

            @Override
            public List<String> getStyleClasses()
            {
                return op.getStyleClasses();
            }
        };
    }

    private void showClassMenu(String name, Node anchor, double screenX, double screenY)
    {
        ClassTarget target = targetFor(name);
        if (target == null)
        {
            return;
        }
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(AbstractOperation.SortedMenuItem.sortAndAddDividers(
                AbstractOperation.getMenuItems(List.of(new ClassItem(target)), true).getItems(), List.of()));
        showClassContextMenu(menu, screenX, screenY);
    }

    private void showBuiltInMenu(String name, Node anchor, double screenX, double screenY)
    {
        ClassKind kind = ClassTreeModel.WORLD.equals(name) ? ClassKind.WORLD : ClassKind.ACTOR;
        String qualified = "greenfoot." + name;
        ContextMenu menu = new ContextMenu(
                JavaFXUtil.makeMenuItem(Config.getString("show.apidoc"), () -> {
                    if (controller != null)
                    {
                        controller.openGreenfootDocTab(qualified);
                    }
                }, null),
                JavaFXUtil.makeMenuItem(Config.getString("new.sub.class"), () -> newSubclassOf(qualified, kind), null));
        showClassContextMenu(menu, screenX, screenY);
    }

    private void showClassContextMenu(ContextMenu menu, double screenX, double screenY)
    {
        if (classMenu != null)
        {
            classMenu.hide();
        }
        classMenu = menu;
        // So that a "new Crab()" preview starts where the mouse is when the item is chosen:
        menu.getScene().setOnMouseMoved(ev -> setLatestMousePosOnScreen(ev.getScreenX(), ev.getScreenY()));
        setLatestMousePosOnScreen(screenX, screenY);
        menu.show(this, screenX, screenY);
    }

    private void setLatestMousePosOnScreen(double screenX, double screenY)
    {
        Point2D rootPoint = getScene().getRoot().screenToLocal(screenX, screenY);
        if (rootPoint != null)
        {
            lastMousePosInScene = getScene().getRoot().localToScene(rootPoint);
        }
    }

    /**
     * The Classes panel's + button: which kind of class to make.
     */
    private void showNewClassMenu()
    {
        if (project == null)
        {
            return;
        }
        ContextMenu menu = new ContextMenu(
                JavaFXUtil.makeMenuItem("New World subclass...", () -> newSubclassOf("greenfoot.World", ClassKind.WORLD), null),
                JavaFXUtil.makeMenuItem("New Actor subclass...", () -> newSubclassOf("greenfoot.Actor", ClassKind.ACTOR), null),
                JavaFXUtil.makeMenuItem(Config.getString("new.other.class"), () -> newNonImageClass(null), null));
        Point2D mouse = getScene().getRoot().localToScreen(lastMousePosInScene);
        menu.show(this, mouse.getX(), mouse.getY());
    }

    /**
     * Ask for a name (and image), then make a new subclass of the given class.
     *
     * @param parentName  the superclass's name (qualified for Greenfoot's World and Actor)
     * @param kind        whether it is a World or an Actor class (or neither)
     */
    private void newSubclassOf(String parentName, ClassKind kind)
    {
        if (project == null || controller == null)
        {
            return;
        }
        String parentFolder = folders.getFolder(parentName);
        if (kind == ClassKind.WORLD || kind == ClassKind.ACTOR)
        {
            new NewImageClassFrame(this, project).showAndWait().ifPresent(classInfo -> {
                String extendsName = parentName.startsWith("greenfoot.") ? parentName.substring("greenfoot.".length()) : parentName;
                String template = kind == ClassKind.WORLD
                        ? GreenfootStage.getWorldTemplateFileName("greenfoot.World".equals(parentName), classInfo.sourceType)
                        : GreenfootStage.getActorTemplateFileName(classInfo.sourceType);
                ClassTarget created = controller.createClassFile(project.getUnnamedPackage(), extendsName,
                        classInfo.className, classInfo.sourceType, template);
                if (created != null)
                {
                    fileWithParent(classInfo.className, parentFolder);
                    if (classInfo.imageFile != null)
                    {
                        installImage(classInfo.className, classInfo.imageFile);
                    }
                    refreshClasses();
                    getClassBrowser().selectedClassProperty().set(classInfo.className);
                }
            });
        }
        else
        {
            newNonImageClass(parentName);
            // (a class with no World/Actor superclass goes wherever it is filed later)
        }
    }

    /**
     * A new class goes into its superclass's folder, if the superclass is in one.
     */
    private void fileWithParent(String className, String parentFolder)
    {
        if (parentFolder != null && !parentFolder.isEmpty())
        {
            folders.setFolder(className, parentFolder);
        }
    }

    /**
     * Ask for a name, then make a new class that is not a World or Actor.
     *
     * @param superClassName  the superclass, or null for none
     */
    private void newNonImageClass(String superClassName)
    {
        if (project == null || controller == null)
        {
            return;
        }
        NewClassDialog dialog = new NewClassDialog(this, project.getUnnamedPackage().getDefaultSourceType());
        dialog.showAndWait().ifPresent(result -> {
            ClassTarget created = controller.createClassFile(project.getUnnamedPackage(), superClassName,
                    result.className, result.sourceType, "std" + result.sourceType + ".tmpl");
            if (created != null)
            {
                if (superClassName != null)
                {
                    fileWithParent(result.className, folders.getFolder(superClassName));
                }
                refreshClasses();
                getClassBrowser().selectedClassProperty().set(result.className);
            }
        });
    }

    /**
     * Copy an image into the scenario (if need be) and make it a class's image.
     */
    private void installImage(String className, File imageFile)
    {
        String imageFileName = controller.installClassImage(imageFile);
        controller.saveAndMirrorClassImageFilename(className, imageFileName);
    }

    private void setImageFor(ClassTarget target)
    {
        if (project == null || controller == null)
        {
            return;
        }
        String name = target.getBaseName();
        File current = imageFile(controller.getClassImages().get(name));
        new SelectImageFrame(this, project, name, current).showAndWait().ifPresent(file -> {
            installImage(name, file);
            refreshClasses();
            setEditorHeaderImage(target);
        });
    }

    private void duplicateClass(ClassTarget original)
    {
        if (project == null || controller == null)
        {
            return;
        }
        String originalName = original.getDisplayName();
        NewClassDialog dialog = new NewClassDialog(this, original.getSourceType());
        dialog.setSuggestedClassName("CopyOf" + originalName);
        dialog.disableLanguageBox(true);
        dialog.showAndWait().ifPresent(info -> {
            ClassTarget copy = controller.duplicateClassFile(original, info.className);
            if (copy != null)
            {
                String image = controller.getClassImages().get(originalName);
                if (image != null)
                {
                    installImage(info.className, imageFile(image));
                }
                fileWithParent(info.className, folders.getFolder(originalName));
                refreshClasses();
                // The class needs to be compiled for the state of the scenario to be correct:
                controller.compileAddedClass(copy);
            }
        });
    }

    private void deleteClass(ClassTarget target)
    {
        if (controller == null)
        {
            return;
        }
        if (DialogManager.askQuestionFX(this, "really-remove-class") == 0)
        {
            String name = target.getBaseName();
            target.remove();
            folders.classRemoved(name);
            refreshClasses();
            // Check after updating the classes, as that checks whether any world classes remain:
            controller.fireWorldRemovedCheck(target);
        }
    }

    private void doImportClass()
    {
        if (project == null || controller == null)
        {
            return;
        }
        File srcFile = new ImportClassDialog(this).showAndWait().orElse(null);
        if (srcFile == null)
        {
            return;
        }
        String className = GreenfootUtil.removeExtension(srcFile.getName());
        Package pkg = project.getUnnamedPackage();
        // Renaming would be too tricky, so just issue error and stop:
        for (ClassTarget existing : pkg.getClassTargets())
        {
            if (existing.getQualifiedName().equals(className))
            {
                DialogManager.showMessageFX(this, "import-class-exists", className);
                return;
            }
        }
        File srcImage = ImportClassDialog.findImage(srcFile);
        File destImage = null;
        if (srcImage != null)
        {
            destImage = new File(new File(project.getProjectDir(), "images"), srcImage.getName());
            if (destImage.exists())
            {
                DialogManager.showMessageFX(this, "import-image-exists", srcImage.getName());
                destImage = null;
            }
        }

        GreenfootProjectController.ImportedClass imported = controller.importClassFiles(srcFile, className);
        if (imported.classTarget == null)
        {
            return;
        }
        if (srcImage != null && destImage != null && !destImage.exists())
        {
            GreenfootUtil.copyFile(srcImage, destImage);
            installImage(className, destImage);
        }
        refreshClasses();
        controller.compileAddedClass(imported.classTarget);
        if (imported.librariesImported)
        {
            // Must restart debug VM to load the imported library:
            controller.restartVM();
        }
    }

    // ------------------------------------------------------ placing new actors

    private void setupPlacingActor()
    {
        Node root = getScene().getRoot();
        root.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            lastMousePosInScene = new Point2D(e.getSceneX(), e.getSceneY());
            movePlacingPreview();
        });
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> lastMousePosInScene = new Point2D(e.getSceneX(), e.getSceneY()));
        root.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            lastMousePosInScene = new Point2D(e.getSceneX(), e.getSceneY());
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 && placingActor != null && controller != null)
            {
                Point2D dest = worldDisplay.sceneToWorld(lastMousePosInScene);
                if (worldAttached && worldDisplay.worldContains(dest))
                {
                    PlacingActor placing = placingActor;
                    if (placing.actorObject != null)
                    {
                        controller.placeNewActor(placing.actorObject, placing.invokerRecord, placing.paramTypes, dest);
                        // Can't place the same instance twice:
                        setPlacingActor(null);
                    }
                    else
                    {
                        // Shift-clicking: make a new instance each time:
                        controller.quickAddActor(placing.typeName, dest);
                    }
                }
                else
                {
                    // Clicked outside the world: discard the pending actor:
                    setPlacingActor(null);
                }
            }
        });
    }

    private void setPlacingActor(PlacingActor placing)
    {
        if (placingActor != null)
        {
            getGlassPane().getChildren().remove(placingActor.previewNode);
        }
        placingActor = placing;
        if (placing != null)
        {
            // Drawn at the world's scale, so it looks as it will once placed:
            double scale = getWorldHost().scaleProperty().get();
            placing.previewNode.setScaleX(scale);
            placing.previewNode.setScaleY(scale);
            getGlassPane().getChildren().add(placing.previewNode);
            getGlassPane().requestLayout();
            getGlassPane().layout();
            movePlacingPreview();
        }
    }

    private void movePlacingPreview()
    {
        if (placingActor != null)
        {
            Region node = placingActor.previewNode;
            node.setTranslateX(lastMousePosInScene.getX() - node.getWidth() / 2.0);
            node.setTranslateY(lastMousePosInScene.getY() - node.getHeight() / 2.0);
            placingActor.cannotDrop.set(!worldAttached
                    || !worldDisplay.worldContains(worldDisplay.sceneToWorld(lastMousePosInScene)));
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public boolean isPlacingActor()
    {
        return placingActor != null;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void beginPlacingActor(DebuggerObject actor, InvokerRecord ir, JavaType[] paramTypes, Reflective type)
    {
        ImageView imageView = imageViewForClass(type);
        if (imageView != null)
        {
            setPlacingActor(new PlacingActor(imageView, "", actor, null, ir, paramTypes));
        }
    }

    /**
     * The image of an actor class (or of its nearest superclass with one), or the
     * Greenfoot logo if none has an image.
     */
    private ImageView imageViewForClass(Reflective type)
    {
        File file = null;
        Reflective r = type;
        for (int steps = 0; r != null && file == null && steps < 100; steps++)
        {
            if (r.getName().equals("greenfoot.Actor"))
            {
                break;
            }
            String image = controller == null ? null : controller.getClassImages().get(r.getName());
            if (image != null)
            {
                file = imageFile(image);
            }
            r = r.getSuperTypesR().stream().filter(s -> !s.isInterface()).findFirst().orElse(null);
        }
        if (file == null)
        {
            file = new File(Config.getGreenfootLibDir().getAbsolutePath() + "/imagelib/other/greenfoot.png");
        }
        try
        {
            return new ImageView(file.toURI().toURL().toExternalForm());
        }
        catch (MalformedURLException e)
        {
            Debug.reportError(e);
            return null;
        }
    }

    private void setupKeys()
    {
        getScene().addEventFilter(KeyEvent.ANY, e -> {
            // Handled here so that the input does not go through to the user's scenario code:
            if (Config.isMacOS() && e.getEventType() == KeyEvent.KEY_PRESSED && e.getCode() == KeyCode.M && e.isMetaDown())
            {
                setIconified(true);
                return;
            }
            if (project == null || worldDisplay.isAsking())
            {
                return;
            }
            if (e.getEventType() == KeyEvent.KEY_PRESSED)
            {
                if (e.getCode() == KeyCode.ESCAPE && placingActor != null)
                {
                    setPlacingActor(null);
                    return;
                }
                // Shift-click adds an actor of the selected class (only when fully paused,
                // and not while typing, e.g. renaming a folder):
                boolean typing = getScene().getFocusOwner() instanceof TextInputControl;
                ClassTarget selected = targetFor(getClassBrowser().selectedClassProperty().get());
                if (e.getCode() == KeyCode.SHIFT && !typing && placingActor == null && selected != null
                        && getState() == SimulationState.PAUSED)
                {
                    Reflective type = selected.getTypeReflective();
                    if (type != null && controller.getActorReflective().isAssignableFrom(type)
                            && GreenfootProjectController.hasNoArgConstructor(type))
                    {
                        ImageView imageView = imageViewForClass(type);
                        if (imageView != null)
                        {
                            setPlacingActor(new PlacingActor(imageView, "new " + selected.getBaseName() + "()",
                                    null, selected.getBaseName(), null, null));
                        }
                    }
                }
            }
            else if (e.getEventType() == KeyEvent.KEY_RELEASED)
            {
                if (e.getCode() == KeyCode.SHIFT && placingActor != null && placingActor.actorObject == null)
                {
                    setPlacingActor(null);
                }
            }
        });
    }

    // ------------------------------------------------------------ the menus

    private MenuBar makeMenuBar()
    {
        recentProjectsMenu.setOnShowing(e -> updateRecentProjects(recentProjectsMenu));
        updateRecentProjects(recentProjectsMenu);

        Menu scenarioMenu = new Menu(Config.getString("menu.scenario"), null,
                JavaFXUtil.makeMenuItem("java.new.project", new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN),
                        () -> doNewProject(SourceType.Java), null),
                JavaFXUtil.makeMenuItem("stride.new.project", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                        () -> doNewProject(SourceType.Stride), null),
                JavaFXUtil.makeMenuItem("open.project", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
                        this::doOpenScenario, null),
                recentProjectsMenu,
                JavaFXUtil.makeMenuItem("open.gfar.project", null, this::doOpenGfarScenario, null),
                JavaFXUtil.makeMenuItem("project.close", new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
                        () -> ProjectRegistry.closeWindow(this, true), hasNoProject),
                JavaFXUtil.makeMenuItem("project.save", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                        this::doSave, hasNoProject),
                JavaFXUtil.makeMenuItem("project.saveAs", null, this::doSaveAs, hasNoProject),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("show.readme", null, this::openReadme, hasNoProject),
                JavaFXUtil.makeMenuItem("export.project", new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN),
                        this::doShare, hasNoProject));
        if (!Config.isMacOS())
        {
            scenarioMenu.getItems().addAll(new SeparatorMenuItem(),
                    JavaFXUtil.makeMenuItem("greenfoot.quit", new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN),
                            () -> {
                                isQuittingRequest = true;
                                Main.wantToQuit();
                            }, null));
        }

        Menu editMenu = new Menu(Config.getString("menu.edit"), null,
                JavaFXUtil.makeMenuItem("new.other.class", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                        () -> newNonImageClass(null), hasNoProject),
                JavaFXUtil.makeMenuItem("import.action", new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN),
                        this::doImportClass, hasNoProject),
                new SeparatorMenuItem(),
                withDisable(JavaFXUtil.makeMenuItem("New Folder", () -> getClassBrowser().newFolder(), null), hasNoProject));

        Menu controlsMenu = new Menu(Config.getString("menu.controls"), null,
                JavaFXUtil.makeMenuItem("run.once", new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN),
                        () -> {
                            if (controller != null)
                            {
                                controller.act();
                            }
                        }, actDisabled),
                JavaFXUtil.makeMenuItem("controls.run.button", new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN),
                        () -> {
                            if (controller != null)
                            {
                                controller.doRunPause();
                            }
                        }, runDisabled),
                JavaFXUtil.makeMenuItem("controls.pause.button",
                        new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> {
                            if (controller != null)
                            {
                                controller.doRunPause();
                            }
                        }, pauseDisabled),
                JavaFXUtil.makeMenuItem("reset.world", new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN),
                        () -> {
                            if (controller != null)
                            {
                                controller.userReset();
                            }
                        }, resetDisabled),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("controls.fullscreen",
                        new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> {
                            if (controller != null)
                            {
                                controller.toggleFullScreenView();
                            }
                        }, hasNoProject));

        Menu viewMenu = new Menu("View", null,
                JavaFXUtil.makeCheckMenuItem("Classes Panel", leftOpenProperty(), null),
                JavaFXUtil.makeCheckMenuItem("Inspector", rightOpenProperty(), null),
                JavaFXUtil.makeCheckMenuItem("Output Panel", bottomOpenProperty(), null),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("Show Folders", () -> getClassBrowser().viewProperty().set(ClassBrowserPane.View.FOLDERS), null),
                JavaFXUtil.makeMenuItem("Show Inheritance", () -> getClassBrowser().viewProperty().set(ClassBrowserPane.View.INHERITANCE), null),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("Fit World to Window", () -> getWorldHost().zoomProperty().set(WorldHost.Zoom.FIT), null),
                JavaFXUtil.makeMenuItem("Pixel-Perfect World", () -> getWorldHost().zoomProperty().set(WorldHost.Zoom.PIXEL_PERFECT), null),
                new SeparatorMenuItem(),
                JavaFXUtil.makeCheckMenuItem("Dark Theme", SuperTheme.darkProperty(), null));

        CheckMenuItem soundRecorderItem = JavaFXUtil.makeCheckMenuItem(Config.getString("menu.soundRecorder"),
                soundRecorder.getShowingProperty(), new KeyCodeCombination(KeyCode.U, KeyCombination.SHORTCUT_DOWN),
                this::toggleSoundRecorder);
        Menu toolsMenu = new Menu(Config.getString("menu.tools"), null,
                JavaFXUtil.makeMenuItem(Config.getString("save.world"), this::saveTheWorld, null),
                JavaFXUtil.makeMenuItem(Config.getString("menu.tools.recompileAll"), () -> {
                    if (project != null)
                    {
                        project.getUnnamedPackage().rebuild();
                    }
                }, null),
                JavaFXUtil.makeMenuItem("menu.tools.generateDoc", new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN),
                        this::generateDocumentation, hasNoProject),
                soundRecorderItem,
                JavaFXUtil.makeMenuItem("Show Terminal", this::showTerminal, null),
                JavaFXUtil.makeCheckMenuItem(Config.getString("menu.debugger"), showingDebugger,
                        new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN)),
                JavaFXUtil.makeMenuItem("set.player", Config.GREENFOOT_SET_PLAYER_NAME_SHORTCUT, this::setPlayer, hasNoProject),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem(Config.getString("menu.tools.switchToClassic"),
                        () -> ProjectRegistry.switchMode(UiMode.CLASSIC), null));
        if (!Config.isMacOS())
        {
            toolsMenu.getItems().add(JavaFXUtil.makeMenuItem("greenfoot.preferences",
                    new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN),
                    GreenfootStage::showPreferences, null));
        }

        Menu helpMenu = new Menu(Config.getString("menu.help"), null);
        if (!Config.isMacOS())
        {
            helpMenu.getItems().add(JavaFXUtil.makeMenuItem("menu.help.about", null,
                    () -> GreenfootStage.aboutGreenfoot(this), null));
        }
        helpMenu.getItems().addAll(
                JavaFXUtil.makeMenuItem("menu.help.classDoc", null, () -> showApiDoc("index.html"), null),
                JavaFXUtil.makeMenuItem("menu.help.javadoc", null,
                        () -> openWebBrowser(Config.getPropString("url.javaStdLib")), null),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("menu.help.tutorial", null,
                        () -> openWebBrowser(Config.getPropString("greenfoot.url.tutorial")), null),
                JavaFXUtil.makeMenuItem("menu.help.website", null,
                        () -> openWebBrowser(Config.getPropString("greenfoot.url.greenfoot")), null),
                JavaFXUtil.makeMenuItem("menu.help.discuss", null,
                        () -> openWebBrowser(Config.getPropString("greenfoot.url.discuss")), null),
                JavaFXUtil.makeMenuItem("menu.help.moreScenarios", null,
                        () -> openWebBrowser(Config.getPropString("greenfoot.url.scenarios")), null));

        MenuBar menuBar = new MenuBar(scenarioMenu, editMenu, controlsMenu, viewMenu, toolsMenu, helpMenu);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    private static MenuItem withDisable(MenuItem item, BooleanProperty disabled)
    {
        item.disableProperty().bind(disabled);
        return item;
    }

    /**
     * The menu the scenario name in the top bar opens.
     */
    private ContextMenu makeScenarioMenu()
    {
        Menu recent = new Menu(Config.getString("menu.openRecent"));
        recent.setOnShowing(e -> updateRecentProjects(recent));
        updateRecentProjects(recent);
        return new ContextMenu(
                JavaFXUtil.makeMenuItem("java.new.project", null, () -> doNewProject(SourceType.Java), null),
                JavaFXUtil.makeMenuItem("open.project", null, this::doOpenScenario, null),
                recent,
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("project.save", null, this::doSave, hasNoProject),
                JavaFXUtil.makeMenuItem("project.saveAs", null, this::doSaveAs, hasNoProject),
                JavaFXUtil.makeMenuItem("export.project", null, this::doShare, hasNoProject),
                new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem("project.close", null, () -> ProjectRegistry.closeWindow(this, true), hasNoProject));
    }

    private void updateRecentProjects(Menu menu)
    {
        menu.getItems().clear();
        List<String> projects = PrefMgr.getRecentProjects();
        for (String projectToOpen : projects)
        {
            MenuItem item = new MenuItem(projectToOpen);
            item.setOnAction(e -> doOpenScenario(new File(projectToOpen)));
            menu.getItems().add(item);
        }
        if (projects.isEmpty())
        {
            MenuItem none = new MenuItem(Config.getString("menu.noRecentProjects"));
            none.setDisable(true);
            menu.getItems().add(none);
        }
    }

    /**
     * The panel shown when no scenario is open.
     */
    private Node makeWelcomePanel()
    {
        Label title = new Label("Open a scenario to get started");
        title.getStyleClass().add("sg-welcome-title");
        Label hint = new Label("Your recent scenarios are under Open Recent. You can switch to the Classic Greenfoot IDE at any time from the Tools menu.");
        hint.getStyleClass().add("sg-muted");
        hint.setWrapText(true);
        hint.setMaxWidth(420);
        Button newButton = Widgets.button("New Java Scenario...", SuperIcons.PLUS, () -> doNewProject(SourceType.Java));
        Button openButton = Widgets.button("Open Scenario...", SuperIcons.FOLDER, this::doOpenScenario);
        MenuButton recentButton = new MenuButton("Open Recent");
        recentButton.getStyleClass().add("sg-btn");
        recentButton.setOnShowing(e -> {
            updateRecentProjects(welcomeRecentMenu);
            recentButton.getItems().setAll(new ArrayList<>(welcomeRecentMenu.getItems()));
        });
        recentButton.getItems().add(new MenuItem(Config.getString("menu.noRecentProjects")));
        newButton.getStyleClass().add("sg-primary");
        VBox box = new VBox(12, title, hint, newButton, openButton, recentButton);
        box.getStyleClass().add("sg-welcome");
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane holder = new StackPane(box);
        holder.getStyleClass().add("sg-welcome-holder");
        return holder;
    }

    // ------------------------------------------------------ menu actions

    private void doOpenScenario()
    {
        File choice = FileUtility.getOpenProjectFX(this);
        if (choice != null)
        {
            doOpenScenario(choice);
        }
    }

    private void doOpenScenario(File projPath)
    {
        Project p = Project.openProject(projPath.getAbsolutePath());
        if (p != null)
        {
            IdeWindow window = ProjectRegistry.findWindowForProject(p);
            if (window != null)
            {
                window.getWindow().toFront();
            }
            else
            {
                ProjectManager.instance().launchProject(p);
            }
        }
        else
        {
            DialogManager.showErrorFX(this, "could-not-open-project");
        }
    }

    private void doOpenGfarScenario()
    {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Greenfoot Scenarios (*.gfar)", "*.gfar"));
        chooser.setInitialDirectory(PrefMgr.getProjectDirectory());
        File chosen = chooser.showOpenDialog(this);
        if (chosen != null)
        {
            ProjectRegistry.openArchive(chosen, this);
        }
    }

    /**
     * Ask where to make a new scenario, make it (with a MyWorld class) and open it.
     */
    private void doNewProject(SourceType sourceType)
    {
        String title = Config.getString("greenfoot.utilDelegate.newScenario") + " - " + sourceType;
        File newnameFile = FileUtility.getSaveProjectFX(project, this, title);
        if (newnameFile == null)
        {
            return;
        }
        String dirName = newnameFile.getAbsolutePath();
        if (!Project.createNewProject(dirName))
        {
            DialogManager.showErrorFX(this, "cannot-create-project");
            return;
        }
        Project proj = Project.openProject(dirName);
        if (proj == null)
        {
            DialogManager.showErrorFX(this, "could-not-open-project");
            return;
        }
        Package unNamedPkg = proj.getUnnamedPackage();
        Properties props = new Properties(unNamedPkg.getLastSavedProperties());
        props.put("version", Boot.GREENFOOT_API_VERSION);
        unNamedPkg.save(props);
        ProjectManager.instance().launchProject(proj);
        IdeWindow window = ProjectRegistry.findWindowForProject(proj);
        if (window != null && window.getController() != null)
        {
            ClassTarget worldClass = window.getController().createClassFile(unNamedPkg, "World", "MyWorld",
                    sourceType, GreenfootStage.getWorldTemplateFileName(true, sourceType));
            if (worldClass != null)
            {
                window.getController().setCurrentWorld(worldClass);
            }
            window.classesChanged();
            window.getWindow().toFront();
        }
    }

    private void doSave()
    {
        if (controller != null)
        {
            controller.doSave();
        }
    }

    private void doSaveAs()
    {
        if (controller != null)
        {
            controller.doSaveAs();
        }
    }

    private void openReadme()
    {
        if (controller != null)
        {
            controller.openReadme();
        }
    }

    /**
     * Show the Share (export) dialog, if the scenario is compiled.
     */
    private void doShare()
    {
        if (controller == null)
        {
            return;
        }
        if (getState() == SimulationState.NO_WORLD)
        {
            DialogManager.showErrorFX(this, "export-compile-not-compiled");
            return;
        }
        try
        {
            new ExportDialog(this, project, controller, controller.getScenarioInfo(), controller.getCurrentWorld(),
                    worldDisplay.getSnapshot()).showAndWait();
        }
        catch (ExportException e)
        {
            DialogManager.showErrorTextFX(this, e.getMessage());
        }
    }

    private void saveTheWorld()
    {
        if (controller == null)
        {
            return;
        }
        Project p = project;
        FXPlatformFunction<String, Editor> fetchEditorByName = className -> {
            Target t = p.getTarget(className);
            return t instanceof ClassTarget ? ((ClassTarget) t).getEditor() : null;
        };
        controller.saveTheWorld(fetchEditorByName);
    }

    private void generateDocumentation()
    {
        if (project != null)
        {
            String message = project.generateDocumentation();
            if (message.length() != 0)
            {
                DialogManager.showTextFX(this, message);
            }
        }
    }

    private void setPlayer()
    {
        new SetPlayerDialog(this, PrefMgr.getPlayerName().get()).showAndWait()
                .ifPresent(name -> PrefMgr.getPlayerName().set(name));
    }

    private void toggleSoundRecorder(Boolean showing)
    {
        if (showing)
        {
            soundRecorder.show();
        }
        else
        {
            soundRecorder.close();
        }
    }

    private void showApiDoc(String page)
    {
        try
        {
            String customUrl = Utility.getGreenfootApiDocURL(page);
            if (customUrl != null)
            {
                openWebBrowser(customUrl);
            }
        }
        catch (IOException ioe)
        {
            DialogManager.showErrorWithTextFX(this, "cannot-read-apidoc", ioe.getLocalizedMessage());
        }
    }

    private static void openWebBrowser(String url)
    {
        JavaFXUtil.runAfterCurrent(() -> {
            if (!Utility.openWebBrowser(url))
            {
                DialogManager.showErrorFX(null, "cannot-open-browser");
            }
        });
    }
}
