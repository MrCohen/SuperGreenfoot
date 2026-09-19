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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import junit.framework.TestCase;

/**
 * Tests that the new IDE's main menus give way to a docked editor's shortcuts, and get
 * them back afterwards.
 */
public class MenuMergeTest extends TestCase
{
    private static KeyCombination shortcut(KeyCode code)
    {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    private static MenuItem item(String name, KeyCombination key)
    {
        MenuItem item = new MenuItem(name);
        item.setAccelerator(key);
        return item;
    }

    public void testClashesAreRemovedAndRestored()
    {
        MenuItem act = item("Act", shortcut(KeyCode.A));
        MenuItem run = item("Run", shortcut(KeyCode.R));
        MenuItem open = item("Open", shortcut(KeyCode.O));
        MenuItem save = item("Save", shortcut(KeyCode.S));
        Menu recent = new Menu("Recent");
        MenuItem nested = item("Nested", shortcut(KeyCode.F));
        recent.getItems().add(nested);
        List<Menu> main = Arrays.asList(new Menu("Scenario", null, open, save, recent), new Menu("Controls", null, act, run));

        Menu editorEdit = new Menu("Edit", null, item("Replace", shortcut(KeyCode.R)), item("Find", shortcut(KeyCode.F)));
        Menu editorClass = new Menu("Class", null, item("Save", shortcut(KeyCode.S)));
        List<Menu> editor = Arrays.asList(editorClass, editorEdit);

        int removed = MenuMerge.removeClashes(main, editor, MenuMerge.EDITOR_KEYS);
        assertEquals(4, removed);
        assertNull(act.getAccelerator());   // select-all in the editor
        assertNull(run.getAccelerator());   // Replace
        assertNull(save.getAccelerator());  // the editor's Save
        assertNull(nested.getAccelerator());// Find, in a submenu
        assertEquals(shortcut(KeyCode.O), open.getAccelerator());

        // Calling again (another editor tab) does not lose the saved shortcuts:
        MenuMerge.removeClashes(main, Collections.singletonList(editorClass), Collections.emptyList());
        assertEquals(shortcut(KeyCode.A), act.getAccelerator());
        assertNull(save.getAccelerator());

        MenuMerge.restore(main);
        assertEquals(shortcut(KeyCode.A), act.getAccelerator());
        assertEquals(shortcut(KeyCode.R), run.getAccelerator());
        assertEquals(shortcut(KeyCode.S), save.getAccelerator());
        assertEquals(shortcut(KeyCode.F), nested.getAccelerator());
        assertEquals(shortcut(KeyCode.O), open.getAccelerator());
    }

    public void testShortcutMatchesPlatformModifier()
    {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        KeyCombination platform = new KeyCodeCombination(KeyCode.S, mac ? KeyCombination.META_DOWN : KeyCombination.CONTROL_DOWN);
        assertEquals(MenuMerge.normalise(shortcut(KeyCode.S)), MenuMerge.normalise(platform));
        assertFalse(MenuMerge.normalise(shortcut(KeyCode.S)).equals(
                MenuMerge.normalise(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN))));
    }
}
