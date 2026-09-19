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

import bluej.pkgmgr.Project;
import bluej.prefmgr.PrefMgr;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.export.mygame.ScenarioInfo;
import greenfoot.record.GreenfootRecorder;
import greenfoot.vmcomm.GreenfootDebugHandler;
import greenfoot.vmcomm.VMCommsMain;
import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Everything about one open project that is not part of the window showing it:
 * the link to the debug VM, the save-the-world recorder, the scenario details,
 * and the work waiting for the debug VM to be ready.
 *
 * <p>One controller exists per open project, for as long as the project is open.
 * A window ({@link ProjectView}) attaches to it to show the project.  This is
 * being split out of GreenfootStage a piece at a time so that the Classic
 * Greenfoot IDE and the new SuperGreenfoot IDE can share it.
 */
@OnThread(Tag.FXPlatform)
public class GreenfootProjectController implements VMCommsMain.CommsListener
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

    // Polls the shared memory for input from the debug VM:
    private AnimationTimer vmCommsHandler;

    // Tasks to add to executeAfterReady after the VM has been terminated:
    private final Queue<FXPlatformRunnable> executeAfterTermination = new LinkedList<>();
    // Tasks to be run once the VM has initialised:
    private final Queue<FXPlatformRunnable> executeAfterReady = new LinkedList<>();

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
    }

    /**
     * Set the window that shows this project.
     */
    public void attachView(ProjectView view)
    {
        this.view = view;
    }

    /**
     * Start talking to the debug VM (the attached view receives its callbacks),
     * and pass player-name changes on to it.
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

        PrefMgr.getPlayerName().addListener(playerNameListener);
    }

    /**
     * The project is being closed: stop passing player-name changes to it.
     */
    public void projectClosing()
    {
        PrefMgr.getPlayerName().removeListener(playerNameListener);
    }

    /**
     * The window no longer shows the project: stop talking to the debug VM.
     */
    public void dispose()
    {
        if (vmCommsHandler != null)
        {
            vmCommsHandler.stop();
            vmCommsHandler = null;
        }
    }

    /**
     * Run a task once the debug VM is ready to execute code.
     */
    public void runWhenVMReady(FXPlatformRunnable task)
    {
        executeAfterReady.add(task);
    }

    /**
     * Run a task once a restarted debug VM is ready to execute code.
     */
    public void runAfterVMRestart(FXPlatformRunnable task)
    {
        executeAfterTermination.add(task);
    }

    /**
     * The debug VM has terminated: tasks waiting for a restart now wait for the new VM.
     */
    public void vmTerminated()
    {
        executeAfterReady.addAll(executeAfterTermination);
        executeAfterTermination.clear();
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

    // The debug VM's callbacks, passed on to the view:

    @Override
    @OnThread(Tag.FXPlatform)
    public void worldChanged(boolean worldPresent)
    {
        view.worldChanged(worldPresent);
    }

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
    public void notifySimulationSpeed(int simSpeed)
    {
        view.notifySimulationSpeed(simSpeed);
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
