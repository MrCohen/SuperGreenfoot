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

import bluej.collect.DataCollector;
import bluej.collect.GreenfootInterfaceEvent;
import bluej.compiler.CompileInputFile;
import bluej.compiler.CompileReason;
import bluej.compiler.CompileType;
import bluej.compiler.Diagnostic;
import bluej.compiler.FXCompileObserver;
import bluej.debugger.gentype.Reflective;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.prefmgr.PrefMgr;
import bluej.utility.DialogManager;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.export.mygame.ScenarioInfo;
import greenfoot.record.GreenfootRecorder;
import greenfoot.vmcomm.GreenfootDebugHandler;
import greenfoot.vmcomm.GreenfootDebugHandler.SimulationStateListener;
import greenfoot.vmcomm.VMCommsMain;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.lang.reflect.Modifier;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;

/**
 * Everything about one open project that is not part of the window showing it:
 * the link to the debug VM, the simulation state (no world, paused, running and
 * so on) and what drives it (act, run, pause, reset, speed, compiling), the
 * save-the-world recorder, the scenario details, and the work waiting for the
 * debug VM to be ready.
 *
 * <p>One controller exists per open project, for as long as the project is open.
 * A window ({@link ProjectView}) attaches to it to show the project.  This is
 * being split out of GreenfootStage a piece at a time so that the Classic
 * Greenfoot IDE and the new SuperGreenfoot IDE can share it.
 */
@OnThread(Tag.FXPlatform)
public class GreenfootProjectController implements VMCommsMain.CommsListener,
        SimulationStateListener, FXCompileObserver
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

    // The window showing the project:
    private ProjectView view;
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
        JavaFXUtil.addChangeListenerPlatform(stateProperty, s -> view.stateChanged(s, atBreakpoint));
    }

    /**
     * Set the window that shows this project.
     */
    public void attachView(ProjectView view)
    {
        this.view = view;
    }

    /**
     * Start talking to the debug VM (the attached view receives its callbacks), pass
     * player-name changes on to it, send it the project properties, and start creating
     * the world that was showing when the project was last saved.
     */
    public void start()
    {
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
            view.sendDisplayState();   // so the world constructor can already ask about the screen
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
        view.showSpeed(lastUserSetSpeed, true);
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
                view.sendDisplayState();
                debugHandler.getVmComms().instantiateWorld(curWorld.getQualifiedName());
                // currentWorld will have been set to null when the VM terminated,
                // so we must set it back again:
                currentWorld = curWorld;
            });
        }
        else if (constructingWorld && view.isWorldAsking())
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
        if (view.isWorldAsking())
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
    public void setSpeedFromSlider(int newSpeed)
    {
        if (!settingSpeedFromSimulation)
        {
            lastUserSetSpeed = newSpeed;
            debugHandler.getVmComms().setSimulationSpeed(newSpeed);
            // Keep both speed sliders (main window and full-screen bar) in step:
            view.showSpeed(newSpeed, true);
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
        view.showSpeed(simSpeed, false);
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
        else if (view.isWorldGreyedOut() && !view.isWorldAsking())
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
            view.setWorldVisible(false);
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
            view.greyOutWorld();
            stateProperty.set(SimulationState.NO_WORLD);
        }
    }

    /**
     * A world image has been shown: if we were waiting for a world, it is here.
     */
    public void worldImageShown()
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
        view.greyOutWorld();
        view.worldStatusChanged();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public boolean compilerMessage(Diagnostic diagnostic, CompileType type)
    {
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
            view.stateChanged(stateProperty.get(), atBreakpoint);
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
            view.stateChanged(stateProperty.get(), atBreakpoint);
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
            view.setWorldVisible(false);
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
            view.vmTerminated();
            worldInstantiationError = false;
            settingSpeedFromSimulation = false;
            constructingWorld = false;
            view.setLastUserExecutionStartTime(0L, false);
            atBreakpoint = false;
            currentWorld = null;
            view.setWorldVisible(false);
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

    /**
     * Note whether the latest world image could be shown.
     */
    public void setWorldInstantiationError(boolean worldInstantiationError)
    {
        this.worldInstantiationError = worldInstantiationError;
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

    // ---- Other debug VM callbacks, passed on to the view ----

    @Override
    @OnThread(Tag.FXPlatform)
    public void receivedWorldImage(int width, int height, IntBuffer buffer)
    {
        view.receivedWorldImage(width, height, buffer);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void bringTerminalToFront()
    {
        view.bringTerminalToFront();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void receivedAsk(int askId, int[] promptCodepoints)
    {
        view.receivedAsk(askId, promptCodepoints);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void cancelAsk()
    {
        view.cancelAsk();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void setLastUserExecutionStartTime(long lastExecStartTime, boolean delayLoop)
    {
        view.setLastUserExecutionStartTime(lastExecStartTime, delayLoop);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void sendDisplayState()
    {
        view.sendDisplayState();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void receivedDisplayRequest(int seq, int flags)
    {
        view.receivedDisplayRequest(seq, flags);
    }
}
