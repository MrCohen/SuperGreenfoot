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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Keeps the new IDE's main menus and a docked editor's menus from fighting over
 * keyboard shortcuts.  While an editor tab is selected, main-menu items whose
 * shortcut the editor also uses (Save, Find, Replace, select all, ...) lose their
 * shortcut, so the key goes to the editor; {@link #restore} gives them back when the
 * World tab is selected again.
 */
@OnThread(Tag.FXPlatform)
public final class MenuMerge
{
    /** Where an item's own shortcut is kept while it is taken away. */
    private static final String SAVED_ACCELERATOR = "supergreenfoot.savedAccelerator";

    /**
     * Shortcuts the editors handle themselves, without a menu item (so they are not
     * found in the editor's menus): select all, and the clipboard and undo keys.
     */
    public static final List<KeyCombination> EDITOR_KEYS = Collections.unmodifiableList(Arrays.asList(
            new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN),
            new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN),
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN),
            new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN),
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN)));

    private MenuMerge()
    {
    }

    /**
     * Take away the shortcut of every item in the main menus that the editor's menus,
     * or the given editor keys, also use.  Items keep their own shortcut to restore later.
     * Any shortcuts taken away earlier are restored first, so this can be called again
     * when the editor tab changes.
     *
     * @return the number of shortcuts taken away
     */
    public static int removeClashes(List<Menu> mainMenus, List<Menu> editorMenus, List<KeyCombination> editorKeys)
    {
        restore(mainMenus);
        Set<String> taken = new HashSet<>();
        for (MenuItem item : allItems(editorMenus))
        {
            if (item.getAccelerator() != null)
            {
                taken.add(normalise(item.getAccelerator()));
            }
        }
        for (KeyCombination key : editorKeys)
        {
            taken.add(normalise(key));
        }
        int removed = 0;
        for (MenuItem item : allItems(mainMenus))
        {
            KeyCombination key = item.getAccelerator();
            if (key != null && taken.contains(normalise(key)))
            {
                item.getProperties().put(SAVED_ACCELERATOR, key);
                item.setAccelerator(null);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Give back every shortcut that {@link #removeClashes} took away from the main menus.
     */
    public static void restore(List<Menu> mainMenus)
    {
        for (MenuItem item : allItems(mainMenus))
        {
            Object saved = item.getProperties().remove(SAVED_ACCELERATOR);
            if (saved instanceof KeyCombination)
            {
                item.setAccelerator((KeyCombination) saved);
            }
        }
    }

    /** Every item in the menus, including the items of submenus. */
    private static List<MenuItem> allItems(List<Menu> menus)
    {
        List<MenuItem> items = new ArrayList<>();
        for (Menu menu : menus)
        {
            addItems(menu.getItems(), items);
        }
        return items;
    }

    private static void addItems(List<MenuItem> from, List<MenuItem> into)
    {
        for (MenuItem item : from)
        {
            into.add(item);
            if (item instanceof Menu)
            {
                addItems(((Menu) item).getItems(), into);
            }
        }
    }

    /**
     * A key combination as the keys actually pressed, so that "Shortcut+S" and
     * "Meta+S" (on macOS) or "Ctrl+S" (elsewhere) count as the same.
     */
    static String normalise(KeyCombination key)
    {
        if (key instanceof KeyCodeCombination)
        {
            KeyCodeCombination k = (KeyCodeCombination) key;
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
            boolean shortcut = k.getShortcut() == KeyCombination.ModifierValue.DOWN;
            boolean meta = k.getMeta() == KeyCombination.ModifierValue.DOWN || (mac && shortcut);
            boolean control = k.getControl() == KeyCombination.ModifierValue.DOWN || (!mac && shortcut);
            return k.getCode() + (k.getShift() == KeyCombination.ModifierValue.DOWN ? "+shift" : "")
                    + (control ? "+control" : "") + (k.getAlt() == KeyCombination.ModifierValue.DOWN ? "+alt" : "")
                    + (meta ? "+meta" : "");
        }
        return key.toString();
    }
}
