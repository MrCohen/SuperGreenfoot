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
import bluej.prefmgr.PrefMgr;
import bluej.utility.Debug;
import bluej.utility.javafx.JavaFXUtil;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Window;
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
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The SuperGreenfoot IDE's look: AtlantaFX's Primer theme as the application's
 * user-agent stylesheet, our own stylesheet (lib/stylesheets/superide.css) on
 * top for the approved palette, and the bundled IBM Plex fonts.
 *
 * <p>The user-agent stylesheet is application-wide, so it is only set while the
 * SuperGreenfoot IDE is the active UI ({@link #activate()}), and cleared again
 * for the Classic IDE ({@link #deactivate()}), which then looks exactly as
 * before.
 *
 * <p>While active, every window gets the palette, including the windows the new
 * IDE shares with BlueJ (editors, terminal, debugger, inspectors, popups): they
 * also get lib/stylesheets/superide-shared.css, which restyles BlueJ's own
 * style classes for light and dark, and the Java editor and terminal use the
 * bundled code font. {@link #deactivate()} takes all of that off again, so
 * windows left open across a switch to Classic look as they always did.
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
    /** Relative to the lib directory: BlueJ's shared windows (editor, terminal, ...) in the new IDE's look. */
    public static final String SHARED_STYLESHEET = "stylesheets/superide-shared.css";
    /** Relative to the lib directory. */
    public static final String FONT_DIR = "superide/fonts";

    private static final String LIGHT_CLASS = "sg-light";
    private static final String DARK_CLASS = "sg-dark";
    /** On every themed root, light or dark; superide-shared.css keys its rules on it. */
    private static final String THEME_CLASS = "sg-theme";
    /** On the root of a see-through window (e.g. BlueJ's rounded object inspector), which must keep no background. */
    private static final String TRANSPARENT_CLASS = "sg-transparent-root";

    private static final BooleanProperty dark = new SimpleBooleanProperty(false);
    private static final List<WeakReference<Scene>> scenes = new ArrayList<>();
    /** Nodes that hold docked editors, which act as a scene root for the editors' stylesheets */
    private static final List<WeakReference<Parent>> editorHosts = new ArrayList<>();

    // Scenes whose root property we already watch (so re-installing adds no second listener):
    private static final Map<Scene, Boolean> watchedScenes = new WeakHashMap<>();
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
            forEachEditorHost(SuperTheme::updatePaletteClass);
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
        PrefMgr.setEditorFontFamilyOverride(MONO);
        Window.getWindows().removeListener(windowListener);
        Window.getWindows().addListener(windowListener);
        for (Window window : new ArrayList<>(Window.getWindows()))
        {
            adopt(window);
        }
    }

    /**
     * Go back to JavaFX's default look, as the Classic IDE expects: windows that
     * stay open (editors, the terminal, ...) lose our stylesheets and palette.
     */
    public static void deactivate()
    {
        active = false;
        Window.getWindows().removeListener(windowListener);
        Application.setUserAgentStylesheet(null);
        PrefMgr.setEditorFontFamilyOverride(null);
        forEachScene(SuperTheme::uninstall);
        scenes.clear();
    }

    /**
     * While the new IDE is active, every window that appears (an editor, the
     * terminal, a dialog, a popup) gets our stylesheets and the palette class.
     */
    private static final ListChangeListener<Window> windowListener = new ListChangeListener<Window>()
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void onChanged(Change<? extends Window> change)
        {
            while (change.next())
            {
                for (Window window : change.getAddedSubList())
                {
                    adopt(window);
                }
            }
        }
    };

    private static void adopt(Window window)
    {
        Scene scene = window.getScene();
        if (!active || scene == null || libDir == null || isTracked(scene))
        {
            return;
        }
        install(scene);
    }

    private static boolean isTracked(Scene scene)
    {
        for (WeakReference<Scene> ref : scenes)
        {
            if (ref.get() == scene)
            {
                return true;
            }
        }
        return false;
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
     * Give a scene the SuperGreenfoot stylesheets and palette class; the scene
     * follows later light/dark switches.
     */
    public static void install(Scene scene)
    {
        String url = stylesheetURL();
        String sharedUrl = sharedStylesheetURL();
        if (url != null && !scene.getStylesheets().contains(url))
        {
            scene.getStylesheets().add(url);
        }
        // Last, so it wins over BlueJ's own sheets of the same specificity:
        if (sharedUrl != null)
        {
            scene.getStylesheets().remove(sharedUrl);
            scene.getStylesheets().add(sharedUrl);
        }
        if (!isTracked(scene))
        {
            scenes.add(new WeakReference<>(scene));
        }
        updateRoot(scene);
        if (!watchedScenes.containsKey(scene))
        {
            watchedScenes.put(scene, Boolean.TRUE);
            JavaFXUtil.addChangeListenerPlatform(scene.rootProperty(), newRoot -> {
                if (isTracked(scene))
                {
                    updateRoot(scene);
                }
            });
        }
    }

    /** Take our stylesheets and palette class off a scene again. */
    private static void uninstall(Scene scene)
    {
        scene.getStylesheets().removeAll(stylesheetURL(), sharedStylesheetURL());
        Parent root = scene.getRoot();
        if (root != null)
        {
            root.getStyleClass().removeAll(LIGHT_CLASS, DARK_CLASS, THEME_CLASS, TRANSPARENT_CLASS);
            root.getStylesheets().remove(sharedStylesheetURL());
        }
    }

    /** Relative to the lib directory: the look of editors docked in the new IDE. */
    public static final String EDITOR_HOST_STYLESHEET = "stylesheets/superide-host.css";

    /**
     * Prepare the node that holds the new IDE's docked editors (and its World tab).
     * The editors' stylesheets are on that node rather than on a scene, and they define
     * their colours on ".root", so the node gets the root style class (and, like a scene
     * root, the light or dark palette class).  It also gets our look for the tab strip,
     * which must come after the editor stylesheets on that node.
     */
    public static void installEditorHost(Parent host)
    {
        if (!host.getStyleClass().contains("root"))
        {
            host.getStyleClass().add("root");
        }
        if (!host.getStyleClass().contains(THEME_CLASS))
        {
            host.getStyleClass().add(THEME_CLASS);
        }
        editorHosts.add(new WeakReference<>(host));
        updatePaletteClass(host);
        if (libDir == null)
        {
            Debug.reportError("SuperTheme used before init(libDir)");
            return;
        }
        // The editors' stylesheets on this node outrank the scene's, so the shared
        // light/dark sheet must sit on the node too, after them:
        String sharedUrl = sharedStylesheetURL();
        if (sharedUrl != null)
        {
            host.getStylesheets().remove(sharedUrl);
            host.getStylesheets().add(sharedUrl);
        }
        String url = new File(libDir, EDITOR_HOST_STYLESHEET).toURI().toString();
        host.getStylesheets().remove(url);
        host.getStylesheets().add(url);
    }

    private static void updatePaletteClass(Parent node)
    {
        node.getStyleClass().removeAll(LIGHT_CLASS, DARK_CLASS);
        node.getStyleClass().add(dark.get() ? DARK_CLASS : LIGHT_CLASS);
    }

    private static void forEachEditorHost(java.util.function.Consumer<Parent> action)
    {
        for (Iterator<WeakReference<Parent>> it = editorHosts.iterator(); it.hasNext(); )
        {
            Parent host = it.next().get();
            if (host == null)
            {
                it.remove();
            }
            else
            {
                action.accept(host);
            }
        }
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
        if (!root.getStyleClass().contains(THEME_CLASS))
        {
            root.getStyleClass().add(THEME_CLASS);
        }
        boolean seeThrough = scene.getFill() == null || Color.TRANSPARENT.equals(scene.getFill());
        if (seeThrough && !root.getStyleClass().contains(TRANSPARENT_CLASS))
        {
            root.getStyleClass().add(TRANSPARENT_CLASS);
        }
        // A root with stylesheets of its own (BlueJ's popups add theirs there)
        // outranks the scene's, so our shared sheet goes on the root too:
        String sharedUrl = sharedStylesheetURL();
        if (sharedUrl != null && !root.getStylesheets().isEmpty() && !root.getStylesheets().contains(sharedUrl))
        {
            root.getStylesheets().add(sharedUrl);
        }
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

    private static String sharedStylesheetURL()
    {
        if (libDir == null)
        {
            return null;
        }
        return new File(libDir, SHARED_STYLESHEET).toURI().toString();
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
