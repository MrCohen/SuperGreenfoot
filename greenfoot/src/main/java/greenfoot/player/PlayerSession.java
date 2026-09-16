/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2005-2023 Poul Henriksen and Michael Kolling
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
package greenfoot.player;

import greenfoot.World;
import greenfoot.core.ExportedProjectProperties;
import greenfoot.core.Simulation;
import greenfoot.core.WorldHandler;
import greenfoot.event.SimulationListener;
import greenfoot.platforms.standalone.ActorDelegateStandAlone;
import greenfoot.sound.SoundFactory;
import greenfoot.sound.SoundMixer;
import greenfoot.util.GreenfootUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.function.Function;

/**
 * Runs a scenario outside the IDE: wires up the engine (delegates, simulation,
 * world handler, sound) around a scenario class loader, with no dependency on
 * any window toolkit. {@link PlayerFrame} puts a Swing window on top of it;
 * tests drive it headless.
 */
@OnThread(Tag.Any)
public final class PlayerSession
{
    /** Properties files looked for in the scenario class loader, in order. */
    public static final String STANDALONE_PROPERTIES = "standalone.properties";
    public static final String PROJECT_PROPERTIES = "project.greenfoot";

    private final ClassLoader loader;
    private final Properties props;
    private final String worldClassName;
    private final Constructor<?> worldConstructor;
    private final WorldHandlerDelegatePlayer delegate;
    private volatile Function<String, String> asker;
    private volatile boolean running = false;

    private PlayerSession(ClassLoader loader, Properties props, File saveDir,
                          WorldHandlerDelegatePlayer.FrameSink sink) throws ClassNotFoundException, NoSuchMethodException
    {
        this.loader = loader;
        this.props = props;

        // No bluej.Config here: its class initialiser needs JavaFX, which an
        // exported game does not ship. The engine paths the player uses never
        // need it (the sound mixer is told not to read IDE preferences).
        SoundMixer.setUseDevicePreference(false);
        GreenfootUtil.initialise(new GreenfootUtilDelegatePlayer(loader, saveDir, props.getProperty("player.name")));
        ActorDelegateStandAlone.setupAsActorDelegate();
        ActorDelegateStandAlone.initProperties(new ExportedProjectProperties(props));

        String name = props.getProperty("main.class");
        if (name == null || name.isEmpty()) {
            name = props.getProperty("world.lastInstantiated");
        }
        if (name == null || name.isEmpty()) {
            throw new ClassNotFoundException("No world class named in " + STANDALONE_PROPERTIES
                    + " (main.class) or " + PROJECT_PROPERTIES + " (world.lastInstantiated)");
        }
        worldClassName = name;
        Class<?> worldClass = Class.forName(worldClassName, true, loader);
        worldConstructor = worldClass.getConstructor();

        Simulation.initialize();
        delegate = new WorldHandlerDelegatePlayer(this, sink);
        WorldHandler.initialise(delegate);
        Simulation sim = Simulation.getInstance();
        sim.attachWorldHandler(WorldHandler.getInstance());
        sim.addSimulationListener(SoundFactory.getInstance().getSoundCollection());
        sim.addSimulationListener(SoundMixer.getInstance().getSimulationListener());
        sim.addSimulationListener(new SimulationListener() {
            @Override
            @OnThread(Tag.Simulation)
            public void simulationChangedSync(SyncEvent e)
            {
                if (e == SyncEvent.STARTED) {
                    running = true;
                }
            }

            @Override
            @OnThread(Tag.Any)
            public void simulationChangedAsync(AsyncEvent e)
            {
                if (e == AsyncEvent.STOPPED || e == AsyncEvent.DISABLED) {
                    running = false;
                }
            }
        });
        try {
            sim.setSpeed(Integer.parseInt(props.getProperty("simulation.speed", "50").trim()));
        }
        catch (NumberFormatException e) {
            // keep default
        }
    }

    /**
     * Create a session for the scenario in the given class loader.
     *
     * @param loader  Loader with the scenario classes and resources at its root.
     * @param saveDir Where Save/UserInfo files go (created on demand), or null.
     * @param sink    Notified on the simulation thread when a frame is ready (may be null).
     */
    public static PlayerSession create(ClassLoader loader, File saveDir, WorldHandlerDelegatePlayer.FrameSink sink)
            throws IOException, ClassNotFoundException, NoSuchMethodException
    {
        Properties props = loadProperties(loader);
        return new PlayerSession(loader, props, saveDir, sink);
    }

    /** Same, with explicit properties (tests, tools). */
    public static PlayerSession create(ClassLoader loader, Properties props, File saveDir,
                                       WorldHandlerDelegatePlayer.FrameSink sink)
            throws ClassNotFoundException, NoSuchMethodException
    {
        return new PlayerSession(loader, props, saveDir, sink);
    }

    /** Read project.greenfoot, then standalone.properties on top, from the loader. */
    public static Properties loadProperties(ClassLoader loader) throws IOException
    {
        Properties p = new Properties();
        for (String name : new String[] {PROJECT_PROPERTIES, STANDALONE_PROPERTIES}) {
            try (InputStream in = loader.getResourceAsStream(name)) {
                if (in != null) {
                    p.load(in);
                }
            }
        }
        return p;
    }

    public Properties getProperties()
    {
        return props;
    }

    public String getWorldClassName()
    {
        return worldClassName;
    }

    public WorldHandlerDelegatePlayer getDelegate()
    {
        return delegate;
    }

    /** Title for the window: the scenario name, or the world class name. */
    public String getTitle()
    {
        String t = props.getProperty("project.name");
        if (t == null || t.isEmpty()) {
            t = props.getProperty("publish.title");
        }
        return t == null || t.isEmpty() ? worldClassName : t;
    }

    public boolean isLocked()
    {
        return Boolean.parseBoolean(props.getProperty("scenario.lock", "false"));
    }

    public boolean isControlsHidden()
    {
        return Boolean.parseBoolean(props.getProperty("scenario.hideControls", "false"));
    }

    /** Set how Greenfoot.ask() gets its answer (a dialog in the UI; null answers "" ). */
    public void setAsker(Function<String, String> asker)
    {
        this.asker = asker;
    }

    String ask(String prompt)
    {
        Function<String, String> a = asker;
        return a == null ? "" : a.apply(prompt);
    }

    /** Construct a fresh instance of the main world (called by the delegate on the simulation thread). */
    @OnThread(Tag.Simulation)
    World instantiateWorld()
    {
        try {
            return (World) Simulation.newInstance(worldConstructor);
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.err.println("Error creating world " + worldClassName + ":");
            cause.printStackTrace();
        }
        catch (InstantiationException | IllegalAccessException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ---- control ----

    /** Create the first world (paused). Call once after construction. */
    public void start()
    {
        Simulation.getInstance().setPaused(true);
        WorldHandler.getInstance().instantiateNewWorld(worldClassName);
    }

    /** Start the first world and immediately run it. */
    public void startRunning()
    {
        start();
        Simulation.getInstance().setPaused(false);
    }

    public void act()
    {
        Simulation.getInstance().runOnce();
    }

    public void runPause()
    {
        Simulation.getInstance().togglePaused();
    }

    public void run()
    {
        Simulation.getInstance().setPaused(false);
    }

    public void pause()
    {
        Simulation.getInstance().setPaused(true);
    }

    public boolean isRunning()
    {
        return running;
    }

    public void reset()
    {
        Simulation.getInstance().setEnabled(false);
        WorldHandler.getInstance().discardWorld();
        Simulation.getInstance().setEnabled(true);
        Simulation.getInstance().setPaused(true);
        WorldHandler.getInstance().instantiateNewWorld(worldClassName);
    }

    public void setSpeed(int speed)
    {
        Simulation.getInstance().setSpeed(speed);
    }

    public int getSpeed()
    {
        return Simulation.getInstance().getSpeed();
    }

    /** Stop the simulation and all sound, e.g. when the window closes. */
    public void shutdown()
    {
        Simulation.getInstance().setEnabled(false);
        SoundMixer.getInstance().shutdown();
        greenfoot.Save.flush();
    }

    public ClassLoader getLoader()
    {
        return loader;
    }
}
