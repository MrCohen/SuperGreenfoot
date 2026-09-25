/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2017,2018,2019,2019,2020,2021,2022,2023,2024,2026  Poul Henriksen and Michael Kolling
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of f
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package greenfoot.guifx;

import java.util.function.Function;
import bluej.pkgmgr.ProjectLayoutStore;
import bluej.Boot;
import bluej.Config;
import bluej.Main;
import bluej.debugger.DebuggerObject;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.Reflective;
import bluej.editor.Editor;
import bluej.extensions2.SourceType;
import bluej.pkgmgr.AboutDialogTemplate;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.Target;
import bluej.prefmgr.PrefMgr;
import bluej.prefmgr.PrefMgrDialog;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.FileUtility;
import bluej.utility.Utility;
import bluej.utility.javafx.FXPlatformConsumer;
import bluej.utility.javafx.FXPlatformFunction;
import bluej.utility.javafx.JavaFXUtil;
import bluej.utility.javafx.UnfocusableScrollPane;
import greenfoot.core.ProjectManager;
import greenfoot.guifx.ControlPanel.ControlPanelListener;
import greenfoot.guifx.classes.GClassDiagram;
import greenfoot.guifx.classes.GClassDiagram.GClassType;
import greenfoot.guifx.classes.ImportClassDialog;
import greenfoot.guifx.classes.LocalGClassNode;
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
import greenfoot.sound.SoundPreferencePanel;
import greenfoot.util.GreenfootUtil;
import greenfoot.vmcomm.GreenfootDebugHandler;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;


/**
 * Greenfoot's main window: a JavaFX replacement for GreenfootFrame which lives on the server VM.
 */
@OnThread(Tag.FXPlatform)
public class GreenfootStage extends Stage implements ControlPanelListener, IdeWindow
{
    private static final String STAGE_TITLE = "Greenfoot";

    // Flag indicating Greenfoot is being exited by the user
    private boolean isQuittingRequest = false;

    // The controller for the project this window shows (null when the window is empty):
    private GreenfootProjectController controller;
    private Project project;
    private BooleanProperty hasNoProject = new SimpleBooleanProperty(true);
    // The glass pane used to show a new actor while it is being placed:
    private final Pane glassPane;
    // Details of the new actor while it is being placed (null otherwise):
    private final ObjectProperty<NewActor> newActorProperty = new SimpleObjectProperty<>(null);
    private final WorldDisplay worldDisplay;
    // The scroll pane to host the world display
    private final UnfocusableScrollPane worldViewScroll;
    private final GClassDiagram classDiagram;
    // SuperGreenfoot: the class diagram column's width, which the user can set:
    private final ClassColumn classColumn = new ClassColumn();
    // The currently-showing context menu, or null if none
    private ContextMenu contextMenu;
    // Last mouse position, in scene coordinates:
    private Point2D lastMousePosInScene = new Point2D(0, 0);

    // The message shown behind the world (blank content if none): 
    private final Label backgroundMessage;
    // A property tracking whether the world is visible (if false, there should be 
    // a background message set in backgroundMessage)
    private final BooleanProperty worldVisible = new SimpleBooleanProperty(false);

    private final ExecutionTwirler executionTwirler;
    private final ControlPanel controlPanel;

    private final SoundRecorderControls soundRecorder;
    private final Menu recentProjectsMenu = new Menu(Config.getString("menu.openRecent"));
    private final SimpleBooleanProperty showingDebugger = new SimpleBooleanProperty(false);

    /**
     * Details for a new actor being added to the world, after you have made it
     * but before it is ready to be placed.
     */
    private static class NewActor
    {
        // The actual image node (will be a child of glassPane)
        private final Region previewNode;
        // Property tracking whether current location is valid or not
        private final BooleanProperty cannotDrop = new SimpleBooleanProperty(true);
        // The object representing the actor, if the actor has already been created:
        private final DebuggerObject actorObject;
        // The type name if the actor has not been constructed:
        private final String typeName;
        // The invoker record for the construction:
        private final InvokerRecord invokerRecord;
        // The parameter types of the construction:
        private final JavaType[] paramTypes;

        private Region makePreviewNode(ImageView imageView, String invocation)
        {
            ImageView cannotDropIcon = new ImageView(this.getClass().getClassLoader().getResource("noParking.png").toExternalForm());
            cannotDropIcon.visibleProperty().bind(cannotDrop);
            Text newLabel = new Text(invocation);
            newLabel.getStyleClass().add("actor-preview-text");
            
            StackPane.setAlignment(newLabel, Pos.TOP_CENTER);
            StackPane.setAlignment(cannotDropIcon, Pos.CENTER);
            StackPane stackPane = new StackPane(imageView, newLabel, cannotDropIcon);
            stackPane.setEffect(new DropShadow(10.0, 3.0, 3.0, Color.BLACK));
            // Need to cache to speed up display when we move it around to follow the mouse:
            stackPane.setCache(true);
            stackPane.setCacheShape(true);
            stackPane.setCacheHint(CacheHint.QUALITY);
            return stackPane;
        }

        /**
         * Create a NewActor wrapper for an already-constructed actor object.
         * 
         * @param imageView    An image view with the actor image
         * @param actorObject  The actor itself
         * @param ir           Invoker record representing the constructor invocation
         * @param paramTypes   Parameter types of the constructor call
         */
        public NewActor(ImageView imageView, DebuggerObject actorObject,
                InvokerRecord ir, JavaType[] paramTypes)
        {
            this.previewNode = makePreviewNode(imageView, "");
            this.actorObject = actorObject;
            this.invokerRecord = ir;
            this.paramTypes = paramTypes;
            this.typeName = null;
        }

        /**
         * Create a NewActor proxy for a yet-to-be constructed actor object. This is used while
         * shift-click creation of an actor is performed.
         * 
         * @param imageView    An image view with the actor image
         * @param typeName     The name of the actor class
         */
        public NewActor(ImageView imageView, String typeName)
        {
            this.previewNode = makePreviewNode(imageView, "new " + typeName + "()");
            this.actorObject = null;
            this.invokerRecord = null;
            this.paramTypes = null;
            this.typeName = typeName;
        }
    }

    
    /**
     * Creates a GreenfootStage for the given project.
     * @param controller   the controller of the project to show (may be null for none)
     */
    private GreenfootStage(GreenfootProjectController controller)
    {
        Project project = controller == null ? null : controller.getProject();
        GreenfootDebugHandler greenfootDebugHandler = controller == null ? null : controller.getDebugHandler();
        setTitle(STAGE_TITLE);

        ProjectRegistry.windowOpened(this);

        soundRecorder = new SoundRecorderControls(project);

        executionTwirler = new ExecutionTwirler(project, greenfootDebugHandler);
        controlPanel = new ControlPanel(this, executionTwirler);

        backgroundMessage = new Label();
        backgroundMessage.getStyleClass().add("background-message");
        
        Label hungMessage = new Label();
        hungMessage.setText(Config.getString("centrePanel.message.hung"));
        hungMessage.getStyleClass().add("hung-message");
        hungMessage.setVisible(false);
        // Stop hungMessage being used for layout calculation when it's not visible:
        hungMessage.managedProperty().bind(hungMessage.visibleProperty());
        
        worldDisplay = new WorldDisplay();
        worldDisplay.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && getState() == SimulationState.RUNNING)
            {
                worldDisplay.requestFocus();
            }
        });
        // this.controller, not the parameter: a window can be reused for another project.
        JavaFXUtil.addFocusListener(worldDisplay, focused -> {
            if (this.controller != null)
            {
                this.controller.notifyWorldFocus(focused);
            }
        });
        executionTwirler.setWhileTwirling(twirling -> {
            // We show hung text if we are twirling and either:
            //  - We are awaiting a reset (greyed out not due to asking)
            //  - We are awaiting a pause
            hungMessage.setVisible(twirling && ((worldDisplay.isGreyedOut() && !worldDisplay.isAsking()) || getState() == SimulationState.RUNNING_REQUESTED_PAUSE));
        });
        
        classDiagram = new GClassDiagram(this);
        ScrollPane classDiagramScroll = new UnfocusableScrollPane(classDiagram);
        JavaFXUtil.expandScrollPaneContent(classDiagramScroll);
        classDiagramScroll.getStyleClass().add("gclass-diagram-scroll");
        classDiagramScroll.setMinViewportWidth(150.0);
        classDiagramScroll.setMinViewportHeight(200.0);

        worldViewScroll = new UnfocusableScrollPane(worldDisplay);
        worldViewScroll.getStyleClass().add("world-display-scroll");
        JavaFXUtil.expandScrollPaneContent(worldViewScroll);
        worldViewScroll.visibleProperty().bind(worldVisible);
        StackPane worldPane = new StackPane(backgroundMessage, worldViewScroll, hungMessage);
        ImageView shareIcon = new ImageView(new Image(
                getClass().getClassLoader().getResourceAsStream("export-publish.png")));
        shareIcon.setPreserveRatio(true);
        shareIcon.setFitHeight(24.0);
        Button shareButton = new Button(Config.getString("export.project"), shareIcon);
        shareButton.setFocusTraversable(false);
        shareButton.disableProperty().bind(hasNoProject);
        shareButton.setOnAction(e -> doShare());
        // SuperGreenfoot: Share now shares its bar with our own controls, and the class
        // column keeps a width the user can set:
        SuperToolBar topBar = new SuperToolBar(shareButton);
        GreenfootStageContentPane contentPane = new GreenfootStageContentPane(
                worldPane, topBar, classDiagramScroll, controlPanel, classColumn);
        BorderPane root = new BorderPane(contentPane, makeMenu(), null, null, null);
        glassPane = new Pane();
        glassPane.setMouseTransparent(true);
        StackPane stackPane = new StackPane(root, glassPane);
        setupMouseForPlacingNewActor(stackPane);
        Scene scene = new Scene(stackPane);
        Config.addGreenfootStylesheets(scene);
        Config.addPMFStylesheets(scene);
        setScene(scene);

        setMinWidth(700);
        setMinHeight(400);

        setOnCloseRequest((e) -> {
            isQuittingRequest = true;
            doClose(false);
        });

        setupKeyAndMouseHandlers();

        JavaFXUtil.addChangeListenerPlatform(worldVisible, b -> updateBackgroundMessage());
        // SuperGreenfoot: when the window moves to another screen, Greenfoot.getScreenWidth() must follow
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

        if (controller != null)
        {
            showProject(controller);
        }
        // Do this whether we have a project or not:
        updateBackgroundMessage();
        
        JavaFXUtil.addChangeListenerPlatform(focusedProperty(), focused -> {
            if (focused && this.project != null)
            {
                this.controller.windowActivated();
            }
        });
        
        /* Uncomment this to use ScenicView temporarily during development (use reflection to avoid needing to mess with Ant classpath)
        try
        {
            getClass().getClassLoader().loadClass("org.scenicview.ScenicView").getMethod("show", Scene.class).invoke(null, scene);
        }
        catch (Exception e)
        {
            Debug.reportError(e);
        }*/
    }
    
    /**
     * Show a particular project in this window.  May be called
     * multiple times for the same GreenfootStage if it is the
     * only window and the project is closed and another opened again.
     * Therefore everything in here should be able to be
     * executed multiple times for consecutive project opens.
     * 
     * @param controller  The controller of the project to display
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void showProject(GreenfootProjectController controller)
    {
        Project project = controller.getProject();
        // Is the project already live (moving here from another window), or newly opened?
        boolean alreadyLive = controller.isStarted();
        setTitle(STAGE_TITLE + ": " + project.getProjectName());
        
        this.controller = controller;
        this.project = project;
        hasNoProject.set(false);
        if (!alreadyLive)
        {
            ProjectRegistry.projectOpened();
        }

        showingDebugger.bindBidirectional(project.debuggerShowing());
        
        classDiagram.setProject(project);
        classColumn.setProject(project.getProjectDir());
        soundRecorder.setProject(project);
        executionTwirler.setProject(project, controller.getDebugHandler());

        // A new project: the controller starts looking after it (package UI, compiling,
        // the simulation, the debug VM), mirrors the project properties and starts
        // creating the last world.  A live project: the controller brings this window
        // up to date.
        controller.attachView(this);
        if (!alreadyLive)
        {
            controller.start();
        }

        // SuperGreenfoot: the window geometry comes from the user's own layout
        // store (it is per machine); the project file is only consulted for a
        // scenario last saved by an older version or by stock Greenfoot.
        Properties lastSavedProperties = project.getUnnamedPackage().getLastSavedProperties();
        ProjectLayoutStore layouts = ProjectLayoutStore.get();
        File projectDir = project.getProjectDir();
        Function<String, String> geometry = key -> {
            String value = layouts.get(projectDir, key);
            return value != null ? value : lastSavedProperties.getProperty(key);
        };
        // A value that is not a number (a hand-edited or damaged file) counts
        // as missing, so the window opens at its default place and size.
        Double xPosition = ProjectLayoutStore.parseNumber(geometry.apply("xPosition"));
        Double yPosition = ProjectLayoutStore.parseNumber(geometry.apply("yPosition"));
        
        if (xPosition != null && yPosition != null)
        {
            Point2D location = Config.ensureOnScreen(xPosition.intValue(), yPosition.intValue());
            setX(location.getX());
            setY(location.getY());
        }

        Double width = ProjectLayoutStore.parseNumber(geometry.apply("width"));
        Double height = ProjectLayoutStore.parseNumber(geometry.apply("height"));
        if (width != null && width > 0)
        {
            setWidth(width);
        }
        if (height != null && height > 0)
        {
            setHeight(height);
        }

        if (!alreadyLive)
        {
            controller.viewReady();
        }
    }

    /**
     * Updates the message that is shown in place of the world when there is not a world
     * showing, e.g. no project open, no world classes, or problem initialising the world.
     */
    private void updateBackgroundMessage()
    {
        final ClassTarget currentWorld = getCurrentWorld();
        final String message;
        if (getState() == SimulationState.NO_WORLD && !classDiagram.hasUserWorld())
        {
            // May be totally blank project (in which case state remains as UNCOMPILED),
            // hint to the user to create a world:
            message = Config.getString("centrePanel.message.createWorldClass");
        }
        else if (worldVisible.get())
        {
            message = "";
        }
        else if (getState() == SimulationState.NO_WORLD)
        {
            // If we are paused, but no world is visible, the user either
            // needs to instantiate a world (if they have one) or create a world class
            String possibleWorld;
            if (controller != null && controller.hasWorldInstantiationError())
            {
                message = Config.getString("centrePanel.message.error1") + " " + Config.getString("centrePanel.message.error2");
            }
            else if (controller != null && controller.isConstructingWorld())
            {
                message = Config.getString("centrePanel.message.initialising");
            }
            else if ((possibleWorld = classDiagram.getInstantiatableWorld()) != null)
            {
                Properties props = new Properties();
                props.put("exampleWorld", possibleWorld);
                message = Config.getString("centrePanel.message.createWorldObject", null, props, false);
            }
            else if (classDiagram.hasUserWorld())
            {
                message = Config.getString("centrePanel.message.missingWorldConstructor1")
                        + " " + Config.getString("centrePanel.message.missingWorldConstructor2");
            }
            else
            {
                message = Config.getString("centrePanel.message.createWorldClass");
            }
        }
        else if (getState() == SimulationState.NO_PROJECT)
        {
            if (isQuittingRequest)
            {
                message = Config.getString("centrePanel.message.quitGreenfoot");
            }
            else
            {
                message = Config.getString("centrePanel.message.openScenario");
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
        backgroundMessage.setText(message);
    }

    /**
     * Make a new window, showing the given project.  (ProjectRegistry decides whether
     * to show a project in an existing empty window instead.)
     * 
     * @param controller   The controller of the project to display (null for an empty window)
     * @return  the new window
     */
    public static GreenfootStage createWindow(GreenfootProjectController controller)
    {
        return new GreenfootStage(controller);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public UiMode getUiMode()
    {
        return UiMode.CLASSIC;
    }

    @Override
    public void userReset()
    {
        if (controller != null)
        {
            controller.userReset();
        }
    }

    /**
     * The state of the simulation in the project this window shows
     * (NO_PROJECT when the window is empty).
     */
    private SimulationState getState()
    {
        return controller == null ? SimulationState.NO_PROJECT : controller.getState();
    }

    /**
     * The world class that Reset instantiates, if any.
     */
    private ClassTarget getCurrentWorld()
    {
        return controller == null ? null : controller.getCurrentWorld();
    }

    /**
     * Allow the user to choose a scenario to open, and open it.
     */
    private void doOpenScenario()
    {
        File choice = FileUtility.getOpenProjectFX(this);
        if (choice != null)
        {
            doOpenScenario(choice);
        }
    }

    /**
     * Allow the user to choose a gfar file and open its scenario
     * after extracting the gfar file into a new folder.
     */
    public void doOpenGfarScenario()
    {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Greenfoot Scenarios (*.gfar)", "*.gfar"));
        chooser.setInitialDirectory(PrefMgr.getProjectDirectory());

        File chosen = chooser.showOpenDialog(this.getStage());
        if (chosen != null)
        {
            ProjectRegistry.openArchive(chosen, getStage());
        }
    }

    /**
     * Open the specified scenario. Display an error dialog on failure.
     * 
     * @param projPath  path of the scenario to open.
     */
    private void doOpenScenario(File projPath)
    {
        Project p = Project.openProject(projPath.getAbsolutePath());
        if (p != null)
        {
            IdeWindow window = ProjectRegistry.findWindowForProject(p);
            if (window != null)
            {
                // If already open, bring the window to the foreground:
                window.getWindow().toFront();
            }
            else
            {
                ProjectManager.instance().launchProject(p);
            }
        }
        else
        {
            // display error dialog
            DialogManager.showErrorFX(this, "could-not-open-project");
        }
    }

    /**
     * Update the recent projects menu with all recently-opened projects.
     */
    private void updateRecentProjects()
    {
        recentProjectsMenu.getItems().clear();

        List<String> projects = PrefMgr.getRecentProjects();
        for (String projectToOpen : projects)
        {
            MenuItem item = new MenuItem(projectToOpen);
            recentProjectsMenu.getItems().add(item);
            item.setOnAction(e -> {
                doOpenScenario(new File(projectToOpen));
            });
        }
        
        if (projects.isEmpty())
        {
            MenuItem noRecentProjects = new MenuItem(Config.getString("menu.noRecentProjects"));
            noRecentProjects.setDisable(true);
            recentProjectsMenu.getItems().add(noRecentProjects);
        }
    }
    
    /**
     * Close the scenario that this stage is showing.
     * @param keepLast  if true, don't close the last stage; leave it open without a scenario. If
     *                  false, quit BlueJ when the last stage is closed.
     */
    public void doClose(boolean keepLast)
    {
        ProjectRegistry.closeWindow(this, keepLast);
    }

    /**
     * The project's controller (null if the window is empty).
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public GreenfootProjectController getController()
    {
        return controller;
    }

    /**
     * The project has been closed: show this window as empty.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void showNoProject()
    {
        clearProject();
    }

    /**
     * The project is moving to another window: stop showing it here, without
     * closing it.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void detachProject()
    {
        hideContextMenu();
        newActorProperty.set(null);
        executionTwirler.stopTwirling();
        // The recorder has no owner window, so it would outlive this one on an IDE switch.
        soundRecorder.close();
        clearProject();
    }

    /**
     * Remove scenario details, making the stage empty.
     */
    private void clearProject()
    {
        if (project != null)
        {
            showingDebugger.unbindBidirectional(project.debuggerShowing());
            project = null;
        }
        controller = null;
        hasNoProject.set(true);
        worldDisplay.setImage(null);
        worldVisible.set(false);
        classDiagram.setProject(null);
        classColumn.setProject(null);
        // Showing the state will update background message:
        stateChanged(SimulationState.NO_PROJECT, false);
        setTitle(STAGE_TITLE);
    }

    /**
     * Save the project (all editors and all project information).
     */
    private void doSave()
    {
        if (controller != null)
        {
            controller.doSave();
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void writeViewProperties(Properties p)
    {
        // SuperGreenfoot: to the user's layout store, not the project file.
        if (project == null)
        {
            return;
        }
        ProjectLayoutStore layouts = ProjectLayoutStore.get();
        File projectDir = project.getProjectDir();
        layouts.put(projectDir, "width", Integer.toString((int) this.getWidth()));
        layouts.put(projectDir, "height", Integer.toString((int) this.getHeight()));
        layouts.put(projectDir, "xPosition", Integer.toString((int) Math.max(this.getX(), 0)));
        layouts.put(projectDir, "yPosition", Integer.toString((int) Math.max(this.getY(), 0)));
    }

    /**
     * Prompt for a location, save the scenario to the chosen location, and re-open the scenario
     * from its new location.
     */
    private void doSaveAs()
    {
        if (controller != null)
        {
            controller.doSaveAs();
        }
    }

    /**
     * Show a dialog to Export a project. It can be exported to many formats,
     * which the user will pick from. This is only possible in case the project
     * is compiled, otherwise, show an error dialog.
     */
    private void doShare()
    {
        if (getState() == SimulationState.NO_WORLD)
        {
            DialogManager.showErrorFX(this, "export-compile-not-compiled");
        }
        else
        {
            try
            {
                new ExportDialog(this, project, controller, controller.getScenarioInfo(), getCurrentWorld(),
                        worldDisplay.getSnapshot()).showAndWait();
            }
            catch (ExportException e)
            {
                DialogManager.showErrorTextFX(this, e.getMessage());
            }
        }
    }

    /**
     * Perform a single act step, if paused, by adding to the list of pending commands.
     */
    public void act()
    {
        if (controller != null)
        {
            controller.act();
        }
    }
    
    /**
     * Run or pause the simulation (depending on current state).
     */
    public void doRunPause()
    {
        if (controller != null)
        {
            controller.doRunPause();
        }
    }

    /**
     * Opens the given page of the Greenfoot API documentation in a web browser.
     * 
     * @param page   name of the page relative to the root of the API doc.
     */
    public void showApiDoc(String page)
    {
        try {
            String customUrl = Utility.getGreenfootApiDocURL(page);
            if (customUrl != null)
            {
                openWebBrowser(customUrl);
            }
            else
            {
                // SuperGreenfoot: say the documentation is missing instead of doing nothing.
                DialogManager.showErrorWithTextFX(this, "cannot-read-apidoc", Config.getBlueJLibDir() + "/doc/API");
            }
        }
        catch (IOException ioe) {
            DialogManager.showErrorWithTextFX(this, "cannot-read-apidoc", ioe.getLocalizedMessage());
        }
    }
    
    /**
     * Display a URL in a web browser.
     */
    private static void openWebBrowser(String url)
    {
        JavaFXUtil.runAfterCurrent(() -> {
            boolean success = Utility.openWebBrowser(url);
            if (! success)
            {
                DialogManager.showErrorFX(null, "cannot-open-browser");
            }
        });
    }
    
    /**
     * Show a dialog with copyright information.
     */
    private void showCopyright()
    {
        String text = Config.getString("menu.help.copyright.line1") + "\n" +
                Config.getString("menu.help.copyright.line2") + "\n" +
                Config.getString("menu.help.copyright.line3") + "\n" +
                Config.getString("menu.help.copyright.line4");
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        alert.setTitle(Config.getString("menu.help.copyright.title"));
        alert.initOwner(this);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setHeaderText(Config.getString("menu.help.copyright.line0"));
        alert.showAndWait();
    }

    /**
     * Make the menu bar for the whole window.
     */
    /**
     * The Controls menu: act/run/pause/reset from the control panel, plus the
     * SuperGreenfoot full-screen toggle.
     */
    private Menu makeControlsMenu()
    {
        Menu menu = new Menu(Config.getString("menu.controls"), null, controlPanel.makeMenuItems().toArray(new MenuItem[0]));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(JavaFXUtil.makeMenuItem("controls.fullscreen",
                new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                () -> {
                    if (controller != null)
                    {
                        controller.toggleFullScreenView();
                    }
                }, hasNoProject));
        return menu;
    }

    private MenuBar makeMenu()
    {
        recentProjectsMenu.setOnShowing(e -> updateRecentProjects());
        updateRecentProjects();
        
        Menu scenarioMenu = new Menu(Config.getString("menu.scenario"), null,
                JavaFXUtil.makeMenuItem("stride.new.project",
                        new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                        () -> {doNewProject(SourceType.Stride);}, null
                    ),
                    JavaFXUtil.makeMenuItem("java.new.project",
                        new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN),
                        () -> {doNewProject(SourceType.Java);}, null
                    ),
                    JavaFXUtil.makeMenuItem("open.project",
                        new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
                        this::doOpenScenario, null
                    ),
                    recentProjectsMenu,
                    JavaFXUtil.makeMenuItem("open.gfar.project",
                        null, this::doOpenGfarScenario, null
                    ),
                    JavaFXUtil.makeMenuItem("project.close",
                        new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
                        () -> { doClose(true); }, hasNoProject
                    ),
                    JavaFXUtil.makeMenuItem("project.save",
                        new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                        this::doSave, hasNoProject
                    ),
                    JavaFXUtil.makeMenuItem("project.saveAs",
                        null,
                        this::doSaveAs, hasNoProject
                    ),
                    new SeparatorMenuItem(),
                    JavaFXUtil.makeMenuItem("show.readme",
                        null,
                        this::openReadme, hasNoProject
                    ),
                    JavaFXUtil.makeMenuItem("export.project",
                        new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN),
                        this::doShare, hasNoProject
                    )
                );

        if (! Config.isMacOS()) {
            scenarioMenu.getItems().add(new SeparatorMenuItem());
            scenarioMenu.getItems().add(JavaFXUtil.makeMenuItem("greenfoot.quit",
                    new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN),
                    () -> {isQuittingRequest = true; Main.wantToQuit();}, null)
                );
        }

        Menu toolsMenu = new Menu(Config.getString("menu.tools"), null);
        toolsMenu.getItems().addAll(
                JavaFXUtil.makeMenuItem(Config.getString("save.world"), () -> {
                    if (controller == null)
                    {
                        return;
                    }
                    FXPlatformFunction<String, Editor> fetchEditorByName = className -> {
                        Target t = project.getTarget(className);
                        if (t instanceof ClassTarget)
                        {
                            return ((ClassTarget) t).getEditor();
                        }
                        else
                        {
                            return null;
                        }
                    };
                    controller.saveTheWorld(fetchEditorByName);
                }, null),
                JavaFXUtil.makeMenuItem(Config.getString("menu.tools.recompileAll"), () -> project.getUnnamedPackage().rebuild(), null),
                JavaFXUtil.makeMenuItem("menu.tools.generateDoc",new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN),
                        this::generateDocumentation, hasNoProject),
                JavaFXUtil.makeCheckMenuItem(Config.getString("menu.soundRecorder"),
                        soundRecorder.getShowingProperty(),
                        new KeyCodeCombination(KeyCode.U, KeyCombination.SHORTCUT_DOWN),
                        this::toggleSoundRecorder),
                JavaFXUtil.makeCheckMenuItem(Config.getString("menu.debugger"),
                        showingDebugger,
                        new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN)),
                JavaFXUtil.makeMenuItem("set.player",
                        Config.GREENFOOT_SET_PLAYER_NAME_SHORTCUT,
                        this::setPlayer, hasNoProject)
        );

        if (Config.getPropBoolean("supergreenfoot.dev.reopenWindow"))
        {
            // Developer check for moving a live project between windows (not for users):
            MenuItem reopenItem = JavaFXUtil.makeMenuItem("Reopen in New Window (developer test)",
                    () -> ProjectRegistry.reopenInNewWindow(this), null);
            reopenItem.disableProperty().bind(hasNoProject);
            toolsMenu.getItems().add(reopenItem);
        }

        // SuperGreenfoot: the way to the new IDE (every open scenario moves with its world):
        toolsMenu.getItems().addAll(new SeparatorMenuItem(),
                JavaFXUtil.makeMenuItem(Config.getString("menu.tools.switchToSuper"),
                        () -> ProjectRegistry.switchMode(UiMode.SUPER), null));

        if (! Config.isMacOS())
        {
            toolsMenu.getItems().add(JavaFXUtil.makeMenuItem("greenfoot.preferences",
                    new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN),
                    () -> showPreferences(), null));
        }

        Menu helpMenu = new Menu(Config.getString("menu.help"), null);
        if (! Config.isMacOS())
        {
            helpMenu.getItems().add(JavaFXUtil.makeMenuItem("menu.help.about", null, () -> aboutGreenfoot(this), null));
        }
        helpMenu.getItems().addAll(
                JavaFXUtil.makeMenuItem("greenfoot.copyright", null, this::showCopyright, null),
                new SeparatorMenuItem(),
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
                        () -> openWebBrowser(Config.getPropString("greenfoot.url.scenarios")), null)
        );

        MenuBar menuBar = new MenuBar(
            scenarioMenu,
            new Menu(Config.getString("menu.edit"), null,
                JavaFXUtil.makeMenuItem("new.other.class", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                            () -> newNonImageClass(project.getUnnamedPackage(), null), hasNoProject),
                JavaFXUtil.makeMenuItem("import.action",
                        new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN), () -> doImportClass(),
                        hasNoProject)
            ),
            makeControlsMenu(),
            toolsMenu,
            helpMenu
        );

        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    /**
     * Show/hide the sound soundRecorder.
     *
     * @param showing if true show the soundRecorder, hide for false.
     */
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

    /**
     * Generates the Documentation for the current scenario
     */
    private void generateDocumentation()
    {
        String message = project.generateDocumentation();
        if (message.length() != 0) {
            DialogManager.showTextFX(this, message);
        }
    }

    /**
     * Opens a set player dialog so the user can enter a player name
     * to be stored in the project's properties.
     */
    private void setPlayer()
    {
        SetPlayerDialog dlg = new SetPlayerDialog(this, PrefMgr.getPlayerName().get());
        dlg.showAndWait().ifPresent(name -> PrefMgr.getPlayerName().set(name));
    }

    /**
     * Shows preferences Dialog
     */
    public static void showPreferences()
    {
        PrefMgrDialog.showDialog(null, new SoundPreferencePanel(), new UiModePreferencePanel());
    }

    /**
     * Update scenario controls (act, run/pause, reset etc) according to scenario state.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void stateChanged(SimulationState newState, boolean atBreakpoint)
    {
        controlPanel.updateState(newState, atBreakpoint);
        updateBackgroundMessage();
    }

    /**
     * Setup mouse listeners for showing a new actor underneath the mouse cursor
     */
    private void setupMouseForPlacingNewActor(StackPane stackPane)
    {
        stackPane.setOnMouseMoved(e -> {
            lastMousePosInScene = new Point2D(e.getSceneX(), e.getSceneY());
            if (newActorProperty.get() != null)
            {
                // We use e.getX/getY here, which is already local to StackPane:
                // TranslateX/Y seems to have a bit less lag than LayoutX/Y:
                newActorProperty.get().previewNode.setTranslateX(e.getX() - newActorProperty.get().previewNode.getWidth() / 2.0);
                newActorProperty.get().previewNode.setTranslateY(e.getY() - newActorProperty.get().previewNode.getHeight() / 2.0);

                newActorProperty.get().cannotDrop.set(!worldDisplay.worldContains(worldDisplay.sceneToWorld(lastMousePosInScene)));
            }
        });
        stackPane.setOnMouseClicked(e -> {
            lastMousePosInScene = new Point2D(e.getSceneX(), e.getSceneY());
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 && newActorProperty.get() != null)
            {
                Point2D dest = worldDisplay.sceneToWorld(lastMousePosInScene);
                if (worldDisplay.worldContains(dest))
                {
                    NewActor newActor = newActorProperty.get();
                    if (newActor.actorObject != null)
                    {
                        // Place the already-constructed actor.
                        controller.placeNewActor(newActor.actorObject, newActor.invokerRecord,
                                newActor.paramTypes, dest);
                        // Can't place same instance twice:
                        newActorProperty.set(null);
                    }
                    else
                    {
                        // Must be shift-clicking; will need to make a new instance:
                        controller.quickAddActor(newActor.typeName, dest);
                    }
                }
                else
                {
                    // They clicked but outside the world: discard pending actor:
                    newActorProperty.set(null);
                }
            }
        });
        newActorProperty.addListener(new ChangeListener<NewActor>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends NewActor> prop, NewActor oldVal, NewActor newVal)
            {
                if (oldVal != null)
                {
                    glassPane.getChildren().remove(oldVal.previewNode);
                }

                if (newVal != null)
                {
                    glassPane.getChildren().add(newVal.previewNode);
                    // Need to do a layout to get the correct width and height:
                    glassPane.requestLayout();
                    glassPane.layout();

                    newVal.previewNode.setTranslateX(lastMousePosInScene.getX() - newVal.previewNode.getWidth() / 2.0);
                    newVal.previewNode.setTranslateY(lastMousePosInScene.getY() - newVal.previewNode.getHeight() / 2.0);
                    newVal.cannotDrop.set(!worldDisplay.worldContains(worldDisplay.sceneToWorld(lastMousePosInScene)));
                }
            }
        });
    }

    /**
     * Update our latest mouse position to the given *SCREEN* (not Scene!) position.
     * Used by GClassDiagram to track position even while a context menu is showing.
     */
    public void setLatestMousePosOnScreen(double screenX, double screenY)
    {
        // There is no screenToScene call, so we must go via this convoluted path:
        Point2D rootPoint = getScene().getRoot().screenToLocal(screenX, screenY);
        lastMousePosInScene = getScene().getRoot().localToScene(rootPoint);
    }

    /**
     * Sets up the writing
     * of keyboard and mouse events back to the buffer.
     */
    private void setupKeyAndMouseHandlers()
    {
        getScene().addEventFilter(KeyEvent.ANY, e -> {
            // We handle this here because we don't want the input to go through to the user's scenario code:
            if (Config.isMacOS() && e.getEventType() == KeyEvent.KEY_PRESSED && e.getCode() == KeyCode.M && e.isMetaDown())
            {
                setIconified(true);
                return;
            }
            
            if (project == null)
            {
                return;
            }
            
            // Ignore keypresses if we are currently waiting for an ask-answer:
            if (worldDisplay.isAsking())
            {
                return;
            }

            if (e.getEventType() == KeyEvent.KEY_PRESSED)
            {
                if (e.getCode() == KeyCode.ESCAPE && newActorProperty.get() != null)
                {
                    newActorProperty.set(null);
                    return;
                }

                // We only want fully paused; if they've requested a run, don't allow a shift-click:
                boolean paused = getState() == SimulationState.PAUSED;
                ClassTarget selectedClassTarget = classDiagram.getSelectedClassTarget();
                if (e.getCode() == KeyCode.SHIFT && newActorProperty.get() == null && selectedClassTarget != null && paused)
                {
                    // Holding shift, so show actor preview if it is an actor with no-arg constructor:
                    Reflective type = selectedClassTarget.getTypeReflective();
                    if (type != null
                            && controller.getActorReflective().isAssignableFrom(type)
                            && GreenfootProjectController.hasNoArgConstructor(type))
                    {
                        newActorProperty.set(new NewActor(getImageViewForClass(type), selectedClassTarget.getBaseName()));
                    }
                }
            }
            else if (e.getEventType() == KeyEvent.KEY_RELEASED)
            {
                if (e.getCode() == KeyCode.SHIFT && newActorProperty.get() != null
                        && newActorProperty.get().actorObject == null)
                {
                    newActorProperty.set(null);
                }
            }
        });
        
        worldDisplay.addEventFilter(KeyEvent.ANY, this::forwardWorldKeyEvent);

        worldDisplay.setOnContextMenuRequested(e -> {
            if (controller == null)
            {
                return;
            }
            
            Point2D worldPos = worldDisplay.sceneToWorld(new Point2D(e.getSceneX(), e.getSceneY()));
            controller.requestWorldContextMenu(worldPos);
        });
        worldDisplay.getImageView().addEventFilter(MouseEvent.ANY, e -> {
            if (controller != null)
            {
                Point2D worldPos = worldDisplay.sceneToWorld(new Point2D(e.getSceneX(), e.getSceneY()));
                controller.forwardWorldMouseEvent(e, worldPos, true);
            }
        });
        // SuperGreenfoot: the mouse wheel reaches the scenario too (SuperWindow scrolling).
        worldDisplay.getImageView().addEventFilter(ScrollEvent.SCROLL, e -> {
            if (controller != null)
            {
                Point2D worldPos = worldDisplay.sceneToWorld(new Point2D(e.getSceneX(), e.getSceneY()));
                controller.forwardWorldScrollEvent(e, worldPos);
            }
        });
    }

    /**
     * Forward a key event from the world view to the scenario in the debug VM.
     */
    private void forwardWorldKeyEvent(KeyEvent e)
    {
        if (controller != null)
        {
            controller.forwardWorldKeyEvent(e);
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showWorldContextMenu(List<MenuItem> items, Point2D worldPos)
    {
        hideContextMenu();
        contextMenu = new ContextMenu();
        contextMenu.setOnHidden(e -> {
            contextMenu = null;
        });
        contextMenu.getItems().addAll(items);
        Point2D screenLocation = worldDisplay.worldToScreen(worldPos);
        contextMenu.show(worldDisplay, screenLocation.getX(), screenLocation.getY());
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void hideWorldContextMenu()
    {
        hideContextMenu();
    }

    /**
     * Hide the context menu if one is currently showing on the world.
     */
    private void hideContextMenu()
    {
        if (contextMenu != null)
        {
            // The onHidden handler sets the contextMenu field back to null:
            contextMenu.hide();
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Point2D worldToScreen(Point2D worldPos)
    {
        return worldDisplay.worldToScreen(worldPos);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public boolean isPlacingActor()
    {
        return newActorProperty.get() != null;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void beginPlacingActor(DebuggerObject actor, InvokerRecord ir, JavaType[] paramTypes, Reflective type)
    {
        ImageView imageView = getImageViewForClass(type);
        if (imageView != null)
        {
            newActorProperty.set(new NewActor(imageView, actor, ir, paramTypes));
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showActorHighlight(int x, int y, int width, int height, int rotation)
    {
        worldDisplay.setActorHighlight(x, y, width, height, rotation);
    }

    /**
     * Get an ImageView with the appropriate preview image for this class type.
     * If no suitable image found, the Greenfoot icon will be used.
     */
    private ImageView getImageViewForClass(Reflective typeReflective)
    {
        File file = getImageFilename(typeReflective);
        // If no image, use the default:
        if (file == null)
        {
            file = new File(getGreenfootLogoPath());
        }

        ImageView imageView = null;
        try
        {
            imageView = new ImageView(file.toURI().toURL().toExternalForm());
        }
        catch (MalformedURLException e)
        {
            Debug.reportError(e);
        }
        return imageView;
    }

    private static String getGreenfootLogoPath()
    {
        File libDir = Config.getGreenfootLibDir();
        return libDir.getAbsolutePath() + "/imagelib/other/greenfoot.png";
    }

    /**
     * Returns a file name for the image of the first class
     * in the given class' class hierarchy that has an image set.
     */
    private File getImageFilename(Reflective type)
    {
        String imageFileName = classDiagram.getImageForActorClass(type);
        if (imageFileName == null)
        {
            return null;
        }
        
        File imageDir = new File(project.getProjectDir(), "images");
        return new File(imageDir, imageFileName);
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
    public void worldStatusChanged()
    {
        updateBackgroundMessage();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void setWorldVisible(boolean visible)
    {
        worldVisible.set(visible);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void greyOutWorld()
    {
        worldDisplay.greyOutWorld();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void worldImageSizeChanged(double width, double height)
    {
        if (worldViewScroll.getWidth() < width || worldViewScroll.getHeight() < height)
        {
            // We don't call sizeToScene() directly while holding the file lock because it can
            // cause us to re-enter the animation timer (see commit comment).  So we set this
            // flag to true as a way of queueing up the request:
            JavaFXUtil.runAfterCurrent(() -> sizeToScene());
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showWorldImage(Image image)
    {
        worldDisplay.setImage(image);
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
    public void setWorldCursor(Cursor cursor)
    {
        worldDisplay.setCursor(cursor);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void clearActorHighlight()
    {
        worldDisplay.clearActorHighlight();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void showSpeed(int speed)
    {
        controlPanel.setSpeed(speed);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void classesChanged()
    {
        classDiagram.recalculateGroups();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void vmTerminated()
    {
        // We must reset the debug VM related state ready for the new debug VM:
        worldDisplay.setImage(null);
        worldDisplay.cancelAsk();
    }

    /**
     * Show a dialog to set the image for the given class target.  Will only be called
     * for classes which have Actor or World as an ancestor.
     *
     * @param classNode   The class node of the class to be assigned an image.
     */
    public void setImageFor(LocalGClassNode classNode)
    {
        // initialise our image library frame
        SelectImageFrame selectImageFrame = new SelectImageFrame(this, project, classNode);
        // if the frame is not canceled after showing, set the image of the class to the selected file
        selectImageFrame.showAndWait().ifPresent(selectedFile -> setImageToClassNode(classNode, selectedFile));
    }

    /**
     * Copies an image file to the local images folder, if it is not already there,
     * and then set it to a class node.
     *
     * @param classNode          The class node to be assigned the image. Can't be null
     * @param originalImageFile  The image's file. Can't be null
     */
    private void setImageToClassNode(LocalGClassNode classNode, File originalImageFile)
    {
        String imageFileName = controller.installClassImage(originalImageFile);
        classNode.setImageFilename(imageFileName);
        String qualifiedName = classNode.getQualifiedName();
        saveAndMirrorClassImageFilename(qualifiedName, imageFileName);
    }

    /**
     * Save image file name for the given class to the project file, and mirror to the debug VM.
     * @param qualifiedName The qualified name of the class
     * @param imageFileName The file name of the image to use for that class.
     */
    public void saveAndMirrorClassImageFilename(String qualifiedName, String imageFileName)
    {
        controller.saveAndMirrorClassImageFilename(qualifiedName, imageFileName);
    }

    /**
     * Show a dialog for a new name, and then duplicate the class target with that new name.
     */
    public void duplicateClass(LocalGClassNode originalNode, ClassTarget originalClassTarget)
    {
        String originalClassName = originalClassTarget.getDisplayName();
        SourceType sourceType = originalClassTarget.getSourceType();
        NewClassDialog dialog = new NewClassDialog(this, sourceType);
        dialog.setSuggestedClassName("CopyOf" + originalClassName);
        dialog.disableLanguageBox(true);
        dialog.setNameProblem(controller::classNameProblem);

        dialog.showAndWait().ifPresent(newClassInfo ->
        {
            ClassTarget newClass = controller.duplicateClassFile(originalClassTarget, newClassInfo.className);
            if (newClass != null)
            {
                LocalGClassNode newNode = classDiagram.addClass(newClass);
                String originalImage = originalNode.getImageFilename();
                if (originalImage != null)
                {
                    File imagesDir = new File(project.getProjectDir(), "images");
                    File srcImage = new File(imagesDir, originalImage);
                    setImageToClassNode(newNode, srcImage);
                }

                // The class needs to be compiled for the state of the scenario to be correct.
                controller.compileAddedClass(newClass);
            }
        });
    }

    /**
     * Import a class using the import class dialog
     */
    public void doImportClass()
    {
        File srcFile = new ImportClassDialog(this).showAndWait().orElse(null);

        if (srcFile != null)
        {
            String className = GreenfootUtil.removeExtension(srcFile.getName());
            final Package pkg = project.getUnnamedPackage();

            // Check if a class of the same name already exists in the project.
            // Renaming would be too tricky, so just issue error and stop in that case:
            for (ClassTarget preexist : pkg.getClassTargets())
            {
                if (preexist.getQualifiedName().equals(className))
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

            // Copy the java/class file and any libraries across, and reload the package:
            GreenfootProjectController.ImportedClass imported = controller.importClassFiles(srcFile, className);
            ClassTarget gclass = imported.classTarget;

            if (gclass == null)
            {
                return;
            }

            // Finally, update the class browser:
            LocalGClassNode gclassNode = classDiagram.addClass(gclass);

            // Copy the image across and set it as the class image:
            if (srcImage != null && destImage != null && !destImage.exists())
            {
                GreenfootUtil.copyFile(srcImage, destImage);
                setImageToClassNode(gclassNode, destImage);
            }

            // The class needs to be compiled for the state of the scenario to be correct.
            controller.compileAddedClass(gclass);

            if (imported.librariesImported)
            {
                // Must restart debug VM to load the imported library:
                controller.restartVM();
            }
        }
    }

    /**
     * Prompt the user by the NewClassDialog to create a new non-image class.
     *
     * @param pkg            The package that should contain the new class.
     * @param superClassName The super class's full qualified name.
     */
    public void newNonImageClass(Package pkg, String superClassName)
    {
        NewClassDialog dlg = new NewClassDialog(this, project.getUnnamedPackage().getDefaultSourceType());
        dlg.setNameProblem(controller::classNameProblem);
        dlg.showAndWait().ifPresent(result -> {
            createNewClass(pkg, superClassName, result.className, result.sourceType,
                    getNormalTemplateFileName(result.sourceType));
        });
    }

    /**
     * Create a new non-image class in the specified package.
     *
     * @param pkg            The package that should contain the new class.
     * @param superClassName The full qualified name of the super class.
     * @param className      The class's name, which will be created.
     * @param language       The source type of the class, e.g. Java or Stride.
     * @param templateFileName  The name of the template file to use
     *
     * @return A class info reference for the class created.
     */
    private LocalGClassNode createNewClass(Package pkg, String superClassName, String className, SourceType language,
            String templateFileName)
    {
        ClassTarget newClass = controller.createClassFile(pkg, superClassName, className, language, templateFileName);
        return newClass == null ? null : classDiagram.addClass(newClass);
    }

    private static String getNormalTemplateFileName(SourceType language)
    {
        return "std" + language + ".tmpl";
    }

    public static String getActorTemplateFileName(SourceType language)
    {
        return "actor" + language + ".tmpl";
    }

    /**
     * SuperGreenfoot: the template for a direct subclass of greenfoot.SuperWindow.
     */
    public static String getSuperWindowTemplateFileName(SourceType language)
    {
        return "superwindow" + language + ".tmpl";
    }

    public static String getWorldTemplateFileName(boolean makeDirectSubclassOfWorld, SourceType language)
    {
        if (!makeDirectSubclassOfWorld)
        {
            return "subworld" + language + ".tmpl";
        }
        else
        {
            return "world" + language + ".tmpl";
        }
    }

    /**
     * Show a dialog to ask for details, then make a new subclass of the given class
     * using those details.
     * 
     * @param parentName  the fully-qualified name of the parent class
     * @param classType  the type of the parent class
     */
    public void newSubClassOf(String parentName, GClassType classType)
    {
        if (classType == GClassType.WORLD || classType == GClassType.ACTOR)
        {
            NewImageClassFrame frame = new NewImageClassFrame(this, project);
            // if the frame is not canceled after showing, create the new class
            // and set its image to the selected file
            frame.showAndWait().ifPresent(classInfo ->
            {
                SourceType sourceType = classInfo.sourceType;
                String extendsName = parentName;
                if (extendsName.startsWith("greenfoot."))
                {
                    extendsName = extendsName.substring("greenfoot.".length());
                }
                LocalGClassNode newClass = createNewClass(project.getUnnamedPackage(), extendsName, classInfo.className,
                        sourceType, getTemplateFileName(classType, parentName, sourceType));

                // set the image of the class to the selected file, if there is one selected.
                File imageFile = classInfo.imageFile;
                if (imageFile != null)
                {
                    setImageToClassNode(newClass, imageFile);
                }
            });
        }
        else
        {
            ClassTarget classTarget = classDiagram.getSelectedClassTarget();
            Package pkg = classTarget.getPackage();
            newNonImageClass(pkg, parentName);
        }
    }

    /**
     * Return the template file name which is built based on
     * the class type (i.e. world or actor), the parent and the source type.
     * Other class type is not allowed and will throw an IllegalArgumentException.
     *
     * @param classType   World or Actor. If Other is passed, an IllegalArgumentException will be fired.
     * @param parentName  The direct parent's name.
     * @param sourceType  Java or Stride.
     * @return The suitable template file name.
     * @throws IllegalStateException Only if the class type is not World or Actor.
     */
    private String getTemplateFileName(GClassType classType, String parentName, SourceType sourceType)
    {
        if (classType == GClassType.WORLD)
        {
            return getWorldTemplateFileName("greenfoot.World".equals(parentName), sourceType);
        }
        else if (classType == GClassType.ACTOR)
        {
            // SuperGreenfoot: a window has no no-argument constructor to inherit.
            return "greenfoot.SuperWindow".equals(parentName) ? getSuperWindowTemplateFileName(sourceType)
                    : getActorTemplateFileName(sourceType);
        }
        throw new IllegalArgumentException("This method should be called only on World or Actor classes.");
    }

    /**
     * Opens a browser tab (in an editor window) for the given fully qualified class name
     * of a built-in Greenfoot class (e.g. greenfoot.Actor)
     */
    public void openGreenfootDocTab(String qualifiedClassName)
    {
        if (controller != null)
        {
            controller.openGreenfootDocTab(qualifiedClassName);
        }
    }

    /**
     * Called when a class has been modified, to notify us that the .class
     * files are now out of date.
     */
    public void classModified()
    {
        if (controller != null)
        {
            controller.classModified();
        }
    }

    /**
     * Shows About-Greenfoot dialog including information about the development team and translators.
     * 
     * @param parentWindow   the parent window; may be null.
     */
    public static void aboutGreenfoot(Window parentWindow)
    {
        // Use the dedicated Super Greenfoot About artwork, separate from the startup splash.
        URL resource = Boot.class.getResource("supergreenfoot-about.png");
        Image image = new javafx.scene.image.Image(resource.toString());

        String[] translatorNames = {
                "Arabic",     "Hakim Hadjaissa",
                "Brazilian",  "Herodoto Bento-DeMello",
                "Chinese",    "Wobot Yuan and He Qing",
                "Czech",      "Zdeněk Chalupský",
                "Dutch",      "Erik van Veen and Renske Smetsers-Weeda",
                "French",     "Guillaume Baudoin, Pierre Lefebvre and Denis Bureau",
                "German",     "Matthias Taulien, Martin Schleyer, Stefan Mueller and Michael Kölling",
                "Greek",      "Elevtherios Chrysochoidis and Dragon Turtle",
                "Indian",     "Kiran Kumar D",
                "Italian",    "Stefano Federici",
                "Korean",     "John Kim",
                "Malayalam",  "K.R.Arun",
                "Polish",     "Przemysław Adam Śmiejek",
                "Portuguese", "Paulo Abadie and Fabio Hedayioglu",
                "Russian",    "Sergey Zemlyannikov",
                "Spanish",    "José Lerma, Esteban Manriquez, Kenneth Daly, Iris Gutierrez and Luis Mijangos",
                "Swedish",    "Daniel Norrman"
        };

        String[] previousTeamMembers = {
                "Amjad Altadmri",
                "Michael Berry",
                "Hamza Hamza",
                "Fabio Hedayioglu",
                "Poul Henriksen",
                "Charalampos Kyfonidis",
                "Davin McCall",
                "Philip Stevens",
                "Ian Utting",
                "Marion Zalk",
        };

        new AboutDialogTemplate(parentWindow,
                Boot.SUPER_VERSION + " (based on Greenfoot " + Boot.GREENFOOT_VERSION + ")",
                "https://github.com/MrCohen/SuperGreenfoot",
                image, translatorNames, previousTeamMembers).showAndWait();
    }

    /**
     * This window, as a Stage.
     */
    public Stage getStage()
    {
        return this;
    }
    
    /**
     * Allow the user to select a directory into which we create a project.
     */
    public void doNewProject(SourceType sourceType)
    {
        String title = Config.getString("greenfoot.utilDelegate.newScenario") + " - " + sourceType;
        File newnameFile = FileUtility.getSaveProjectFX(project, this.getStage(), title);
        if (newnameFile == null)
        {
            return;
        }
        if (! newProject(newnameFile.getAbsolutePath(),sourceType))
        {
            DialogManager.showErrorWithTextFX(null, "cannot-create-directory", newnameFile.getPath());
        }
    }

    /**
     * Create new Greenfoot project with a sub class of World.
     *
     * @param dirName The directory to create the project in.
     * @param sourceType The source type of the project which is either Stride or Java.
     * @return     true if successful, false otherwise
     */
    public boolean newProject(String dirName, SourceType sourceType)
    {
        if (Project.createNewProject(dirName))
        {
            Project proj = Project.openProject(dirName);

            if (proj != null)
            {
                Package unNamedPkg = proj.getUnnamedPackage();
                Properties props = new Properties(unNamedPkg.getLastSavedProperties());
                props.put("version", Boot.GREENFOOT_API_VERSION);
                unNamedPkg.save(props);
                ProjectManager.instance().launchProject(proj);
                IdeWindow window = ProjectRegistry.findWindowForProject(proj);
                if (window instanceof GreenfootStage)
                {
                    GreenfootStage stage = (GreenfootStage) window;
                    LocalGClassNode worldClass = stage.createNewClass(unNamedPkg, "World",
                            "MyWorld", sourceType, getWorldTemplateFileName(true, sourceType));
                    stage.controller.setCurrentWorld(worldClass.getClassTarget());
                }
                else if (window != null && window.getController() != null)
                {
                    // Another IDE's window (the project opened in the current IDE):
                    ClassTarget worldClass = window.getController().createClassFile(unNamedPkg, "World",
                            "MyWorld", sourceType, getWorldTemplateFileName(true, sourceType));
                    if (worldClass != null)
                    {
                        window.getController().setCurrentWorld(worldClass);
                    }
                    window.classesChanged();
                }
                if (window != null)
                {
                    window.getWindow().toFront();
                }
                return true;
            }
            else
            {
                // display error dialog
                DialogManager.showErrorFX(this, "could-not-open-project");
                return false;
            }
        }
        
        DialogManager.showErrorFX(this, "cannot-create-project");
        return false;
    }

    /**
     * Show the readme file for this project in an editor window.
     */
    public void openReadme()
    {
        if (controller != null)
        {
            controller.openReadme();
        }
    }

    /**
     * When the speed slider is moved, this method is called
     * to communicate the new value to the debug VM.
     * @param newSpeed The new speed, from the slider.
     */
    public void setSpeedFromSlider(int newSpeed)
    {
        if (controller != null)
        {
            controller.setSpeedFromSlider(newSpeed);
        }
    }

    /**
     * Checks if the class to be deleted is the current world class, then it sets
     * currentWorld field to null. This avoids generating exception when Greenfoot
     * later tries to use the currentWorld field in GreenfootStage.
     * @param classTarget The class to be deleted.
     */
    public void fireWorldRemovedCheck(ClassTarget classTarget)
    {
        if (controller != null)
        {
            controller.fireWorldRemovedCheck(classTarget);
        }
    }

    /**
     * The project shown in this window, or null if the window is empty.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public Project getProject()
    {
        return project;
    }
}
