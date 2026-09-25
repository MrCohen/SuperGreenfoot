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
package greenfoot.player;

import threadchecker.OnThread;
import threadchecker.Tag;

import greenfoot.net.ServerMode;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Entry point of an exported SuperGreenfoot game, and a command-line runner
 * for scenario folders during development.
 *
 * <pre>
 *   java -jar MyGame.jar                      # exported: classes and resources are in the jar
 *   java -cp supergreenfoot-runtime.jar greenfoot.player.PlayerMain /path/to/scenario
 *                                             # development: compiled classes in the folder
 * </pre>
 *
 * Options: {@code --fullscreen} start full screen, {@code --run} start running
 * (also implied by scenario.hideControls), {@code --headless N} run N act
 * cycles without a window (tests/tools), {@code --no-render} run without
 * drawing the world at all.
 *
 * <p>{@code --server} runs the scenario as a dedicated server: no window,
 * nothing drawn, paced at the scenario's speed, until the world stops itself
 * (or Ctrl-C or a TERM signal, which put the command {@code stop} on the
 * console queue and give the world five seconds to save). Settings come from
 * {@code server.properties} next to the jar (or in the scenario folder), or
 * the file named by {@code --server-properties FILE}; {@code --server-setting key=value}
 * overrides one. The setting {@code server.world} names the world to run
 * (default: the scenario's main world). The scenario reads them through
 * {@code Network.getServerSetting}.</p>
 */
@OnThread(Tag.Any)
public final class PlayerMain
{
    private PlayerMain()
    {
    }

    public static void main(String[] args) throws Exception
    {
        File scenarioDir = null;
        boolean fullScreen = false;
        boolean autoRun = false;
        int headlessCycles = -1;
        boolean noRender = false;
        boolean server = false;
        File serverPropertiesFile = null;
        Properties serverOverrides = new Properties();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--fullscreen")) {
                fullScreen = true;
            }
            else if (a.equals("--no-render")) {
                noRender = true;
            }
            else if (a.equals("--run")) {
                autoRun = true;
            }
            else if (a.equals("--headless") && i + 1 < args.length) {
                headlessCycles = Integer.parseInt(args[++i]);
            }
            else if (a.equals("--server")) {
                server = true;
            }
            else if (a.equals("--server-properties") && i + 1 < args.length) {
                serverPropertiesFile = new File(args[++i]);
            }
            else if (a.equals("--server-setting") && i + 1 < args.length) {
                String kv = args[++i];
                int eq = kv.indexOf('=');
                if (eq > 0) {
                    serverOverrides.setProperty(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
                }
            }
            else if (!a.startsWith("-")) {
                scenarioDir = new File(a);
            }
        }

        ClassLoader loader;
        String saveName;
        if (scenarioDir != null) {
            if (!scenarioDir.isDirectory()) {
                System.err.println("Not a scenario folder: " + scenarioDir);
                System.exit(2);
            }
            loader = scenarioLoader(scenarioDir);
            saveName = scenarioDir.getCanonicalFile().getName();
        }
        else {
            loader = PlayerMain.class.getClassLoader();
            Properties p = PlayerSession.loadProperties(loader);
            saveName = p.getProperty("project.name", "scenario");
        }
        File saveDir = scenarioDir != null ? new File(scenarioDir, "saves") : defaultSaveDir(saveName);

        if (server) {
            runServer(loader, scenarioDir, serverPropertiesFile, serverOverrides);
            return;
        }

        if (headlessCycles >= 0) {
            System.setProperty("java.awt.headless", "true");
            PlayerSession session = PlayerSession.create(loader, saveDir, null);
            // Nobody is watching, so do not draw the world: that is most of what
            // a headless run costs.
            session.setRenderingEnabled(false);
            session.start();
            for (int i = 0; i < headlessCycles; i++) {
                session.act();
                Thread.sleep(5);
            }
            session.shutdown();
            System.exit(0);
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            // default look and feel
        }
        System.setProperty("apple.awt.application.name", saveName);

        PlayerSession session = PlayerSession.create(loader, saveDir, null);
        if (noRender) {
            // A window still opens, but stays empty: for timing the simulation on
            // its own, and for a dedicated run that only serves other players.
            session.setRenderingEnabled(false);
        }
        boolean run = autoRun || session.isControlsHidden();
        boolean fs = fullScreen;
        SwingUtilities.invokeLater(() -> {
            PlayerFrame frame = new PlayerFrame(session);
            frame.show();
            session.start();
            if (fs) {
                frame.setFullScreen(true);
            }
            if (run) {
                session.run();
            }
        });
    }

    /**
     * A dedicated server: no window, nothing drawn, paced at the scenario's
     * speed, until the world stops itself. Ctrl-C or a TERM signal queues the
     * console command "stop" and gives the world five seconds to save and stop.
     */
    private static void runServer(ClassLoader loader, File scenarioDir, File propertiesFile,
                                  Properties overrides) throws Exception
    {
        System.setProperty("java.awt.headless", "true");
        File home = scenarioDir != null ? scenarioDir : jarDirectory();
        if (propertiesFile == null) {
            propertiesFile = new File(home, "server.properties");
        }
        Properties settings = new Properties();
        if (propertiesFile.isFile()) {
            try (InputStream in = new FileInputStream(propertiesFile)) {
                settings.load(in);
            }
            System.out.println("Server settings: " + propertiesFile);
        }
        else {
            System.out.println("No " + propertiesFile + ": using the scenario's own defaults");
        }
        settings.putAll(overrides);
        ServerMode.enable(settings);
        ServerMode.startConsole();

        Properties props = PlayerSession.loadProperties(loader);
        String world = settings.getProperty("server.world");
        if (world != null && !world.isEmpty()) {
            props.setProperty("main.class", world);
        }
        PlayerSession session = PlayerSession.create(loader, props, new File(home, "saves"), null);
        session.setRenderingEnabled(false);
        System.out.println("SuperGreenfoot dedicated server: " + session.getTitle() + ", world "
                + session.getWorldClassName() + ", speed " + session.getSpeed() + ". Type stop to stop.");
        session.startRunning();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Ctrl-C or TERM: the world gets the console command "stop" and a
            // few seconds to save and stop itself. The JVM halts as soon as
            // this hook returns, so this is the last word.
            ServerMode.pushCommand("stop");
            long started = System.currentTimeMillis();
            while (session.isRunning() && System.currentTimeMillis() - started < 5000) {
                try {
                    Thread.sleep(50);
                }
                catch (InterruptedException e) {
                    break;
                }
            }
            greenfoot.Save.flush();
            // Tell the players "server stopped" before the JVM halts.
            greenfoot.Network.closeAll();
            greenfoot.net.Link.awaitAllClosed(2000);
            System.out.println(session.isRunning()
                    ? "SuperGreenfoot dedicated server: the world did not stop within 5 seconds; stopping anyway."
                    : "SuperGreenfoot dedicated server: stopped.");
        }, "SuperGreenfoot-server-shutdown"));

        long startBy = System.currentTimeMillis() + 30000;
        while (!session.isRunning() && System.currentTimeMillis() < startBy) {
            Thread.sleep(50);
        }
        if (!session.isRunning()) {
            System.err.println("The world did not start.");
            System.exit(1);
        }
        while (session.isRunning()) {
            Thread.sleep(100);
        }
        System.out.println("SuperGreenfoot dedicated server: the world has stopped.");
        session.shutdown();
        // The network threads are daemons: wait for the close frames to go out,
        // so players see "server stopped" rather than "connection lost".
        greenfoot.Network.closeAll();
        greenfoot.net.Link.awaitAllClosed(2000);
        System.exit(0);
    }

    /** The folder the running jar is in, or the working directory when not run from a jar. */
    private static File jarDirectory()
    {
        try {
            File jar = new File(PlayerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile()) {
                return jar.getParentFile();
            }
        }
        catch (Exception e) {
            // fall through
        }
        return new File(".").getAbsoluteFile();
    }

    /** A class loader over a scenario folder (classes + images/ + sounds/) and any +libs jars. */
    public static ClassLoader scenarioLoader(File dir) throws Exception
    {
        List<URL> urls = new ArrayList<URL>();
        urls.add(dir.toURI().toURL());
        File libs = new File(dir, "+libs");
        File[] jars = libs.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File j : jars) {
                urls.add(j.toURI().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), PlayerMain.class.getClassLoader());
    }

    /** Per-user application data folder for an exported game's saves. */
    public static File defaultSaveDir(String scenarioName)
    {
        String safe = scenarioName.replaceAll("[^A-Za-z0-9_.-]", "_");
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        File base;
        if (os.contains("mac")) {
            base = new File(home, "Library/Application Support/SuperGreenfoot");
        }
        else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = new File(appData != null ? appData : home, "SuperGreenfoot");
        }
        else {
            String xdg = System.getenv("XDG_DATA_HOME");
            base = new File(xdg != null ? xdg : home + "/.local/share", "SuperGreenfoot");
        }
        return new File(base, safe);
    }
}
