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

import greenfoot.guifx.superide.folders.ClassFolders;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the logic behind the SuperGreenfoot IDE window: world scaling and what
 * the Classes panel lists in its Folders and Inheritance views.
 */
public class SuperIdeShellTest extends TestCase
{
    private static List<ClassEntry> classes()
    {
        List<ClassEntry> list = new ArrayList<>();
        list.add(new ClassEntry("Level2", "MyWorld", null));
        list.add(new ClassEntry("MyWorld", "greenfoot.World", null));
        list.add(new ClassEntry("Level1", "MyWorld", null));
        list.add(new ClassEntry("Slime", "Enemy", null));
        list.add(new ClassEntry("Enemy", "Actor", null));
        list.add(new ClassEntry("Bat", "Enemy", null));
        list.add(new ClassEntry("Player", "greenfoot.Actor", null));
        list.add(new ClassEntry("Utility", null, null));
        list.add(new ClassEntry("Helper", "Utility", null));
        list.add(new ClassEntry("Stack", "java.util.ArrayList", null));
        return list;
    }

    public void testFitScale()
    {
        // The mockup's default layout: 822 x 536 available for a 960 x 540 world.
        assertEquals(822.0 / 960, WorldHost.computeScale(WorldHost.Zoom.FIT, 822, 536, 960, 540), 1e-9);
        // Height-limited:
        assertEquals(700.0 / 540, WorldHost.computeScale(WorldHost.Zoom.FIT, 1302, 700, 960, 540), 1e-9);
    }

    public void testPixelPerfectScale()
    {
        // Room for 2.4x: pixel-perfect rounds down to 2x.
        assertEquals(2.0, WorldHost.computeScale(WorldHost.Zoom.PIXEL_PERFECT, 1536, 900, 640, 360), 1e-9);
        // A world larger than the space still fits (smoothly) rather than overflowing.
        assertEquals(0.5, WorldHost.computeScale(WorldHost.Zoom.PIXEL_PERFECT, 480, 400, 960, 540), 1e-9);
        // Exactly one.
        assertEquals(1.0, WorldHost.computeScale(WorldHost.Zoom.PIXEL_PERFECT, 960, 540, 960, 540), 1e-9);
    }

    public void testDegenerateScale()
    {
        assertEquals(1.0, WorldHost.computeScale(WorldHost.Zoom.FIT, 0, 500, 960, 540), 1e-9);
        assertEquals(1.0, WorldHost.computeScale(WorldHost.Zoom.FIT, 500, 500, 0, 540), 1e-9);
    }

    public void testWholeScale()
    {
        assertTrue(WorldHost.isWholeScale(1.0));
        assertTrue(WorldHost.isWholeScale(2.0004));
        assertFalse(WorldHost.isWholeScale(1.5));
        assertFalse(WorldHost.isWholeScale(0.856));
        // Below one is always smoothed.
        assertFalse(WorldHost.isWholeScale(0.0));
    }

    public void testInheritanceGroups()
    {
        List<ClassTreeModel.InheritanceGroup> groups = ClassTreeModel.inheritanceGroups(classes());
        assertEquals(3, groups.size());

        ClassTreeModel.InheritanceGroup world = groups.get(0);
        assertEquals("World", world.base);
        assertEquals(List.of("MyWorld:1", "Level1:2", "Level2:2"), describe(world));

        ClassTreeModel.InheritanceGroup actor = groups.get(1);
        assertEquals("Actor", actor.base);
        // The built-in SuperWindow sits in name order among Actor's subclasses.
        assertEquals(List.of("Enemy:1", "Bat:2", "Slime:2", "Player:1", "(SuperWindow):1"), describe(actor));

        ClassTreeModel.InheritanceGroup other = groups.get(2);
        assertNull(other.base);
        // A superclass outside the scenario (ArrayList) makes the class a root of "other".
        assertEquals(List.of("Stack:0", "Utility:0", "Helper:1"), describe(other));
    }

    public void testInheritanceCycleIsListedNotLost()
    {
        List<ClassEntry> list = new ArrayList<>();
        list.add(new ClassEntry("A", "B", null));
        list.add(new ClassEntry("B", "A", null));
        List<ClassTreeModel.InheritanceGroup> groups = ClassTreeModel.inheritanceGroups(list);
        assertEquals(List.of("A:0", "B:0"), describe(groups.get(2)));
    }

    public void testSuperWindowInTheActorGroup()
    {
        // Subclasses of the built-in SuperWindow sit under its row, one deeper.
        List<ClassEntry> list = new ArrayList<>(classes());
        list.add(new ClassEntry("Inventory", "greenfoot.SuperWindow", null));
        list.add(new ClassEntry("Chest", "Inventory", null));
        list.add(new ClassEntry("Zombie", "Actor", null));
        ClassTreeModel.InheritanceGroup actor = ClassTreeModel.inheritanceGroups(list).get(1);
        assertEquals(List.of("Enemy:1", "Bat:2", "Slime:2", "Player:1", "(SuperWindow):1", "Inventory:2",
                "Chest:3", "Zombie:1"), describe(actor));

        // A scenario's own SuperWindow (MrCohenLibrary's) replaces the built-in row, and its
        // subclasses follow it wherever it sits.
        list = new ArrayList<>(classes());
        list.add(new ClassEntry("SuperWindow", "Actor", null));
        list.add(new ClassEntry("Inventory", "SuperWindow", null));
        actor = ClassTreeModel.inheritanceGroups(list).get(1);
        assertEquals(List.of("Enemy:1", "Bat:2", "Slime:2", "Player:1", "SuperWindow:1", "Inventory:2"),
                describe(actor));
    }

    public void testFolderGroupsAndUnfiled()
    {
        ClassFolders folders = new ClassFolders();
        folders.addFolder("Worlds");
        folders.addFolder("Characters");
        folders.addFolder("Empty");
        folders.setFolder("Level1", "Worlds");
        folders.setFolder("MyWorld", "Worlds");
        folders.setFolder("Player", "Characters");
        folders.setFolder("Slime", "Characters");
        folders.setOpen("Characters", false);

        List<ClassTreeModel.FolderGroup> groups = ClassTreeModel.folderGroups(folders, classes());
        assertEquals(3, groups.size());
        assertEquals("Worlds", groups.get(0).name);
        assertTrue(groups.get(0).open);
        assertEquals(List.of("Level1", "MyWorld"), names(groups.get(0).classes));
        assertEquals("Characters", groups.get(1).name);
        assertFalse(groups.get(1).open);
        assertEquals(List.of("Player", "Slime"), names(groups.get(1).classes));
        assertTrue(groups.get(2).classes.isEmpty());

        assertEquals(List.of("Bat", "Enemy", "Helper", "Level2", "Stack", "Utility"),
                names(ClassTreeModel.unfiled(folders, classes())));
    }

    public void testInheritanceInsideAFolder()
    {
        ClassFolders folders = new ClassFolders();
        folders.addFolder("Characters");
        folders.addFolder("Enemies");
        for (String name : List.of("Player", "Enemy", "Slime", "Bat"))
        {
            folders.setFolder(name, "Characters");
        }
        List<ClassTreeModel.FolderGroup> groups = ClassTreeModel.folderGroups(folders, classes());
        // Folder rows start one level in; subclasses follow their superclass, one deeper.
        assertEquals(List.of("Enemy:1", "Bat:2", "Slime:2", "Player:1"),
                describe(ClassTreeModel.nested(groups.get(0).classes, 1)));

        // A superclass in another folder leaves its subclasses as roots here.
        folders.setFolder("Enemy", "Enemies");
        groups = ClassTreeModel.folderGroups(folders, classes());
        assertEquals(List.of("Bat:1", "Player:1", "Slime:1"), describe(ClassTreeModel.nested(groups.get(0).classes, 1)));
        assertEquals(List.of("Enemy:1"), describe(ClassTreeModel.nested(groups.get(1).classes, 1)));

        // The unfiled classes nest the same way, from depth 0.
        assertEquals(List.of("MyWorld:0", "Level1:1", "Level2:1", "Stack:0", "Utility:0", "Helper:1"),
                describe(ClassTreeModel.nested(ClassTreeModel.unfiled(folders, classes()), 0)));

        // A superclass cycle is listed flat, not lost.
        List<ClassEntry> cycle = List.of(new ClassEntry("A", "B", null), new ClassEntry("B", "A", null));
        assertEquals(List.of("A:1", "B:1"), describe(ClassTreeModel.nested(cycle, 1)));
    }

    public void testNestedSubclassesMoveWithTheirSuperclass()
    {
        ClassFolders folders = new ClassFolders();
        folders.addFolder("Characters");
        folders.addFolder("Worlds");
        for (String name : List.of("Enemy", "Bat", "Slime", "Player"))
        {
            folders.setFolder(name, "Characters");
        }
        assertEquals(List.of("Bat", "Slime"), ClassTreeModel.nestedSubclasses("Enemy", folders, classes()));
        assertEquals(List.of(), ClassTreeModel.nestedSubclasses("Player", folders, classes()));

        // A subclass in another folder isn't drawn under it, so it doesn't go with it.
        folders.setFolder("Slime", "Worlds");
        assertEquals(List.of("Bat"), ClassTreeModel.nestedSubclasses("Enemy", folders, classes()));

        // Unfiled classes nest (and move) the same way.
        assertEquals(List.of("Level1", "Level2"), ClassTreeModel.nestedSubclasses("MyWorld", folders, classes()));
    }

    public void testClassHelpers()
    {
        List<ClassEntry> list = classes();
        assertTrue(ClassTreeModel.isWorldClass("Level2", list));
        assertFalse(ClassTreeModel.isWorldClass("Player", list));
        assertTrue(ClassTreeModel.isActorClass("Slime", list));
        assertFalse(ClassTreeModel.isActorClass("Helper", list));
        assertEquals(List.of("Bat", "Slime"), ClassTreeModel.subclassesOf("Enemy", list));
        assertEquals("World", ClassTreeModel.simple("greenfoot.World"));
        assertNull(ClassTreeModel.simple(null));
        assertEquals("L1", ClassTreeModel.initials("Level1"));
        assertEquals("Sb", ClassTreeModel.initials("ScoreBar"));
        assertEquals("P", ClassTreeModel.initials("Player"));
        assertEquals("?", ClassTreeModel.initials(""));
    }

    private static List<String> describe(ClassTreeModel.InheritanceGroup group)
    {
        return describe(group.rows);
    }

    private static List<String> describe(List<ClassTreeModel.InheritanceRow> rows)
    {
        List<String> result = new ArrayList<>();
        for (ClassTreeModel.InheritanceRow row : rows)
        {
            result.add((row.builtIn != null ? "(" + row.builtIn + ")" : row.entry.getName()) + ":" + row.depth);
        }
        return result;
    }

    private static List<String> names(List<ClassEntry> entries)
    {
        List<String> result = new ArrayList<>();
        for (ClassEntry e : entries)
        {
            result.add(e.getName());
        }
        return result;
    }
}
