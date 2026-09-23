/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2017,2018,2019,2019,2020,2021,2022,2023,2024  Poul Henriksen and Michael Kolling
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
package greenfoot.guifx.controller;

import bluej.pkgmgr.ProjectLayoutStore;
import bluej.Boot;
import bluej.Config;
import bluej.collect.DataCollector;
import bluej.collect.GreenfootInterfaceEvent;
import bluej.compiler.CompileInputFile;
import bluej.compiler.CompileReason;
import bluej.compiler.CompileType;
import bluej.compiler.Diagnostic;
import bluej.compiler.FXCompileObserver;
import bluej.debugger.Debugger;
import bluej.debugger.DebuggerObject;
import bluej.debugger.DebuggerResult;
import bluej.debugger.ExceptionDescription;
import bluej.debugger.gentype.GenTypeClass;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.Reflective;
import bluej.debugmgr.Invoker;
import bluej.debugmgr.NamedValue;
import bluej.debugmgr.ResultWatcher;
import bluej.debugmgr.objectbench.InvokeListener;
import bluej.debugmgr.objectbench.ObjectWrapper;
import bluej.debugmgr.objectbench.ResultWatcherBase;
import bluej.editor.Editor;
import bluej.extensions2.SourceType;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PackageUI;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.ProjectUtils;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.ReadmeTarget;
import bluej.pkgmgr.target.Target;
import bluej.prefmgr.PrefMgr;
import bluej.testmgr.record.InvokerRecord;
import bluej.testmgr.record.ObjectInspectInvokerRecord;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.FileUtility;
import bluej.utility.JavaReflective;
import bluej.utility.Utility;
import bluej.utility.javafx.FXPlatformFunction;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import bluej.views.CallableView;
import bluej.views.ConstructorView;
import bluej.views.MethodView;
import greenfoot.Actor;
import greenfoot.core.ProjectManager;
import greenfoot.export.ScenarioSaver;
import greenfoot.export.mygame.ScenarioInfo;
import greenfoot.guifx.ControlPanel.ControlPanelListener;
import greenfoot.guifx.FullScreenView;
import greenfoot.record.GreenfootRecorder;
import greenfoot.util.GreenfootUtil;
import greenfoot.vmcomm.DisplayState;
import greenfoot.vmcomm.GreenfootDebugHandler;
import greenfoot.vmcomm.GreenfootDebugHandler.SimulationStateListener;
import greenfoot.vmcomm.VMCommsMain;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Queue;

import static bluej.pkgmgr.target.EditableTarget.MENU_STYLE_INBUILT;
import static greenfoot.vmcomm.Command.*;

/**
 * Everything about one open project that is not part of the window showing it:
 * the link to the debug VM, the simulation state (no world, paused, running and
 * so on) and what drives it (act, run, pause, reset, speed, compiling), the
 * latest world image and whether it is greyed out, any Greenfoot.ask prompt,
 * whether user code has been running long enough to show the execution twirler,
 * keyboard and mouse input to the world, picking and dragging actors and their
 * context menus, interactive method and constructor calls (it is the package's
 * {@link PackageUI}), the full-screen play window and the display state the
 * scenario sees, saving the project, creating, duplicating and importing class
 * files and their images, the save-the-world recorder, the scenario details, and
 * the work waiting for the debug VM to be ready.
 *
 * <p>One controller exists per open project, for as long as the project is open.
 * A window ({@link ProjectView}) attaches to it to show the project, and can be
 * replaced by another window while the project stays open (the new window is
 * brought up to date: see {@link #attachView}).  The Classic Greenfoot IDE window
 * and the new SuperGreenfoot IDE window are both such views.
 */
@OnThread(Tag.FXPlatform)
public class GreenfootProjectController implements VMCommsMain.CommsListener,
        SimulationStateListener, FXCompileObserver, PackageUI, ControlPanelListener, ScenarioSaver
{
    private final Project project;
    private final GreenfootDebugHandler debugHandler;
    private final GreenfootRecorder saveTheWorldRecorder;

    // The scenario information that usually shipped with it when uploading
    // to the gallery. We should maintain a reference to it and make sure
    // that its properties are written to the project properties file always.
    // This change is needed as we now wipe the properties file each time
    // before saving to avoid stacking unneeded properties hanging forever.
    private final ScenarioInfo scenarioInfo;
    // The image chosen for each class (saved in the project file):
    private final ClassImages classImages;

    // The window showing the project:
    private ProjectView view;
    // Set once start() has run (the project is live and being shown):
    private boolean started = false;
    // Set once the window has stopped showing the project; late callbacks are then ignored:
    private boolean disposed = false;

    // Polls the shared memory for input from the debug VM:
    private AnimationTimer vmCommsHandler;

    // Tasks to add to executeAfterReady after the VM has been terminated:
    private final Queue<FXPlatformRunnable> executeAfterTermination = new LinkedList<>();
    // Tasks to be run once the VM has initialised:
    private final Queue<FXPlatformRunnable> executeAfterReady = new LinkedList<>();

    private final ObjectProperty<SimulationState> stateProperty = new SimpleObjectProperty<>(SimulationState.NO_PROJECT);
    private boolean atBreakpoint = false;
    private boolean simulationRunning = false;
    private boolean waitingForDiscard = false;
    private boolean constructingWorld = false;
    private boolean worldInstantiationError = false;

    // The current active world. This will be set either by properties when opening
    // a scenario, or by calling a world constructor through the context menu.
    // This will NOT change if the world changes by user's code.
    private ClassTarget currentWorld;

    // The last speed value set by the user altering it in interface (rather than programmatically):
    private int lastUserSetSpeed;
    // Used to stop an infinite loop if we set the speed slider in response to a programmatic change:
    private boolean settingSpeedFromSimulation = false;
    // The speed last shown on the speed slider (by the user or the simulation):
    private int shownSpeed = 50;

    // The world image, double-buffered (the one not showing is written next):
    private final WritableImage[] worldImg = new WritableImage[2];
    private int nextWorldImgToWrite = 0;
    // The most recently received world image (null if none since the debug VM started):
    private Image lastWorldImage;
    // Whether the world is showing (if not, the window shows a message in its place):
    private boolean worldVisible = false;
    // Whether the world is greyed out (out of date, or behind an ask prompt).  This mirrors
    // the window's display: set by greying out and by asking, cleared by a new image and
    // by the end of an ask:
    private boolean greyedOut = false;
    // The Greenfoot.ask prompt showing, if asking:
    private boolean asking = false;
    private int currentAskId;
    private String currentAskPrompt;
    // When did the user code last start executing (zero if it is not executing)?
    private long lastExecStartTime;
    // Whether the execution twirler is showing:
    private boolean twirling = false;

    // Details for pick requests that we have sent to the debug VM:
    private static enum PickType
    {
        LEFT_CLICK, CONTEXT_MENU, DRAG;
    }

    // The next free pick ID that we will use
    private int nextPickId = 1;
    // The most recent pick ID that we are waiting on from the debug VM.
    private int curPickRequest;
    // The point at which the most recent pick happened.
    private Point2D curPickPoint;
    // If true, most recent pick was for right-click menu.  If false, was for a left-click drag.
    private PickType curPickType;
    // The current drag request ID, or -1 if not currently dragging:
    private int curDragRequest;
    private DebuggerObject draggedActor;

    // The number of interactive calls (made from the world or the class diagram) that are executing:
    private int invocationsRunning = 0;

    // The full-screen play window, or null when not in full screen.  It has no owner
    // window, so it is not affected by which window shows the project:
    private FullScreenView fullScreenView;
    // Full-screen bar preferences, kept while no full-screen window is open, so that
    // scenario code (Greenfoot.setScaleMode etc.) and the user's choices survive re-entry:
    private boolean fsPixelPerfect = false;
    private boolean fsControlsVisible = true;
    private boolean fsControlsLocked = false;
    private int lastAppliedDisplayRequest = 0;
    // The screen last reported to the debug VM:
    private Rectangle2D lastReportedScreen = null;

    private final ChangeListener<String> playerNameListener = new ChangeListener<String>()
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends String> observable, String oldValue,
                            String newValue)
        {
            sendPropertyToDebugVM("greenfoot.player.name", newValue);
        }
    };

    /**
     * Create the controller for a newly opened project.
     *
     * @param project        the project
     * @param debugHandler   the debug handler for the project
     */
    public GreenfootProjectController(Project project, GreenfootDebugHandler debugHandler)
    {
        this.project = project;
        this.debugHandler = debugHandler;
        this.saveTheWorldRecorder = debugHandler.getRecorder();
        this.scenarioInfo = new ScenarioInfo(project.getUnnamedPackage().getLastSavedProperties());
        this.classImages = new ClassImages(project.getUnnamedPackage().getLastSavedProperties());
        JavaFXUtil.addChangeListenerPlatform(stateProperty, s -> showState());
    }

    /**
     * Set the window that shows this project.  If the project is already being shown
     * (another window showed it until now), the new window is brought up to date:
     * the simulation state, speed, world image (and whether it is greyed out or
     * hidden), any ask prompt, the execution twirler and the message shown in place
     * of the world.  All of this happens in one FX turn, so nothing from the debug VM
     * can arrive between the old window letting go and the new one catching up.
     */
    public void attachView(ProjectView view)
    {
        this.view = view;
        if (started && !disposed)
        {
            view.stateChanged(stateProperty.get(), atBreakpoint);
            // Moving the new window's slider must not count as the user choosing a speed:
            settingSpeedFromSimulation = true;
            view.showSpeed(shownSpeed);
            settingSpeedFromSimulation = false;
            if (lastWorldImage != null)
            {
                view.worldImageSizeChanged(lastWorldImage.getWidth(), lastWorldImage.getHeight());
                view.showWorldImage(lastWorldImage);
            }
            if (greyedOut && !asking)
            {
                view.greyOutWorld();
            }
            view.setWorldVisible(worldVisible);
            if (asking)
            {
                int askId = currentAskId;
                view.showAsk(currentAskPrompt, answer -> answerAsk(askId, answer));
            }
            view.setExecutionTwirling(twirling);
            view.worldStatusChanged();
        }
    }

    /**
     * Whether start() has run: the project is live (a window has shown it).
     */
    public boolean isStarted()
    {
        return started;
    }

    /**
     * Start looking after the project: become the package's UI, observe its
     * compilation, listen to the simulation, start talking to the debug VM (the
     * attached view is told what to show), pass player-name changes on to it, send
     * it the project properties, and start creating the world that was showing
     * when the project was last saved.
     */
    public void start()
    {
        started = true;
        project.getPackage("").setUI(this);
        project.getUnnamedPackage().addCompileObserver(this);
        debugHandler.setPickListener(this::pickResults);
        debugHandler.setSimulationListener(this);

        vmCommsHandler = new AnimationTimer()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void handle(long now)
            {
                if (debugHandler.getVmComms().checkIO(GreenfootProjectController.this))
                {
                    // Shouldn't execute directly, because we're in an animation timer and shouldn't block:
                    executeAfterReady.forEach(JavaFXUtil::runAfterCurrent);
                    executeAfterReady.clear();
                }
            }
        };
        vmCommsHandler.start();

        loadAndMirrorProperties();
        Properties lastSavedProperties = project.getUnnamedPackage().getLastSavedProperties();
        String lastInstantiatedWorldName = lastSavedProperties.getProperty("world.lastInstantiated");
        currentWorld = lastInstantiatedWorldName != null
                ? (ClassTarget) project.getTarget(lastInstantiatedWorldName)
                : null;
        if (currentWorld != null && currentWorld.isCompiled()
                && hasNoArgConstructor(currentWorld.getTypeReflective()))
        {
            // We send a reset to make a new world after the project properties have been sent across:
            constructingWorld = true;
            project.getTerminal().activate(true);
            sendDisplayState();   // so the world constructor can already ask about the screen
            debugHandler.getVmComms().instantiateWorld(lastInstantiatedWorldName);
            saveTheWorldRecorder.recordingValid();
            view.worldStatusChanged();
        }

        PrefMgr.getPlayerName().addListener(playerNameListener);
    }

    /**
     * The window has finished setting itself up to show the project.
     */
    public void viewReady()
    {
        stateProperty.set(SimulationState.NO_WORLD);
    }

    /**
     * The project is being closed: stop passing player-name changes to it.
     */
    public void projectClosing()
    {
        PrefMgr.getPlayerName().removeListener(playerNameListener);
    }

    /**
     * The window no longer shows the project: stop talking to the debug VM,
     * and ignore any late news from it.
     */
    public void dispose()
    {
        disposed = true;
        if (vmCommsHandler != null)
        {
            vmCommsHandler.stop();
            vmCommsHandler = null;
        }
    }

    /**
     * Send an updated property value to the debug VM.
     * @param key    The property name
     * @param value  The property value
     */
    public void sendPropertyToDebugVM(String key, String value)
    {
        debugHandler.getVmComms().sendProperty(key, value);
    }

    /**
     * Send package properties to the other VM. (This allows the actor/world classes to determine their
     * image).
     */
    private void loadAndMirrorProperties()
    {
        Properties props = project.getUnnamedPackage().getLastSavedProperties();

        for (String key : props.stringPropertyNames())
        {
            String value = props.getProperty(key);
            sendPropertyToDebugVM(key, value);
        }

        // Add the player name property from the user properties.
        sendPropertyToDebugVM("greenfoot.player.name", PrefMgr.getPlayerName().get());

        // Load the speed into our slider and inform debug VM:
        lastUserSetSpeed = 50;
        try
        {
            String speedString = project.getUnnamedPackage().getLastSavedProperties().getProperty("simulation.speed");
            if (speedString != null)
            {
                lastUserSetSpeed = Integer.valueOf(speedString);
            }
        }
        catch (NumberFormatException e)
        {
            // Just leave it as the default 50 if there is a problem
        }
        showSpeed(lastUserSetSpeed, true);
        debugHandler.getVmComms().setSimulationSpeed(lastUserSetSpeed);
    }

    /**
     * Check whether a type has a no-argument constructor.
     */
    public static boolean hasNoArgConstructor(Reflective type)
    {
        return type.getDeclaredConstructors().stream().anyMatch(c -> c.getParamTypes().isEmpty()
                && !Modifier.isPrivate(c.getModifiers()));
    }

    // ---- Act, run, pause, reset and speed ----

    /**
     * Perform a single act step, if paused, by adding to the list of pending commands.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void act()
    {
        if (stateProperty.get() == SimulationState.PAUSED)
        {
            DataCollector.recordGreenfootEvent(project, GreenfootInterfaceEvent.WORLD_ACT);
            debugHandler.getVmComms().act();
            stateProperty.set(SimulationState.PAUSED_REQUESTED_ACT_OR_RUN);
            saveTheWorldRecorder.invalidateRecording();
        }
    }

    /**
     * Run or pause the simulation (depending on current state).
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void doRunPause()
    {
        if (stateProperty.get() == SimulationState.PAUSED)
        {
            DataCollector.recordGreenfootEvent(project, GreenfootInterfaceEvent.WORLD_RUN);
            debugHandler.getVmComms().runSimulation();
            stateProperty.set(SimulationState.PAUSED_REQUESTED_ACT_OR_RUN);
            saveTheWorldRecorder.invalidateRecording();
            view.requestWorldFocus();
        }
        else if (stateProperty.get() == SimulationState.RUNNING)
        {
            DataCollector.recordGreenfootEvent(project, GreenfootInterfaceEvent.WORLD_PAUSE);
            debugHandler.getVmComms().pauseSimulation();
            stateProperty.set(SimulationState.RUNNING_REQUESTED_PAUSE);
        }
    }

    /**
     * The user pressed Reset.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void userReset()
    {
        DataCollector.recordGreenfootEvent(project, GreenfootInterfaceEvent.WORLD_RESET);
        doReset();
    }

    /**
     * Perform a reset. This discards the world, and instantiates a new one (if possible).
     * If the simulation thread has been halted via the debugger, it is resumed.
     */
    public void doReset()
    {
        //we pause before reset to prevent waiting too long in a delay between act frames
        debugHandler.getVmComms().pauseSimulation();

        if (currentWorld != null && currentWorld.isCompiled()
                && hasNoArgConstructor(currentWorld.getTypeReflective()))
        {
            // Must store this first, because if they have to terminate the VM it won't be available after:
            ClassTarget curWorld = currentWorld;

            suggestTerminateIfAskingThenRun(() -> {
                doWorldDiscard();
                constructingWorld = true;
                project.getTerminal().activate(true);
                sendDisplayState();
                debugHandler.getVmComms().instantiateWorld(curWorld.getQualifiedName());
                // currentWorld will have been set to null when the VM terminated,
                // so we must set it back again:
                currentWorld = curWorld;
            });
        }
        else if (constructingWorld && asking)
        {
            // Could be that we haven't got a world yet, but there is one being constructed
            // and waiting for an ask response: we should offer to terminate, but then
            // we deliberately won't make a new world (they can do it if they want it):
            suggestTerminateIfAskingThenRun(() -> {
                doWorldDiscard();
            });
        }
        else
        {
            // Not a reset so much as a discard world:
            doWorldDiscard();
        }
    }

    /**
     * Helper method used by doReset() to discard the world
     */
    private void doWorldDiscard()
    {
        discardWorld();
        debugHandler.simulationThreadResumeOnResetClick();
        saveTheWorldRecorder.recordingValid();
        view.clearActorHighlight();
    }

    /**
     * Discard the current world, if any, by issuing a command to the remote VM.
     */
    private void discardWorld()
    {
        if (stateProperty.get() != SimulationState.NO_WORLD && stateProperty.get() != SimulationState.NO_PROJECT)
        {
            debugHandler.getVmComms().discardWorld();
            stateProperty.set(SimulationState.NO_WORLD);
            waitingForDiscard = true;
        }
    }

    /**
     * Checks if there is currently a Greenfoot.ask prompt showing and then:
     *  - If there is an ask prompt showing, asks the user if they want to terminate VM or cancel, and then:
     *     - If they terminate, add the given runnable to the queue to be run after the VM restarts.
     *     - If they cancel, do nothing.
     *  - If there is not an ask prompt showing, run the given runnable as soon as the VM is ready.
     *
     *  In neither case will it actually run the runnable now, so do not assume it has been run!
     */
    public void suggestTerminateIfAskingThenRun(FXPlatformRunnable runAfterward)
    {
        if (asking)
        {
            if (0 == DialogManager.askQuestionFX(view.getWindow(), "terminate-for-reset"))
            {
                // Agreed to terminate:
                executeAfterTermination.add(runAfterward);
                project.restartVM();
            }
        }
        else
        {
            executeAfterReady.add(runAfterward);
        }
    }

    /**
     * When the speed slider is moved, this method is called
     * to communicate the new value to the debug VM.
     * @param newSpeed The new speed, from the slider.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void setSpeedFromSlider(int newSpeed)
    {
        if (!settingSpeedFromSimulation)
        {
            lastUserSetSpeed = newSpeed;
            debugHandler.getVmComms().setSimulationSpeed(newSpeed);
            // Keep both speed sliders (main window and full-screen bar) in step:
            showSpeed(newSpeed, true);
        }
    }

    /**
     * Called with the latest simulation speed
     * @param simSpeed The simulation speed we received from the debug VM:
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void notifySimulationSpeed(int simSpeed)
    {
        // We want to update the speed slider, but we don't want to alter
        // the speed in lastUserSetSpeed which will get saved, and we don't want to
        // tell the simulation about a speed change that they instigated.
        // So we set a boolean flag to block the slider listener:
        settingSpeedFromSimulation = true;
        showSpeed(simSpeed, false);
        settingSpeedFromSimulation = false;
    }

    /**
     * The window showing the project has gained focus.
     */
    public void windowActivated()
    {
        DataCollector.recordGreenfootEvent(project, GreenfootInterfaceEvent.WINDOW_ACTIVATED);
        // If any classes are uncompiled, compile-all.  If all compiled, may need a reset:
        if (project.getUnnamedPackage().getClassTargets().stream()
                .anyMatch(ct -> !ct.isCompiled()))
        {
            project.scheduleCompilation(true, CompileReason.USER,
                CompileType.INDIRECT_USER_COMPILE, project.getUnnamedPackage());
        }
        else if (greyedOut && !asking)
        {
            doReset();
        }
    }

    /**
     * Called when a class has been modified, to notify us that the .class
     * files are now out of date.
     */
    public void classModified()
    {
        discardWorld();
    }

    /**
     * Checks if the class to be deleted is the current world class, then it sets
     * currentWorld field to null. This avoids generating exception when Greenfoot
     * later tries to use the currentWorld field.
     * @param classTarget The class to be deleted.
     */
    public void fireWorldRemovedCheck(ClassTarget classTarget)
    {
        if (classTarget.equals(currentWorld))
        {
            currentWorld = null;
            setWorldVisible(false);
            doReset();
        }
        else
        {
            // In case this was last world class, update background message:
            view.worldStatusChanged();
        }
    }

    // ---- News from the debug VM about the world ----

    /**
     * When processing messages from the remote VM, we discovered the world has changed.
     *
     * @param worldPresent True if a world is present after the change.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void worldChanged(boolean worldPresent)
    {
        // We assume that a world change after issuing a discard is in
        // response to the discard.  If the world is no longer there, the
        // discard finished in isolation.  If a new world is there, it might
        // already be recreated after a discard as part of a reset, but the
        // initial discard must still have succeeded:
        waitingForDiscard = false;
        constructingWorld = false;
        project.getTerminal().activate(false);
        if (!worldPresent)
        {
            greyOutWorld();
            stateProperty.set(SimulationState.NO_WORLD);
        }
    }

    /**
     * A world image has been shown: if we were waiting for a world, it is here.
     */
    private void worldImageShown()
    {
        if (stateProperty.get() == SimulationState.NO_WORLD && ! waitingForDiscard)
        {
            stateProperty.set(simulationRunning ? SimulationState.RUNNING : SimulationState.PAUSED);
        }
    }

    // ---- Compiling (FXCompileObserver) ----

    @Override
    @OnThread(Tag.FXPlatform)
    public void startCompile(CompileInputFile[] sources, CompileReason reason, CompileType type, int compilationSequence)
    {
        if (disposed)
        {
            return;
        }
        // Grey out the world display until compilation finishes:
        discardWorld();
        greyOutWorld();
        view.worldStatusChanged();
        view.compileStarted();
        List<File> files = new ArrayList<>();
        for (CompileInputFile source : sources)
        {
            files.add(source.getUserSourceFile());
        }
        view.compilingFiles(files);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public boolean compilerMessage(Diagnostic diagnostic, CompileType type)
    {
        if (!disposed)
        {
            view.compilerMessage(diagnostic, type);
        }
        // Not shown to the user by us (the editor shows it):
        return false;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void endCompile(CompileInputFile[] sources, boolean succesful, CompileType type, int compilationSequence)
    {
        // If disposed, this is the end of a compile left-over from a project
        // that has just been closed; ignore it:
        if (disposed)
        {
            return;
        }

        // Do a Garbage Collection to finalize any garbage JdiObjects, thereby
        // allowing objects on the remote VM to be garbage collected.
        System.gc();

        // We only create the world if the window is focused, otherwise
        // we let it remain greyed out:
        if (view.isWindowFocused())
        {
            doReset();
        }
        view.worldStatusChanged();
        view.classesChanged();
        view.compileFinished(succesful);
    }

    // ---- The simulation thread (SimulationStateListener) ----

    @Override
    @OnThread(Tag.Any)
    public void simulationStartedRunning()
    {
        Platform.runLater(() -> {
            if (disposed)
            {
                return;
            }
            simulationRunning = true;
            // If the world constructor calls Greenfoot.start() we can see the simulation start
            // before we know whether world creation is successful. Therefore, set the state now
            // only if we have received the world already:
            if (stateProperty.get() != SimulationState.NO_WORLD)
            {
                stateProperty.set(SimulationState.RUNNING);
            }
            project.getTerminal().activate(true);
        });
    }

    @Override
    @OnThread(Tag.Any)
    public void simulationPaused()
    {
        Platform.runLater(() -> {
            if (disposed)
            {
                return;
            }
            simulationRunning = false;
            // We can see this message when the world has been removed,
            // in which case we want to ignore it:
            if (stateProperty.get() != SimulationState.NO_WORLD)
            {
                stateProperty.set(SimulationState.PAUSED);
            }
            project.getTerminal().activate(false);
        });
    }

    @Override
    @OnThread(Tag.Any)
    public void simulationDebugHalted()
    {
        Platform.runLater(() -> {
            if (disposed)
            {
                return;
            }
            atBreakpoint = true;
            showState();
        });
    }

    @Override
    @OnThread(Tag.Any)
    public void simulationDebugResumed()
    {
        Platform.runLater(() -> {
            if (disposed)
            {
                return;
            }
            atBreakpoint = false;
            showState();
        });
    }

    @Override
    @OnThread(Tag.Any)
    public void worldInstantiationError()
    {
        Platform.runLater(() -> {
            if (disposed)
            {
                return;
            }
            worldInstantiationError = true;
            constructingWorld = false;
            project.getTerminal().activate(false);
            // This will update the background message:
            setWorldVisible(false);
        });
    }

    @Override
    @OnThread(Tag.Any)
    public void simulationVMTerminated()
    {
        Platform.runLater(() -> {
            if (disposed)
            {
                return;
            }
            // We must reset the debug VM related state ready for the new debug VM:
            exitFullScreenView();
            view.vmTerminated();
            nextPickId = 1;
            curPickRequest = 0;
            curDragRequest = -1;
            invocationsRunning = 0;
            lastWorldImage = null;
            asking = false;
            greyedOut = false;
            currentAskPrompt = null;
            worldInstantiationError = false;
            settingSpeedFromSimulation = false;
            constructingWorld = false;
            setLastUserExecutionStartTime(0L, false);
            atBreakpoint = false;
            currentWorld = null;
            setWorldVisible(false);
            stateProperty.set(SimulationState.NO_WORLD);
            // This will set up pendingCommands, ready for when
            // the new debug VM can process data:
            loadAndMirrorProperties();

            executeAfterReady.addAll(executeAfterTermination);
            executeAfterTermination.clear();
        });
    }

    // ---- State the window reads ----

    public SimulationState getState()
    {
        return stateProperty.get();
    }

    public boolean isAtBreakpoint()
    {
        return atBreakpoint;
    }

    public boolean isConstructingWorld()
    {
        return constructingWorld;
    }

    /**
     * Note that a world is (or is no longer) being constructed by an interactive
     * constructor call.
     */
    public void setConstructingWorld(boolean constructingWorld)
    {
        this.constructingWorld = constructingWorld;
    }

    public boolean hasWorldInstantiationError()
    {
        return worldInstantiationError;
    }

    public ClassTarget getCurrentWorld()
    {
        return currentWorld;
    }

    public void setCurrentWorld(ClassTarget currentWorld)
    {
        this.currentWorld = currentWorld;
    }

    public int getLastUserSetSpeed()
    {
        return lastUserSetSpeed;
    }

    public Project getProject()
    {
        return project;
    }

    public GreenfootDebugHandler getDebugHandler()
    {
        return debugHandler;
    }

    public GreenfootRecorder getRecorder()
    {
        return saveTheWorldRecorder;
    }

    public ScenarioInfo getScenarioInfo()
    {
        return scenarioInfo;
    }

    /**
     * The image chosen for each class in the project.
     */
    public ClassImages getClassImages()
    {
        return classImages;
    }

    /**
     * The most recently received world image, or null if there has been none since the
     * debug VM started.
     */
    public Image getLastWorldImage()
    {
        return lastWorldImage;
    }

    /**
     * Whether a Greenfoot.ask prompt is showing.
     */
    public boolean isAsking()
    {
        return asking;
    }

    /**
     * Whether the world is greyed out (out of date, or behind an ask prompt).
     */
    public boolean isWorldGreyedOut()
    {
        return greyedOut;
    }

    /**
     * Whether the world is showing (if not, a message is shown in its place).
     */
    public boolean isWorldVisible()
    {
        return worldVisible;
    }

    // ---- The world display: image, grey-out, visibility ----

    /**
     * Grey out the world until it is up to date again.
     */
    private void greyOutWorld()
    {
        greyedOut = true;
        view.greyOutWorld();
    }

    /**
     * Show or hide the world (when hidden, the window shows a message in its place).
     */
    private void setWorldVisible(boolean visible)
    {
        worldVisible = visible;
        view.setWorldVisible(visible);
    }

    /**
     * A world image has been received from the remote VM.
     *
     * @param width   The image width
     * @param height  The image height
     * @param buffer  The buffer containing the pixel data (only valid during this call)
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void receivedWorldImage(int width, int height, IntBuffer buffer)
    {
        // If we are closing a project but receive an image late on, ignore it:
        if (disposed)
        {
            return;
        }

        if (worldImg[nextWorldImgToWrite] == null || worldImg[nextWorldImgToWrite].getWidth() != width || worldImg[nextWorldImgToWrite].getHeight() != height)
        {
            worldImg[nextWorldImgToWrite] = new WritableImage(width == 0 ? 1 : width, height == 0 ? 1 : height);
            view.worldImageSizeChanged(worldImg[nextWorldImgToWrite].getWidth(), worldImg[nextWorldImgToWrite].getHeight());
        }
        try
        {
            worldImg[nextWorldImgToWrite].getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(),
                    buffer, width);
            lastWorldImage = worldImg[nextWorldImgToWrite];
            // Showing a new image turns off any greying effect:
            greyedOut = false;
            view.showWorldImage(lastWorldImage);
            if (fullScreenView != null)
            {
                fullScreenView.setImage(lastWorldImage);
            }
            nextWorldImgToWrite = (nextWorldImgToWrite + 1) % worldImg.length;
            worldInstantiationError = false;
            setWorldVisible(true);
        }
        catch (IndexOutOfBoundsException ex)
        {
            Debug.reportError("Error receiving world (world image probably too large)");
            worldInstantiationError = true;
            setWorldVisible(false);
        }

        worldImageShown();
    }

    // ---- Greenfoot.ask ----

    /**
     * An "ask" request has been received from the remote VM (this is repeated while the
     * request is pending).
     *
     * @param askId The identification number of the ask request
     * @param promptCodepoints   the codepoints making up the prompt string.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void receivedAsk(int askId, int[] promptCodepoints)
    {
        // The ask pane lives in the main window, so leave full screen first:
        exitFullScreenView();
        String prompt = new String(promptCodepoints, 0, promptCodepoints.length);
        asking = true;
        // Asking greys out the world behind the prompt:
        greyedOut = true;
        currentAskId = askId;
        currentAskPrompt = prompt;
        view.showAsk(prompt, answer -> answerAsk(askId, answer));
        // Make sure world is visible so that the ask pane is actually visible;
        // the world may not be visible if the ask is during world construction and there was not previously a world:
        setWorldVisible(true);
    }

    /**
     * The user answered an ask prompt (the window has already hidden it).
     */
    private void answerAsk(int askId, String answer)
    {
        asking = false;
        greyedOut = false;
        currentAskPrompt = null;
        debugHandler.getVmComms().sendAnswer(askId, answer);
    }

    /**
     * There is no pending ask request (called on every check of the debug VM without
     * a fresh ask prompt): hide any ask prompt.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void cancelAsk()
    {
        if (asking)
        {
            // Hiding the prompt also removes the grey-out:
            asking = false;
            greyedOut = false;
            currentAskPrompt = null;
        }
        view.hideAsk();
    }

    // ---- Execution twirler ----

    /**
     * Record the last time (from System.currentTimeMillis) that the user code started executing.
     * If enough time has passed then show the execution twirler.
     * @param lastExecStartTime The last time the user code started executing, or zero if it has now finished executing.
     * @param delayLoop The true or false value to indicate whether there is a delay loop or not
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void setLastUserExecutionStartTime(long lastExecStartTime, boolean delayLoop)
    {
        this.lastExecStartTime = lastExecStartTime;
        if (lastExecStartTime == 0L)
        {
            setTwirling(false);
        }
        else
        {
            long duration = System.currentTimeMillis() - lastExecStartTime;
            if (duration < 4000L)
            {
                setTwirling(false);
                JavaFXUtil.runAfter(Duration.millis(4000L - duration), () -> {
                    if (this.lastExecStartTime == lastExecStartTime && !delayLoop)
                    {
                        setTwirling(true);
                    }
                });
            }
            else if (!delayLoop)
            {
                setTwirling(true);
            }
        }
    }

    private void setTwirling(boolean twirling)
    {
        this.twirling = twirling;
        view.setExecutionTwirling(twirling);
    }

    // ---- Other debug VM callbacks ----

    /**
     * The error count went up: show the terminal and bring it to the front.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void bringTerminalToFront()
    {
        project.getTerminal().showHide(true);
        project.getTerminal().getWindow().toFront();
    }

    // ---- Showing state and speed (main window and full-screen window) ----

    /**
     * Show the current simulation state in the window, and on the full-screen controls.
     */
    private void showState()
    {
        view.stateChanged(stateProperty.get(), atBreakpoint);
        if (fullScreenView != null)
        {
            fullScreenView.updateState(stateProperty.get(), atBreakpoint);
        }
    }

    /**
     * Show the simulation speed on the speed slider.
     *
     * @param speed              the speed
     * @param includeFullScreen  whether to update the full-screen controls as well
     */
    private void showSpeed(int speed, boolean includeFullScreen)
    {
        shownSpeed = speed;
        view.showSpeed(speed);
        if (includeFullScreen && fullScreenView != null)
        {
            fullScreenView.setSpeed(speed);
        }
    }

    /**
     * The window currently showing the project.
     */
    public ProjectView getView()
    {
        return view;
    }

    // ---- Full screen and the display state (SuperGreenfoot) ----

    /**
     * Enter or leave full-screen play mode. If the full-screen window exists but
     * the user has gone back to the main window, bring it forward.
     */
    public void toggleFullScreenView()
    {
        if (fullScreenView != null && !fullScreenView.isFocused())
        {
            fullScreenView.toFront();
            fullScreenView.requestFocus();
        }
        else if (fullScreenView != null)
        {
            exitFullScreenView();
        }
        else
        {
            fullScreenView = new FullScreenView(this);
            fullScreenView.setImage(lastWorldImage);
            fullScreenView.updateState(stateProperty.get(), atBreakpoint);
            fullScreenView.setSpeed(lastUserSetSpeed);
            fullScreenView.setPixelPerfect(fsPixelPerfect);
            fullScreenView.setControlsLocked(fsControlsLocked);
            fullScreenView.setControlsVisible(fsControlsVisible && !fsControlsLocked);
            fullScreenView.show();
            sendDisplayState();
        }
    }

    /**
     * Leave full-screen play mode if it is active.
     */
    public void exitFullScreenView()
    {
        if (fullScreenView != null)
        {
            FullScreenView v = fullScreenView;
            fullScreenView = null;
            fsPixelPerfect = v.isPixelPerfect();
            fsControlsLocked = v.isControlsLocked();
            fsControlsVisible = v.isControlsVisible() || !fsControlsLocked;
            v.leave();
            view.requestWorldFocus();
            sendDisplayState();
        }
    }

    /**
     * A window opened by scenario code (a JOptionPane, say) closed while it had
     * the keyboard. The debug VM has no Dock icon and now no window either, so
     * take the keyboard back, for the full-screen view if it is up.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void scenarioWindowClosed()
    {
        Utility.appToFront();
        // appToFront works on the AWT thread; focus the window once it has run:
        EventQueue.invokeLater(() -> Platform.runLater(() -> {
            Stage target = fullScreenView != null ? fullScreenView : view.getWindow();
            target.toFront();
            target.requestFocus();
        }));
    }

    /**
     * Whether the full-screen play window is showing.
     */
    public boolean isFullScreenView()
    {
        return fullScreenView != null;
    }

    /**
     * Scenario code asked (via Greenfoot.setFullScreen and friends) for a display
     * change. Only the fields the scenario actually set are applied.
     *
     * @param seq    the request's sequence number (echoed back in the state)
     * @param flags  values and mask, see DisplayState
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void receivedDisplayRequest(int seq, int flags)
    {
        lastAppliedDisplayRequest = seq;
        if (DisplayState.isRequested(flags, DisplayState.PIXEL_PERFECT))
        {
            fsPixelPerfect = DisplayState.value(flags, DisplayState.PIXEL_PERFECT);
        }
        if (DisplayState.isRequested(flags, DisplayState.CONTROLS_VISIBLE))
        {
            fsControlsVisible = DisplayState.value(flags, DisplayState.CONTROLS_VISIBLE);
        }
        if (DisplayState.isRequested(flags, DisplayState.CONTROLS_LOCKED))
        {
            fsControlsLocked = DisplayState.value(flags, DisplayState.CONTROLS_LOCKED);
            if (fsControlsLocked)
            {
                fsControlsVisible = false;
            }
        }
        if (DisplayState.isRequested(flags, DisplayState.FULL_SCREEN))
        {
            boolean want = DisplayState.value(flags, DisplayState.FULL_SCREEN);
            if (want && fullScreenView == null && !asking)
            {
                toggleFullScreenView();   // applies the preferences and sends the state
                return;
            }
            else if (!want && fullScreenView != null)
            {
                exitFullScreenView();     // sends the state
                return;
            }
        }
        if (fullScreenView != null)
        {
            fullScreenView.setPixelPerfect(fsPixelPerfect);
            fullScreenView.setControlsLocked(fsControlsLocked);
            fullScreenView.setControlsVisible(fsControlsVisible && !fsControlsLocked);
        }
        sendDisplayState();
    }

    /**
     * The full-screen window's controls were changed by the user.
     */
    public void displayStateChanged()
    {
        if (fullScreenView != null)
        {
            fsPixelPerfect = fullScreenView.isPixelPerfect();
            fsControlsLocked = fullScreenView.isControlsLocked();
            fsControlsVisible = fullScreenView.isControlsVisible();
        }
        sendDisplayState();
    }

    /**
     * Send the debug VM the current display state, so that Greenfoot.isFullScreen(),
     * getScreenWidth() and the rest answer truthfully.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void sendDisplayState()
    {
        if (debugHandler.getVmComms() == null)
        {
            return;
        }
        Rectangle2D screen = screenBounds();
        boolean fs = fullScreenView != null;
        boolean visible = fs ? fullScreenView.isControlsVisible() : fsControlsVisible;
        boolean locked = fs ? fullScreenView.isControlsLocked() : fsControlsLocked;
        boolean pixel = fs ? fullScreenView.isPixelPerfect() : fsPixelPerfect;
        double scale = fs ? fullScreenView.getDisplayScale() : 1.0;
        lastReportedScreen = screen;
        debugHandler.getVmComms().sendDisplayState(DisplayState.encode(fs, visible, locked, pixel, true,
                (int) Math.round(screen.getWidth()), (int) Math.round(screen.getHeight()), scale,
                lastAppliedDisplayRequest));
    }

    /**
     * The window showing the project may have moved to another screen: if so, resend
     * the display state, so that Greenfoot.getScreenWidth() follows it.
     */
    public void screenMayHaveChanged()
    {
        if (fullScreenView != null)
        {
            return;
        }
        Rectangle2D now = screenBounds();
        if (!now.equals(lastReportedScreen))
        {
            sendDisplayState();
        }
    }

    /**
     * The bounds of the screen the window (or the full-screen window) is on.
     */
    private Rectangle2D screenBounds()
    {
        Window w = fullScreenView != null ? fullScreenView : view.getWindow();
        List<Screen> screens = Screen.getScreensForRectangle(w.getX(), w.getY(),
                Math.max(1, w.getWidth()), Math.max(1, w.getHeight()));
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        return screen.getBounds();
    }

    // ---- Keyboard and mouse input to the world ----

    /**
     * Forward a key event from a world view (the main one or the full-screen one)
     * to the scenario in the debug VM.
     */
    public void forwardWorldKeyEvent(KeyEvent e)
    {
        if (disposed)
        {
            return;
        }

        // Ignore keypresses if we are currently waiting for an ask-answer:
        if (asking)
        {
            return;
        }

        int eventType;
        if (e.getEventType().equals(KeyEvent.KEY_PRESSED))
        {
            eventType = KEY_DOWN;
        }
        else if (e.getEventType().equals(KeyEvent.KEY_RELEASED))
        {
            eventType = KEY_UP;
        }
        else if (e.getEventType().equals(KeyEvent.KEY_TYPED))
        {
            eventType = KEY_TYPED;
        }
        else
        {
            return;
        }
        debugHandler.getVmComms().sendKeyEvent(eventType, e.getCode(), e.getText());

        // Don't consume keypresses involving control because they might be menu accelerators.
        // Consume anything else, including anything involving alt, because we don't want alt
        // to trigger the menu like it usually would on Windows.
        // On MacOS consuming the key doesn't prevent the menu accelerators firing.
        if (!e.isControlDown() || (e.isAltDown() && Config.isWinOS()))
        {
            e.consume();
        }
    }

    /**
     * Forward a mouse event from a world view to the scenario in the debug VM,
     * and (optionally) handle actor picking and dragging while paused.
     *
     * @param e         The event
     * @param worldPos  The event position in world pixel coordinates
     * @param allowPick Whether clicks while paused may pick/drag actors (main view only)
     */
    public void forwardWorldMouseEvent(MouseEvent e, Point2D worldPos, boolean allowPick)
    {
        if (disposed)
        {
            return;
        }

        boolean paused = stateProperty.get() == SimulationState.PAUSED && allowPick;
        int eventType;
        if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
        {
            if (e.getButton() == MouseButton.PRIMARY && (!Config.isMacOS() || !e.isControlDown()))
            {
                view.hideWorldContextMenu();
                if (paused)
                {
                    pickRequest(worldPos, PickType.LEFT_CLICK);
                }
            }
            eventType = MOUSE_CLICKED;
        }
        else if (e.getEventType() == MouseEvent.MOUSE_PRESSED)
        {
            eventType = MOUSE_PRESSED;
            if (paused && e.isPrimaryButtonDown() && !e.isControlDown())
            {
                // Begin a drag. We do this on MOUSE_PRESSED, because MOUSE_DRAG_DETECTED requires
                // several pixels of movement, which might take us off the actor if it is small.
                pickRequest(worldPos, PickType.DRAG);
            }
        }
        else if (e.getEventType() == MouseEvent.MOUSE_RELEASED)
        {
            eventType = MOUSE_RELEASED;
            // Finish any current drag:
            if (curDragRequest != -1)
            {
                Point2D cellPos = pixelToCellCoordinates(worldPos);
                debugHandler.getVmComms().endDrag(curDragRequest, (int)cellPos.getX(), (int)cellPos.getY());
                curDragRequest = -1;
                saveTheWorldRecorder.moveActor(draggedActor, (int)cellPos.getX(), (int)cellPos.getY());
            }
        }
        else if (e.getEventType() == MouseEvent.MOUSE_DRAGGED)
        {
            // Continue the drag if one is going:
            if (e.getButton() == MouseButton.PRIMARY && paused && curDragRequest != -1)
            {
                debugHandler.getVmComms().continueDrag(curDragRequest, (int)worldPos.getX(), (int)worldPos.getY());
            }

            eventType = MOUSE_DRAGGED;
        }
        else if (e.getEventType() == MouseEvent.MOUSE_MOVED)
        {
            eventType = MOUSE_MOVED;
        }
        else if (e.getEventType() == MouseEvent.MOUSE_EXITED)
        {
            eventType = MOUSE_EXITED;
        }
        else
        {
            return;
        }
        MouseButton button = e.getButton();
        if (Config.isMacOS() && button == MouseButton.PRIMARY && e.isControlDown())
        {
            button = MouseButton.SECONDARY;
        }

        // Don't send the event if they are placing a new actor:
        if (!view.isPlacingActor())
        {
            debugHandler.getVmComms().sendMouseEvent(
                    eventType, (int) worldPos.getX(), (int) worldPos.getY(),
                    button.ordinal(), e.getClickCount());
        }
    }

    /**
     * SuperGreenfoot: forward a mouse wheel (or trackpad scroll) event over the
     * world view to the scenario in the debug VM.
     */
    public void forwardWorldScrollEvent(ScrollEvent e, Point2D worldPos)
    {
        if (disposed || view.isPlacingActor())
        {
            return;
        }
        // JavaFX deltaY is negative when scrolling down; the scenario wants positive = down.
        int amount = (int) Math.round(-e.getDeltaY());
        if (amount != 0)
        {
            debugHandler.getVmComms().sendScrollEvent((int) worldPos.getX(), (int) worldPos.getY(), amount);
        }
    }

    /**
     * Tell the debug VM whether a world view has keyboard focus.
     */
    public void notifyWorldFocus(boolean focused)
    {
        if (disposed)
        {
            return;
        }
        debugHandler.getVmComms().worldFocusChanged(focused);
    }

    /**
     * The user asked for a context menu on the world (right-click) at the given
     * world position: while paused, show the menu for the actors there (or the world).
     */
    public void requestWorldContextMenu(Point2D worldPos)
    {
        if (stateProperty.get() == SimulationState.PAUSED)
        {
            pickRequest(worldPos, PickType.CONTEXT_MENU);
        }
    }

    /**
     * Convert world pixel coordinates to cell coordinates.
     */
    private Point2D pixelToCellCoordinates(Point2D worldPixels)
    {
        int cellSize = debugHandler.getVmComms().getWorldCellSize();
        if (cellSize == 0)
        {
            return worldPixels;
        }

        int xpos = (int)worldPixels.getX() / cellSize;
        int ypos = (int)worldPixels.getY() / cellSize;
        return new Point2D(xpos, ypos);
    }

    // ---- Picking actors: selecting, dragging and their context menus ----

    /**
     * Performs a pick request on the debug VM at given coordinates.
     */
    private void pickRequest(Point2D worldPosition, PickType pickType)
    {
        curPickType = pickType;
        Debugger debugger = project.getDebugger();
        // Bit hacky to pass positions as strings, but mirroring the values as integers
        // would have taken a lot of code changes to route through to VMReference:
        DebuggerObject xObject = debugger.getMirror("" + (int) worldPosition.getX());
        DebuggerObject yObject = debugger.getMirror("" + (int) worldPosition.getY());
        int thisPickId = nextPickId++;
        DebuggerObject pickIdObject = debugger.getMirror("" + thisPickId);
        String requestTypeString = pickType == PickType.DRAG ? "drag" : "";
        DebuggerObject requestTypeObject = debugger.getMirror(requestTypeString);
        // One pick at a time only:
        curPickRequest = thisPickId;
        curPickPoint = worldPosition;


        // Need to find out which actors are at the point.  Do this in background thread to
        // avoid blocking the GUI thread:
        Utility.runBackground(() ->
            debugger.instantiateClass("greenfoot.core.PickActorHelper",
                new String[] {"java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"},
                new DebuggerObject[] {xObject, yObject, pickIdObject, requestTypeObject})
        );
        // Once that completes, pickResults(..) will be called.
    }

    /**
     * Callback when a pick has completed (i.e. a request to find actors at given position)
     * @param pickId The ID of the pick has requested
     * @param actors The list of actors found.  May be any size.
     * @param world The world -- only relevant if actors list is empty.
     */
    @OnThread(Tag.Any)
    public void pickResults(int pickId, List<DebuggerObject> actors, DebuggerObject world)
    {
        Platform.runLater(() -> {
            if (curPickRequest != pickId)
            {
                return; // Pick has been cancelled by a more recent pick, so ignore
            }

            if (curPickType == PickType.CONTEXT_MENU)
            {
                // If single actor, show simple context menu:
                if (!actors.isEmpty())
                {
                    NamedValue[] namesForActors = debugHandler.nameObjects(actors);
                    // This is a list of menus; if there's only one we'll display
                    // directly in context menu.  If there's more than one, we'll
                    // have a higher level menu to pick between them.
                    List<Menu> actorMenus = new ArrayList<>();
                    for (int i = 0; i < actors.size(); i++)
                    {
                        DebuggerObject actor = actors.get(i);
                        Target target = project.getTarget(actor.getClassName());
                        // Should always be ClassTarget, but check in case:
                        if (target instanceof ClassTarget)
                        {
                            Menu menu = new Menu(namesForActors[i].getName() + ":" + actor.getClassName());
                            ObjectWrapper.createMethodMenuItems(menu.getItems(), project.loadClass(actor.getClassName()), new RecordInvoke(actor), "", true);
                            menu.getItems().add(makeInspectMenuItem(actor, namesForActors[i].getName()));
                            //add a listener to the action event on the items in the sub-menu to hide the context menus
                            for (MenuItem menuItem : menu.getItems())
                            {
                                menuItem.addEventHandler(ActionEvent.ACTION, e -> view.hideWorldContextMenu());
                            }

                            MenuItem removeItem = new MenuItem(Config.getString("world.handlerDelegate.remove"));
                            JavaFXUtil.addStyleClass(removeItem, MENU_STYLE_INBUILT);
                            removeItem.setOnAction(e -> removeActor(actor));
                            menu.getItems().add(removeItem);
                            actorMenus.add(menu);
                        }
                    }
                    if (actorMenus.size() == 1)
                    {
                        // No point showing higher-level menu with one item, collapse:
                        view.showWorldContextMenu(actorMenus.get(0).getItems(), curPickPoint);
                    }
                    else
                    {
                        view.showWorldContextMenu(new ArrayList<MenuItem>(actorMenus), curPickPoint);
                    }
                }
                else
                {
                    Target target = project.getTarget(world.getClassName());
                    // Should always be ClassTarget, but check in case:
                    if (target instanceof ClassTarget)
                    {
                        ObservableList<MenuItem> items = FXCollections.observableArrayList();
                        ObjectWrapper.createMethodMenuItems(items,
                                project.loadClass(world.getClassName()), new RecordInvoke(world), "", true);
                        items.add(makeInspectMenuItem(world, debugHandler.nameObjects(List.of(world))[0].getName()));

                        MenuItem saveTheWorld = new MenuItem(Config.getString("save.world"));
                        JavaFXUtil.addStyleClass(saveTheWorld, MENU_STYLE_INBUILT);
                        saveTheWorld.setOnAction(e -> {
                            if (!saveTheWorldRecorder.writeCode(className -> ((ClassTarget) target).getEditor()))
                            {
                                DialogManager.showErrorFX(view.getWindow(), "cannot-save-world");
                            }
                            else
                            {
                                project.scheduleCompilation(true, CompileReason.USER,
                                        CompileType.INDIRECT_USER_COMPILE, project.getUnnamedPackage());
                            }
                        });
                        items.add(saveTheWorld);

                        view.showWorldContextMenu(items, curPickPoint);
                    }
                }
            }
            else if (curPickType == PickType.DRAG && !actors.isEmpty())
            {
                // Left-click drag, and there is an actor there, so begin drag:
                curDragRequest = pickId;
                draggedActor = actors.get(0);
            }
            else if (curPickType == PickType.LEFT_CLICK && !actors.isEmpty())
            {
                debugHandler.addSelectedObjects(actors, view.worldToScreen(curPickPoint));
                view.actorClicked(actors.get(0));
            }
            else if (curPickType == PickType.LEFT_CLICK)
            {
                // A click on the world's background:
                view.actorClicked(null);
            }
        });
    }

    /**
     * Makes a MenuItem with an Inspect command for the given debugger object
     */
    private MenuItem makeInspectMenuItem(DebuggerObject debuggerObject, String name)
    {
        MenuItem inspectItem = new MenuItem(Config.getString("debugger.objectwrapper.inspect"));
        JavaFXUtil.addStyleClass(inspectItem, MENU_STYLE_INBUILT);
        inspectItem.setOnAction(e -> inspectObject(debuggerObject, name));
        return inspectItem;
    }

    /**
     * Show the object inspector for the given actor or world.
     */
    @OnThread(Tag.FXPlatform)
    public void inspectObject(DebuggerObject debuggerObject, String name)
    {
        InvokerRecord ir = new ObjectInspectInvokerRecord(debuggerObject.getClassName());
        project.getInspectorInstance(debuggerObject, name, project.getUnnamedPackage(), ir, view.getWindow(), null);  // shows the inspector
    }

    /**
     * Remove the given actor from its world (as the actor's "Remove" menu item does).
     */
    @OnThread(Tag.FXPlatform)
    public void removeActor(DebuggerObject actor)
    {
        project.getDebugger().instantiateClass(
            "greenfoot.core.RemoveFromWorldHelper",
            new String[]{"java.lang.Object"},
            new DebuggerObject[]{actor});
        saveTheWorldRecorder.removeActor(actor);
    }

    /**
     * The name the IDE uses for an object in the world (e.g. "walker1"), putting it on
     * the object bench so its methods can be called.
     */
    @OnThread(Tag.FXPlatform)
    public String nameObject(DebuggerObject object)
    {
        return debugHandler.nameObjects(List.of(object))[0].getName();
    }

    /**
     * The method menu items for an actor (or the world), as its context menu offers them:
     * its own methods first, then a submenu per class it inherits from.  Choosing one
     * calls the method (asking for any parameters) and records it for Save the World.
     */
    @OnThread(Tag.FXPlatform)
    public List<MenuItem> makeMethodItems(DebuggerObject object)
    {
        ObservableList<MenuItem> items = FXCollections.observableArrayList();
        Target target = project.getTarget(object.getClassName());
        if (target instanceof ClassTarget)
        {
            ObjectWrapper.createMethodMenuItems(items, project.loadClass(object.getClassName()),
                    new RecordInvoke(object), "", true);
        }
        return items;
    }

    // ---- Adding actors to the world by hand ----

    /**
     * Place an actor that has already been constructed (interactively) into the world.
     *
     * @param actor       The actor
     * @param ir          The invoker record for its construction
     * @param paramTypes  The parameter types of the constructor call
     * @param dest        Where to place it, in world pixel coordinates
     */
    public void placeNewActor(DebuggerObject actor, InvokerRecord ir, JavaType[] paramTypes, Point2D dest)
    {
        Point2D cell = pixelToCellCoordinates(dest);
        saveTheWorldRecorder.createActor(actor, ir.getArgumentValues(), paramTypes);
        new Thread("Add actor on click")
        {
            public void run()
            {
                addActorQuick(dest, cell, actor);
            }
        }.start();
    }

    /**
     * Construct a new actor of the given class with its no-argument constructor
     * (shift-click) and place it into the world.
     *
     * @param typeName    The actor's class name
     * @param dest        Where to place it, in world pixel coordinates
     */
    public void quickAddActor(String typeName, Point2D dest)
    {
        Point2D cell = pixelToCellCoordinates(dest);
        new Thread("Add actor on shift click") {
            public void run()
            {
                DebuggerResult result = project.getDebugger().instantiateClass(typeName);
                DebuggerObject actor = result.getResultObject();

                if (actor != null)
                {
                    saveTheWorldRecorder.createActor(actor, new String[0],
                            new JavaType[0]);
                    addActorQuick(dest, cell, actor);
                }
            }
        }.start();
    }

    /**
     * A helper method used for adding actors to the world as part of the shift-click "quick add"
     * functionality. This must not be called from the UI event thread.
     *
     * @param dest  the destination coordinates in the world (pixels)
     * @param cell  the destination coordinates in the world (cells)
     * @param actor  the actor to be added
     */
    @OnThread(Tag.Any)
    @SuppressWarnings("threadchecker")
    private void addActorQuick(Point2D dest, Point2D cell, DebuggerObject actor)
    {
        // Note: threadchecker checking disabled due to incorrect tagging of "getMirror" method.

        saveTheWorldRecorder.addActorToWorld(actor, (int)cell.getX(), (int)cell.getY());

        // Bit hacky to pass positions as strings, but mirroring the values as integers
        // would have taken a lot of code changes to route through to VMReference:
        DebuggerObject xObject = project.getDebugger().getMirror("" + (int) dest.getX());
        DebuggerObject yObject = project.getDebugger().getMirror("" + (int) dest.getY());

        project.getDebugger().instantiateClass(
                "greenfoot.core.AddToWorldHelper",
                new String[] {"java.lang.Object", "java.lang.String", "java.lang.String"},
                new DebuggerObject[] {actor, xObject, yObject});
    }

    /**
     * Gets a Reflective for the Actor class.
     */
    public Reflective getActorReflective()
    {
        return new JavaReflective(project.loadClass("greenfoot.Actor"));
    }

    /**
     * Gets a Reflective for the World class.
     */
    private Reflective getWorldReflective()
    {
        return new JavaReflective(project.loadClass("greenfoot.World"));
    }

    // ---- Interactive calls (PackageUI) ----

    /**
     * Whether an interactive call (a method called on an actor or the world, or a
     * constructor or static method called from the class diagram) is executing.  Its
     * result will be shown relative to the window that started it, so the window
     * showing the project should not be replaced while this is true.
     */
    public boolean isInvocationRunning()
    {
        return invocationsRunning > 0;
    }

    /**
     * Wrap a result watcher so that the calls it watches are counted while they execute
     * (see isInvocationRunning).
     */
    private ResultWatcher countWhileRunning(ResultWatcher watcher)
    {
        return new ResultWatcher()
        {
            private boolean running = false;

            private void finished()
            {
                if (running)
                {
                    running = false;
                    invocationsRunning--;
                }
            }

            @Override
            public void beginCompile()
            {
                watcher.beginCompile();
            }

            @Override
            public void beginExecution(InvokerRecord ir)
            {
                if (!running)
                {
                    running = true;
                    invocationsRunning++;
                }
                watcher.beginExecution(ir);
            }

            @Override
            public void putResult(DebuggerObject result, String name, InvokerRecord ir)
            {
                finished();
                watcher.putResult(result, name, ir);
            }

            @Override
            public void putError(String message, InvokerRecord ir)
            {
                finished();
                watcher.putError(message, ir);
            }

            @Override
            public void putException(ExceptionDescription exception, InvokerRecord ir)
            {
                finished();
                watcher.putException(exception, ir);
            }

            @Override
            public void putVMTerminated(InvokerRecord ir, boolean terminatedByUserCode)
            {
                finished();
                watcher.putVMTerminated(ir, terminatedByUserCode);
            }
        };
    }

    /**
     * An InvokeListener which also records the invocation for save-the-world purposes
     */
    private class RecordInvoke implements InvokeListener
    {
        private final DebuggerObject target;

        public RecordInvoke(DebuggerObject target)
        {
            this.target = target;
        }

        @Override
        public void executeMethod(MethodView mv)
        {
            // We must put the object on the bench so that it has a name on the debug VM
            // side.  Without a name, you can't call a method on it using the BlueJ workers.
            // The object bench gets cleared on compile, so that takes care of clean-up:
            String objInstanceName = debugHandler.ensureObjectOnBench(target, target.getGenType()).getName();

            Stage window = view.getWindow();
            ResultWatcher watcher = new ResultWatcherBase(target, objInstanceName,
                    project.getUnnamedPackage(), window, mv) {
                @Override
                @OnThread(Tag.FXPlatform)
                public void beginExecution(InvokerRecord ir)
                {
                    super.beginExecution(ir);
                    view.interactiveCallStarted(ir.hasVoidResult() ? ir.toStatement() : ir.toExpression());
                }

                @Override
                protected void addInteraction(InvokerRecord ir)
                {
                    saveTheWorldRecorder.callActorOrWorldMethod(target, mv.getMethod(),
                            ir.getArgumentValues(), mv.getParamTypes(false));
                }
            };

            if (ProjectUtils.checkDebuggerState(project, window)) {
                // Invoker invoker = new Invoker(pmf, mv, objInstanceName, target, watcher);
                Package unpkg = project.getPackage("");
                Invoker invoker = new Invoker(window, unpkg, mv, countWhileRunning(watcher), unpkg.getCallHistory(), debugHandler, debugHandler,
                        project.getDebugger(), objInstanceName);
                invoker.invokeInteractive();
            }
        }

        @Override
        public void callConstructor(ConstructorView cv)
        {
            //We are not used for constructors, so this won't get called.
        }
    }

    /*
     * PackageUI getStage() implementation: the window showing the project.
     * @see bluej.pkgmgr.PackageUI#getStage()
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public Stage getStage()
    {
        return view == null ? null : view.getWindow();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void callStaticMethodOrConstructor(CallableView cv)
    {
        suggestTerminateIfAskingThenRun(() -> callStaticMethodOrConstructorNowReady(cv));
    }

    private void callStaticMethodOrConstructorNowReady(CallableView cv)
    {
        ResultWatcher watcher = null;
        Package pkg = project.getPackage("");
        Stage window = view.getWindow();

        if (cv instanceof ConstructorView)
        {
            // Is it a World subclass?  If so, count it as constructing a world.
            Class<?> viewClass = cv.getDeclaringView().getViewClass();
            try
            {
                // Must use same class loader for the World class for this to work:
                if (viewClass.getClassLoader().loadClass("greenfoot.World").isAssignableFrom(viewClass))
                {
                    constructingWorld = true;
                    view.worldStatusChanged();
                }
            }
            catch (ClassNotFoundException e)
            {
                // Just don't set the flag.
            }

            // if we are constructing an object, create a watcher that waits for
            // completion of the call and then places the object on the object
            // bench
            watcher = new ResultWatcherBase(pkg, window, cv) {
                @Override
                public void beginCompile()
                {
                    super.beginCompile();
                }

                @Override
                protected void nonNullResult(DebuggerObject result, String name, InvokerRecord ir)
                {
                    if ((name == null) || (name.length() == 0))
                        name = "result";

                    project.getTerminal().activate(false);
                    debugHandler.addObject(result, result.getGenType(), name);
                    project.getDebugger().addObject(project.getPackage("").getId(), name, result);

                    gotConstructionResult(result, ir, cv.getParamTypes(false));
                }

                @Override
                protected void addInteraction(InvokerRecord ir)
                {
                    // Nothing we can do here.
                }

                @Override
                public void putException(ExceptionDescription exception, InvokerRecord ir)
                {
                    constructingWorld = false;
                    project.getTerminal().activate(false);
                    view.worldStatusChanged();
                    super.putException(exception, ir);
                }

                @Override
                public void putError(String msg, InvokerRecord ir)
                {
                    constructingWorld = false;
                    project.getTerminal().activate(false);
                    view.worldStatusChanged();
                    super.putError(msg, ir);
                }
            };
        }
        else if (cv instanceof MethodView) {
            // create a watcher
            // that waits for completion of the call and then displays the
            // result (or does nothing if void)
            watcher = new ResultWatcherBase(pkg, window, cv) {
                @Override
                protected void addInteraction(InvokerRecord ir)
                {
                    project.getTerminal().activate(false);
                    saveTheWorldRecorder.callStaticMethod(cv.getClassName(), ((MethodView) cv).getMethod(),
                            ir.getArgumentValues(), cv.getParamTypes(false));
                }
            };
        }

        // create an Invoker to handle the actual invocation
        if (ProjectUtils.checkDebuggerState(project, window)) {
            project.getTerminal().activate(true);
            new Invoker(window, pkg, cv, watcher == null ? null : countWhileRunning(watcher), pkg.getCallHistory(), debugHandler, debugHandler,
                    project.getDebugger(), null).invokeInteractive();
        }
    }

    /**
     * We have the result of an interactive object construction. Do the appropriate thing
     * depending on the object type.
     *
     * @param result      The result object
     * @param ir          The invocation record for the invocation which created the object
     * @param paramTypes  The parameter types for the constructor call
     */
    private void gotConstructionResult(DebuggerObject result, InvokerRecord ir, JavaType[] paramTypes)
    {
        Reflective typeReflective = result.getGenType().getReflective();
        if (typeReflective != null)
        {
            // We need to convert the reflective to a JavaReflective in order for the
            // "isAssignableFrom" tests below to work.
            Class<?> cl = project.loadClass(typeReflective.getName());
            if (cl != null)
            {
                typeReflective = new JavaReflective(cl);
            }
            else {
                typeReflective = null;
            }
        }

        if (typeReflective != null && getActorReflective().isAssignableFrom(typeReflective))
        {
            // It's an actor!  The window lets the user place it:
            view.beginPlacingActor(result, ir, paramTypes, typeReflective);
        }
        else if (typeReflective != null && getWorldReflective().isAssignableFrom(typeReflective))
        {
            // It's a world
            String className = result.getGenType().getErasedType().toString();
            Target t = project.getTarget(className);
            if (t instanceof ClassTarget)
            {
                currentWorld = (ClassTarget)t;
            }

            // Shouldn't wait on debug VM in the UI thread, so run in a separate thread:
            new Thread("Setting constructed world")
            {
                public void run()
                {
                    project.getDebugger()
                    .instantiateClass("greenfoot.core.SetWorldHelper",
                            new String[]{"java.lang.Object"},
                            new DebuggerObject[] { result });
                    Platform.runLater(() -> saveTheWorldRecorder.recordingValid());
                }
            }.start();
        }
        else
        {
            // If neither actor nor world, we just inspect the constructed object:
            project.getInspectorInstance(result, "<object>", project.getUnnamedPackage(), null, view.getWindow(), null);
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void highlightObject(DebuggerObject currentObject)
    {
        JavaType actorType = new GenTypeClass(new JavaReflective(Actor.class));

        if (currentObject != null && currentObject.getGenType() != null
            && actorType.isAssignableFrom(currentObject.getGenType()))
        {
            // It is an actor; try to find the bounds.  Do this in background thread to
            // avoid blocking the GUI thread:
            Utility.runBackground(() -> {
                // Since the execution is paused, probably on the simulation thread, it is
                // a bit awkward to execute code to access the details from another thread, especially
                // for methods like getX() that the user may have overridden with arbitrary code.  So
                // instead we use the debugger to read the values directly out of fields in Actor/GreenfootImage:

                OptionalInt x = getIntegerField(currentObject, "greenfoot.Actor", "x");
                OptionalInt y = getIntegerField(currentObject, "greenfoot.Actor", "y");
                OptionalInt rotation = getIntegerField(currentObject, "greenfoot.Actor", "rotation");
                OptionalInt width = getIntegerField(currentObject, "greenfoot.Actor", "imageWidth");
                OptionalInt height = getIntegerField(currentObject, "greenfoot.Actor", "imageHeight");
                DebuggerObject world = getObjectField(currentObject, "greenfoot.Actor", "world");
                OptionalInt cellSize = getIntegerField(world, "greenfoot.World", "cellSize");

                if (x.isPresent() && y.isPresent() && width.isPresent()
                        && height.isPresent() && rotation.isPresent() && cellSize.isPresent())
                {
                    Platform.runLater(() -> view.showActorHighlight(
                            x.getAsInt() * cellSize.getAsInt() + (cellSize.getAsInt() / 2),
                            y.getAsInt() * cellSize.getAsInt() + (cellSize.getAsInt() / 2),
                            width.getAsInt(), height.getAsInt(), rotation.getAsInt()));
                }
            });
        }
        else
        {
            view.clearActorHighlight();
        }
    }

    /**
     * Given a debugger object (currentObject), get value of field declared in class className named
     * fieldName.  Returns null if the object is null or the field cannot be found.
     */
    @OnThread(Tag.Any)
    private static DebuggerObject getObjectField(DebuggerObject currentObject, String className, String fieldName)
    {
        if (currentObject == null || currentObject.isNullObject())
        {
            return null;
        }

        return currentObject.getFields().stream()
                .filter(f -> f.getDeclaringClassName().equals(className)
                    && f.getName().equals(fieldName))
                .map(f -> f.getValueObject())
                .findFirst()
                .orElse(null);
    }

    /**
     * Given a debugger object (currentObject), get integer field declared in class className named
     * fieldName.  Returns OptionalInt.empty if the object is null or the field cannot be found.
     */
    @OnThread(Tag.Any)
    private static OptionalInt getIntegerField(DebuggerObject currentObject, String className, String fieldName)
    {
        if (currentObject == null || currentObject.isNullObject())
        {
            return OptionalInt.empty();
        }

        return currentObject.getFields().stream()
            .filter(f -> f.getDeclaringClassName().equals(className)
                && f.getName().equals(fieldName))
            .mapToInt(f -> Integer.parseInt(f.getValueString()))
            .findFirst();
    }

    // ---- Saving and closing ----

    /**
     * Save the project (all editors and all project information).
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void doSave()
    {
        try
        {
            // Collect the various properties to be written out:
            Properties p = project.getProjectPropertiesCopy();
            p.setProperty("simulation.speed", Integer.toString(lastUserSetSpeed));
            // Only save if not default:
            if (debugHandler.getShmFileSize() != VMCommsMain.DEFAULT_MAPPED_SIZE)
            {
                p.setProperty("shm.size", Integer.toString(debugHandler.getShmFileSize()));
            }
            view.writeViewProperties(p);
            p.put("version", Boot.GREENFOOT_API_VERSION);
            if (currentWorld != null)
            {
                p.put("world.lastInstantiated", currentWorld.getQualifiedName());
            }
            project.saveEditorLocations(p);
            // SuperGreenfoot: window geometry went to the user's layout store
            ProjectLayoutStore.get().flush();
            List<String> classNames = new ArrayList<>();
            for (ClassTarget classTarget : project.getUnnamedPackage().getClassTargets())
            {
                classNames.add(classTarget.getQualifiedName());
            }
            classImages.save(p, classNames);
            scenarioInfo.store(p);

            // Actually write out the properties to disk:
            project.getUnnamedPackage().save(p);

            // Save editor contents, etc:
            project.getImportScanner().saveCachedImports();
            project.saveAllEditors();
        }
        catch (IOException ioe)
        {
            // The exception is logged earlier, so we won't bother logging again.
            // However, alert the user:
            DialogManager.showMessageFX(view.getWindow(), "error-saving-project");
        }
    }

    /**
     * Prompt for a location, save the scenario to the chosen location, and re-open the scenario
     * from its new location.
     */
    public void doSaveAs()
    {
        Stage window = view.getWindow();
        File choice = FileUtility.getSaveProjectFX(project, window, Config.getString("project.saveAs.title"));
        if (choice == null)
        {
            return;
        }

        if (! ProjectUtils.saveProjectCopy(project, choice, window))
        {
            return;
        }

        ProjectRegistry.close(this, true);

        Project p = Project.openProject(choice.getAbsolutePath());
        if (p == null) {
            // This shouldn't happen, but log an error just in case:
            Debug.reportError("Project save-as succeeded, but new project could not be opened");
            return;
        }

        ProjectManager.instance().launchProject(p);
    }

    /**
     * The project is being closed: save it, and close its inspectors, terminal and editors.
     */
    public void closeProject()
    {
        doSave();
        Project.cleanUp(project);
        project.getPackage("").closeAllEditors();
    }

    // ---- Save the world ----

    /**
     * Write the code for the interactions recorded since the world was created
     * (actors added, moved and removed, methods called) into the world class, then compile.
     *
     * @param fetchEditorByName  finds the editor of the class to write into, by class name
     */
    public void saveTheWorld(FXPlatformFunction<String, Editor> fetchEditorByName)
    {
        if (!saveTheWorldRecorder.writeCode(fetchEditorByName))
        {
            DialogManager.showErrorFX(view.getWindow(), "cannot-save-world");
        }
        else
        {
            project.scheduleCompilation(true, CompileReason.USER,
                    CompileType.INDIRECT_USER_COMPILE, project.getUnnamedPackage());
        }
    }

    // ---- Class files and class images ----

    /**
     * Save image file name for the given class to the project file, and mirror to the debug VM.
     * @param qualifiedName The qualified name of the class
     * @param imageFileName The file name of the image to use for that class.
     */
    public void saveAndMirrorClassImageFilename(String qualifiedName, String imageFileName)
    {
        classImages.set(qualifiedName, imageFileName);
        doSave();
        sendPropertyToDebugVM("class." + qualifiedName + ".image", imageFileName);
    }

    /**
     * Make sure an image file is in the project's images folder, copying it there if it
     * is somewhere else.
     *
     * @param originalImageFile  The image's file
     * @return  the name of the image file in the images folder
     */
    public String installClassImage(File originalImageFile)
    {
        File localImageFile;
        File imagesDir = new File(project.getProjectDir(), "images");
        if (originalImageFile.getParentFile().equals(imagesDir))
        {
            // The file is already in the project's images dir
            localImageFile = originalImageFile;
        }
        else
        {
            // Copy the image file to the project's images dir
            localImageFile = new File(imagesDir, originalImageFile.getName());
            GreenfootUtil.copyFile(originalImageFile, localImageFile);
        }
        return localImageFile.getName();
    }

    /**
     * Create a new class file from a template, add the class to the package and compile it.
     *
     * @param pkg            The package that should contain the new class.
     * @param superClassName The full qualified name of the super class.
     * @param className      The class's name, which will be created.
     * @param language       The source type of the class, e.g. Java or Stride.
     * @param templateFileName  The name of the template file to use
     * @return  the new class, or null if the file could not be written.
     */
    public ClassTarget createClassFile(Package pkg, String superClassName, String className, SourceType language,
            String templateFileName)
    {
        try
        {
            File dir = project.getProjectDir();
            final String extension = language.getExtension();
            File newFile = new File(dir, className + "." + extension);
            ProjectUtils.createSkeleton(className, superClassName, newFile,
                    templateFileName, project.getProjectCharset().toString());
            ClassTarget newClass = pkg.addClass(className);

            // The stride class needs to be compiled to be placed correctly on the class diagram.
            pkg.compile(newClass, CompileReason.LOADED, CompileType.INDIRECT_USER_COMPILE);

            return newClass;
        }
        catch (IOException ioe)
        {
            Debug.reportError(ioe);
            return null;
        }
    }

    /**
     * Copy a class's source file under a new class name and add the new class to the package
     * (it still needs compiling: see compileAddedClass).
     *
     * @return  the new class, or null if the file could not be copied.
     */
    public ClassTarget duplicateClassFile(ClassTarget originalClassTarget, String newClassName)
    {
        String originalClassName = originalClassTarget.getDisplayName();
        SourceType sourceType = originalClassTarget.getSourceType();
        final String extension = sourceType.getExtension();
        final Package pkg = originalClassTarget.getPackage();
        final File dir = pkg.getProject().getProjectDir();
        final File originalFile = new File(dir, originalClassName + "." + extension);
        final File newFile = new File(dir, newClassName + "." + extension);
        try
        {
            ProjectUtils.duplicate(originalClassName, newClassName, originalFile, newFile, sourceType);
            return pkg.addClass(newClassName);
        }
        catch (IOException ioe)
        {
            Debug.reportError(ioe);
            return null;
        }
    }

    /**
     * Compile a class that has just been added to the project, so that the state of
     * the scenario is correct.
     */
    public void compileAddedClass(ClassTarget classTarget)
    {
        classTarget.getPackage().compile(classTarget, CompileReason.LOADED, CompileType.INDIRECT_USER_COMPILE);
    }

    /**
     * The result of copying a class (and any libraries it needs) into the project.
     */
    public static class ImportedClass
    {
        /** The imported class, or null if it could not be found after copying. */
        public final ClassTarget classTarget;
        /** Whether libraries were copied as well (the debug VM must restart to load them). */
        public final boolean librariesImported;

        private ImportedClass(ClassTarget classTarget, boolean librariesImported)
        {
            this.classTarget = classTarget;
            this.librariesImported = librariesImported;
        }
    }

    /**
     * Copy a class's source or class file into the project, along with the libraries
     * in the "className/lib" folder beside it, and add it to the package.
     *
     * @param srcFile    The class's file
     * @param className  The class's name
     */
    public ImportedClass importClassFiles(File srcFile, String className)
    {
        boolean librariesImportedFlag = false;
        final Package pkg = project.getUnnamedPackage();

        // Copy the java/class file cross:
        File destFile = new File(project.getProjectDir(), srcFile.getName());
        GreenfootUtil.copyFile(srcFile, destFile);

        // Copy the lib files cross:
        File libFolder = new File(srcFile.getParentFile(), className + "/lib");
        if ( (libFolder.exists()) && (libFolder.listFiles().length > 0) )
        {
            for (File srcLibFile : libFolder.listFiles())
            {
                File destLibFile = new File(project.getProjectDir(), "+libs/" + srcLibFile.getName());
                GreenfootUtil.copyFile(srcLibFile, destLibFile);
            }
            librariesImportedFlag = true;
        }

        // We must reload the package to be able to access the GClass object:
        pkg.reload();
        return new ImportedClass((ClassTarget)pkg.getTarget(className), librariesImportedFlag);
    }

    /**
     * Restart the debug VM (for example to load newly imported libraries).
     */
    public void restartVM()
    {
        project.restartVM();
    }

    // ---- Documentation ----

    /**
     * Show the readme file for this project in an editor window.
     */
    public void openReadme()
    {
        ReadmeTarget target = project.getUnnamedPackage().getReadmeTarget();
        if (target.getEditor() == null)
        {
            DialogManager.showErrorFX(view.getWindow(), "error-open-readme");
        }
        else
        {
            target.getEditor().setEditorVisible(true, false);
        }
    }

    /**
     * Opens a browser tab (in an editor window) for the given fully qualified class name
     * of a built-in Greenfoot class (e.g. greenfoot.Actor)
     */
    public void openGreenfootDocTab(String qualifiedClassName)
    {
        project.getDefaultFXTabbedEditor().openGreenfootDocTab(qualifiedClassName);
    }
}
