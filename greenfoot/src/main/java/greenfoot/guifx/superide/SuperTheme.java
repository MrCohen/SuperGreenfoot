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
package greenfoot.guifx.superide;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import bluej.utility.Debug;
import bluej.utility.javafx.JavaFXUtil;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The SuperGreenfoot IDE's look: AtlantaFX's Primer theme as the application's
 * user-agent stylesheet, our own stylesheet (lib/stylesheets/superide.css) on
 * top for the approved palette, and the bundled IBM Plex fonts.
 *
 * <p>The user-agent stylesheet is application-wide, so it is only set while the
 * SuperGreenfoot IDE is the active UI ({@link #activate()}), and cleared again
 * for the Classic IDE ({@link #deactivate()}), which then looks exactly as
 * before.
 */
@OnThread(Tag.FXPlatform)
public final class SuperTheme
{
    /** Font families as JavaFX registers the bundled files. */
    public static final String SANS = "IBM Plex Sans";
    public static final String SANS_MEDIUM = "IBM Plex Sans Medm";
    public static final String SANS_SEMIBOLD = "IBM Plex Sans SmBld";
    public static final String MONO = "IBM Plex Mono";
    public static final String MONO_MEDIUM = "IBM Plex Mono Medm";

    /** Relative to the lib directory. */
    public static final String STYLESHEET = "stylesheets/superide.css";
    /** Relative to the lib directory. */
    public static final String FONT_DIR = "superide/fonts";

    private static final String LIGHT_CLASS = "sg-light";
    private static final String DARK_CLASS = "sg-dark";

    private static final BooleanProperty dark = new SimpleBooleanProperty(false);
    private static final List<WeakReference<Scene>> scenes = new ArrayList<>();
    private static File libDir;
    private static boolean fontsLoaded = false;
    private static boolean active = false;

    static
    {
        JavaFXUtil.addChangeListenerPlatform(dark, isDark -> {
            if (active)
            {
                applyUserAgentStylesheet();
            }
            forEachScene(SuperTheme::updateRoot);
        });
    }

    private SuperTheme()
    {
    }

    /**
     * Tell the theme where the lib directory is (normally Config's BlueJ lib
     * dir) and load the bundled fonts. Safe to call more than once.
     */
    public static void init(File libDirectory)
    {
        libDir = libDirectory;
        loadFonts();
    }

    /** Make the SuperGreenfoot look the application's look. */
    public static void activate()
    {
        active = true;
        applyUserAgentStylesheet();
    }

    /** Go back to JavaFX's default look, as the Classic IDE expects. */
    public static void deactivate()
    {
        active = false;
        Application.setUserAgentStylesheet(null);
    }

    public static boolean isActive()
    {
        return active;
    }

    /** Whether the dark palette is in use (a per-user preference, set by the caller). */
    public static BooleanProperty darkProperty()
    {
        return dark;
    }

    /**
     * Give a scene the SuperGreenfoot stylesheet and palette class; the scene
     * follows later light/dark switches.
     */
    public static void install(Scene scene)
    {
        String url = stylesheetURL();
        if (url != null && !scene.getStylesheets().contains(url))
        {
            scene.getStylesheets().add(url);
        }
        scenes.add(new WeakReference<>(scene));
        updateRoot(scene);
        JavaFXUtil.addChangeListenerPlatform(scene.rootProperty(), newRoot -> updateRoot(scene));
    }

    private static void updateRoot(Scene scene)
    {
        Parent root = scene.getRoot();
        if (root == null)
        {
            return;
        }
        root.getStyleClass().removeAll(LIGHT_CLASS, DARK_CLASS);
        root.getStyleClass().add(dark.get() ? DARK_CLASS : LIGHT_CLASS);
    }

    private interface SceneAction
    {
        void apply(Scene scene);
    }

    private static void forEachScene(SceneAction action)
    {
        for (Iterator<WeakReference<Scene>> it = scenes.iterator(); it.hasNext(); )
        {
            Scene scene = it.next().get();
            if (scene == null)
            {
                it.remove();
            }
            else
            {
                action.apply(scene);
            }
        }
    }

    private static String stylesheetURL()
    {
        if (libDir == null)
        {
            Debug.reportError("SuperTheme used before init(libDir)");
            return null;
        }
        File css = new File(libDir, STYLESHEET);
        return css.toURI().toString();
    }

    private static void applyUserAgentStylesheet()
    {
        Theme theme = dark.get() ? new PrimerDark() : new PrimerLight();
        // A full URL, so it resolves whichever class loader loaded us:
        URL url = theme.getClass().getResource(theme.getUserAgentStylesheet());
        if (url == null)
        {
            Debug.reportError("AtlantaFX stylesheet not found: " + theme.getUserAgentStylesheet());
            return;
        }
        Application.setUserAgentStylesheet(url.toExternalForm());
    }

    private static void loadFonts()
    {
        if (fontsLoaded || libDir == null)
        {
            return;
        }
        fontsLoaded = true;
        File[] files = new File(libDir, FONT_DIR).listFiles();
        if (files == null)
        {
            Debug.reportError("SuperGreenfoot fonts not found in " + new File(libDir, FONT_DIR));
            return;
        }
        for (File file : files)
        {
            if (!file.getName().toLowerCase().endsWith(".ttf"))
            {
                continue;
            }
            try (InputStream in = new FileInputStream(file))
            {
                if (Font.loadFont(in, 13) == null)
                {
                    Debug.reportError("Could not load font " + file);
                }
            }
            // As in Config.loadFXFonts: font loading has been seen to throw errors too.
            catch (Throwable t)
            {
                Debug.reportError("Error loading font " + file, t);
            }
        }
    }
}
