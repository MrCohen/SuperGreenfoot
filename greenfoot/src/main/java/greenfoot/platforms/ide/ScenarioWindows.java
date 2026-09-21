/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
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
package greenfoot.platforms.ide;

import greenfoot.vmcomm.VMCommsSimulation;
import java.awt.AWTEvent;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * SuperGreenfoot: windows opened by scenario code (a JOptionPane, a JFrame, a
 * JavaFX Stage) when the debug VM runs without a Dock icon on macOS (see
 * VMReference). Such a window would open behind the IDE without keyboard focus,
 * and closing it would leave the keyboard with this invisible VM. So each one is
 * brought to the front, focused, as it shows, and once the last one closes while
 * it had the keyboard, the IDE is asked to take the keyboard back.
 */
@OnThread(Tag.Any)
public class ScenarioWindows
{
    private final VMCommsSimulation comms;
    // Whether a scenario window has the keyboard, i.e. this VM is the active app:
    private final AtomicBoolean holdsFocus = new AtomicBoolean(false);

    private ScenarioWindows(VMCommsSimulation comms)
    {
        this.comms = comms;
    }

    /**
     * Start watching scenario windows, if this platform can bring an app forward.
     */
    @OnThread(Tag.Swing)
    public static void install(VMCommsSimulation comms)
    {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND))
        {
            return;
        }
        ScenarioWindows windows = new ScenarioWindows(comms);
        Toolkit.getDefaultToolkit().addAWTEventListener(windows::awtEvent,
                AWTEvent.WINDOW_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        Platform.runLater(windows::watchStages);
    }

    @OnThread(Tag.Swing)
    private void awtEvent(AWTEvent e)
    {
        if (!(e.getSource() instanceof Window) || !isScenarioWindow((Window) e.getSource()))
        {
            return;
        }
        Window w = (Window) e.getSource();
        switch (e.getID())
        {
            case ComponentEvent.COMPONENT_SHOWN:
                Desktop.getDesktop().requestForeground(false);
                w.toFront();
                break;
            case WindowEvent.WINDOW_ACTIVATED:
                holdsFocus.set(true);
                break;
            case WindowEvent.WINDOW_DEACTIVATED:
                // Focus left for another app, unless the window is going away (it is
                // already hidden by now, and the hide/close event below decides):
                if (((WindowEvent) e).getOppositeWindow() == null && w.isShowing())
                {
                    holdsFocus.set(false);
                }
                break;
            case ComponentEvent.COMPONENT_HIDDEN:
            case WindowEvent.WINDOW_CLOSED:
                for (Window other : Window.getWindows())
                {
                    if (other.isShowing() && isScenarioWindow(other))
                    {
                        return;
                    }
                }
                returnFocusToIde();
                break;
        }
    }

    /**
     * Frames and dialogs that can take the keyboard; not popups or tooltips.
     */
    @OnThread(Tag.Swing)
    private static boolean isScenarioWindow(Window w)
    {
        return (w instanceof Frame || w instanceof Dialog) && w.getFocusableWindowState()
                && w.getType() != Window.Type.POPUP;
    }

    /**
     * JavaFX keeps a list of the showing windows: a Stage joins it when shown and
     * leaves it when hidden.
     */
    @OnThread(Tag.FXPlatform)
    private void watchStages()
    {
        javafx.stage.Window.getWindows().addListener((ListChangeListener<javafx.stage.Window>) c -> {
            while (c.next())
            {
                for (javafx.stage.Window w : c.getAddedSubList())
                {
                    if (w instanceof Stage)
                    {
                        stageShown((Stage) w);
                    }
                }
                if (c.wasRemoved()
                        && javafx.stage.Window.getWindows().stream().noneMatch(w -> w instanceof Stage))
                {
                    returnFocusToIde();
                }
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    private void stageShown(Stage stage)
    {
        if (stage.getProperties().putIfAbsent(ScenarioWindows.class, Boolean.TRUE) == null)
        {
            stage.focusedProperty().addListener((prop, was, focused) -> {
                if (focused)
                {
                    holdsFocus.set(true);
                }
                else if (stage.isShowing())
                {
                    // Focus left for another app (a stage being hidden is no longer showing)
                    holdsFocus.set(false);
                }
            });
        }
        // Activate on the AWT thread, then bring the stage up:
        EventQueue.invokeLater(() -> {
            Desktop.getDesktop().requestForeground(false);
            Platform.runLater(() -> {
                stage.toFront();
                stage.requestFocus();
            });
        });
    }

    private void returnFocusToIde()
    {
        if (holdsFocus.getAndSet(false))
        {
            comms.requestIdeFocus();
        }
    }
}
