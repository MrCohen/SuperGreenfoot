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

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;
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
 * cycles without a window (tests/tools).
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
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--fullscreen")) {
                fullScreen = true;
            }
            else if (a.equals("--run")) {
                autoRun = true;
            }
            else if (a.equals("--headless") && i + 1 < args.length) {
                headlessCycles = Integer.parseInt(args[++i]);
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

        if (headlessCycles >= 0) {
            System.setProperty("java.awt.headless", "true");
            PlayerSession session = PlayerSession.create(loader, saveDir, null);
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
