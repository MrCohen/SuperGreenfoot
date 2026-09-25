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
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out what the class list shows: the classes in each virtual folder, the
 * unfiled ones, and the Classic-style inheritance groups (World classes, Actor
 * classes, other classes). No JavaFX nodes, so it can be tested headless.
 */
@OnThread(Tag.FXPlatform)
public final class ClassTreeModel
{
    public static final String WORLD = "World";
    public static final String ACTOR = "Actor";
    /** A built-in Actor subclass, listed in the Actor group like Classic's diagram does. */
    public static final String SUPER_WINDOW = "SuperWindow";

    private ClassTreeModel()
    {
    }

    /** A folder and the classes in it, in display order. */
    @OnThread(Tag.Any)
    public static final class FolderGroup
    {
        public final String name;
        public final boolean open;
        public final List<ClassEntry> classes;

        FolderGroup(String name, boolean open, List<ClassEntry> classes)
        {
            this.name = name;
            this.open = open;
            this.classes = classes;
        }
    }

    /**
     * A class in the inheritance view (or in a folder showing inheritance),
     * indented by its depth below the group's base.
     */
    @OnThread(Tag.Any)
    public static final class InheritanceRow
    {
        /** The scenario's class, or null for a built-in class (see {@link #builtIn}). */
        public final ClassEntry entry;
        /** The simple name of a built-in class shown as a row (SuperWindow), else null. */
        public final String builtIn;
        public final int depth;

        InheritanceRow(ClassEntry entry, int depth)
        {
            this.entry = entry;
            this.builtIn = null;
            this.depth = depth;
        }

        InheritanceRow(String builtIn, int depth)
        {
            this.entry = null;
            this.builtIn = builtIn;
            this.depth = depth;
        }

        /** The class's name, whether it is the scenario's or built in. */
        public String getName()
        {
            return entry != null ? entry.getName() : builtIn;
        }
    }

    /** "World classes", "Actor classes" or "Other classes" and their rows. */
    @OnThread(Tag.Any)
    public static final class InheritanceGroup
    {
        public final String label;
        /** "World", "Actor", or null for the other classes. */
        public final String base;
        public final List<InheritanceRow> rows;

        InheritanceGroup(String label, String base, List<InheritanceRow> rows)
        {
            this.label = label;
            this.base = base;
            this.rows = rows;
        }
    }

    /** Folders in display order, each with its classes sorted by name. */
    public static List<FolderGroup> folderGroups(ClassFolders folders, List<ClassEntry> classes)
    {
        Map<String, ClassEntry> byName = byName(classes);
        List<FolderGroup> groups = new ArrayList<>();
        for (String folder : folders.getFolderNames())
        {
            List<ClassEntry> inFolder = new ArrayList<>();
            for (String name : folders.getClassesIn(folder, byName.keySet()))
            {
                inFolder.add(byName.get(name));
            }
            groups.add(new FolderGroup(folder, folders.isOpen(folder), inFolder));
        }
        return groups;
    }

    /** Classes in no folder, sorted by name. */
    public static List<ClassEntry> unfiled(ClassFolders folders, List<ClassEntry> classes)
    {
        Map<String, ClassEntry> byName = byName(classes);
        List<ClassEntry> result = new ArrayList<>();
        for (String name : folders.getUnfiled(byName.keySet()))
        {
            result.add(byName.get(name));
        }
        return result;
    }

    /**
     * The three Classic groups. Within a group, subclasses follow their
     * superclass, and siblings are sorted by name.
     */
    @OnThread(Tag.Any)
    public static List<InheritanceGroup> inheritanceGroups(List<ClassEntry> classes)
    {
        Map<String, ClassEntry> byName = byName(classes);
        Map<String, List<ClassEntry>> children = new HashMap<>();
        List<ClassEntry> worldRoots = new ArrayList<>();
        List<ClassEntry> actorRoots = new ArrayList<>();
        List<ClassEntry> otherRoots = new ArrayList<>();
        // A scenario's own SuperWindow hides the built-in one (Java prefers the package's
        // class), so then there is no built-in row and that class sits wherever it extends.
        boolean builtInWindow = !byName.containsKey(SUPER_WINDOW);
        List<ClassEntry> windowRoots = new ArrayList<>();
        for (ClassEntry entry : classes)
        {
            String sup = entry.getSuperName();
            if (sup != null && byName.containsKey(sup) && !sup.equals(entry.getName()))
            {
                children.computeIfAbsent(sup, k -> new ArrayList<>()).add(entry);
            }
            else if (WORLD.equals(sup))
            {
                worldRoots.add(entry);
            }
            else if (ACTOR.equals(sup))
            {
                actorRoots.add(entry);
            }
            else if (builtInWindow && SUPER_WINDOW.equals(sup))
            {
                windowRoots.add(entry);
            }
            else
            {
                otherRoots.add(entry);
            }
        }
        Comparator<ClassEntry> byNameOrder = Comparator.comparing(ClassEntry::getName);
        for (List<ClassEntry> list : children.values())
        {
            list.sort(byNameOrder);
        }
        Set<String> placed = new HashSet<>();
        List<InheritanceGroup> groups = new ArrayList<>();
        groups.add(new InheritanceGroup("World classes", WORLD, walk(worldRoots, 1, children, placed, byNameOrder)));
        List<InheritanceRow> actorRows = walk(actorRoots, 1, children, placed, byNameOrder);
        if (builtInWindow)
        {
            // The built-in SuperWindow, and its subclasses under it, in name order among
            // Actor's direct subclasses:
            List<InheritanceRow> window = new ArrayList<>();
            window.add(new InheritanceRow(SUPER_WINDOW, 1));
            window.addAll(walk(windowRoots, 2, children, placed, byNameOrder));
            int at = actorRows.size();
            for (int i = 0; i < actorRows.size(); i++)
            {
                if (actorRows.get(i).depth == 1 && actorRows.get(i).getName().compareTo(SUPER_WINDOW) > 0)
                {
                    at = i;
                    break;
                }
            }
            actorRows.addAll(at, window);
        }
        groups.add(new InheritanceGroup("Actor classes", ACTOR, actorRows));
        List<InheritanceRow> other = walk(otherRoots, 0, children, placed, byNameOrder);
        // Classes in a superclass cycle are never reached from a root; list them flat.
        List<ClassEntry> leftover = new ArrayList<>();
        for (ClassEntry entry : classes)
        {
            if (!placed.contains(entry.getName()))
            {
                leftover.add(entry);
            }
        }
        leftover.sort(byNameOrder);
        for (ClassEntry entry : leftover)
        {
            other.add(new InheritanceRow(entry, 0));
        }
        groups.add(new InheritanceGroup("Other classes", null, other));
        return groups;
    }

    /**
     * One folder's classes (or the unfiled ones) as a tree: a class whose
     * superclass is in the same list follows it, one level deeper. A class
     * whose superclass is elsewhere is a root at the given depth. Siblings are
     * sorted by name.
     */
    @OnThread(Tag.Any)
    public static List<InheritanceRow> nested(List<ClassEntry> classes, int depth)
    {
        Map<String, ClassEntry> byName = byName(classes);
        Map<String, List<ClassEntry>> children = new HashMap<>();
        List<ClassEntry> roots = new ArrayList<>();
        for (ClassEntry entry : classes)
        {
            String sup = entry.getSuperName();
            if (sup != null && byName.containsKey(sup) && !sup.equals(entry.getName()))
            {
                children.computeIfAbsent(sup, k -> new ArrayList<>()).add(entry);
            }
            else
            {
                roots.add(entry);
            }
        }
        Comparator<ClassEntry> byNameOrder = Comparator.comparing(ClassEntry::getName);
        for (List<ClassEntry> list : children.values())
        {
            list.sort(byNameOrder);
        }
        Set<String> placed = new HashSet<>();
        List<InheritanceRow> rows = walk(roots, depth, children, placed, byNameOrder);
        // Classes in a superclass cycle are never reached from a root; list them flat.
        List<ClassEntry> leftover = new ArrayList<>();
        for (ClassEntry entry : classes)
        {
            if (!placed.contains(entry.getName()))
            {
                leftover.add(entry);
            }
        }
        leftover.sort(byNameOrder);
        for (ClassEntry entry : leftover)
        {
            rows.add(new InheritanceRow(entry, depth));
        }
        return rows;
    }

    @OnThread(Tag.Any)
    private static List<InheritanceRow> walk(List<ClassEntry> roots, int depth, Map<String, List<ClassEntry>> children,
            Set<String> placed, Comparator<ClassEntry> order)
    {
        List<ClassEntry> sorted = new ArrayList<>(roots);
        sorted.sort(order);
        List<InheritanceRow> rows = new ArrayList<>();
        for (ClassEntry entry : sorted)
        {
            if (!placed.add(entry.getName()))
            {
                continue;
            }
            rows.add(new InheritanceRow(entry, depth));
            rows.addAll(walk(children.getOrDefault(entry.getName(), Collections.emptyList()), depth + 1,
                    children, placed, order));
        }
        return rows;
    }

    /**
     * The classes drawn under a class when its folder shows inheritance: its
     * subclasses in the same folder (or also unfiled), their subclasses in that
     * folder, and so on. Sorted by name.
     */
    public static List<String> nestedSubclasses(String name, ClassFolders folders, List<ClassEntry> classes)
    {
        String folder = folders.getFolder(name);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(name);
        List<String> todo = new ArrayList<>();
        todo.add(name);
        while (!todo.isEmpty())
        {
            String current = todo.remove(todo.size() - 1);
            for (ClassEntry entry : classes)
            {
                if (current.equals(entry.getSuperName()) && folder.equals(folders.getFolder(entry.getName()))
                        && seen.add(entry.getName()))
                {
                    result.add(entry.getName());
                    todo.add(entry.getName());
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    /** Whether the class is (indirectly) a subclass of World. */
    @OnThread(Tag.Any)
    public static boolean isWorldClass(String name, List<ClassEntry> classes)
    {
        return reaches(name, WORLD, classes);
    }

    /** Whether the class is (indirectly) a subclass of Actor. */
    @OnThread(Tag.Any)
    public static boolean isActorClass(String name, List<ClassEntry> classes)
    {
        return reaches(name, ACTOR, classes);
    }

    @OnThread(Tag.Any)
    private static boolean reaches(String name, String base, List<ClassEntry> classes)
    {
        Map<String, ClassEntry> byName = byName(classes);
        Set<String> seen = new HashSet<>();
        String current = name;
        while (current != null && seen.add(current))
        {
            ClassEntry entry = byName.get(current);
            if (entry == null)
            {
                return false;
            }
            String sup = entry.getSuperName();
            if (base.equals(sup))
            {
                return true;
            }
            current = sup;
        }
        return false;
    }

    /** The direct subclasses of a class, sorted. */
    @OnThread(Tag.Any)
    public static List<String> subclassesOf(String name, List<ClassEntry> classes)
    {
        List<String> result = new ArrayList<>();
        for (ClassEntry entry : classes)
        {
            if (name.equals(entry.getSuperName()))
            {
                result.add(entry.getName());
            }
        }
        Collections.sort(result);
        return result;
    }

    /** "greenfoot.World" becomes "World"; null stays null. */
    @OnThread(Tag.Any)
    public static String simple(String className)
    {
        if (className == null)
        {
            return null;
        }
        return className.startsWith("greenfoot.") ? className.substring("greenfoot.".length()) : className;
    }

    /**
     * Up to two characters for a class without an image: the first letter plus
     * the first digit ("L1" for Level1) or the second capital ("SB" for
     * ScoreBar -> "Sb").
     */
    @OnThread(Tag.Any)
    public static String initials(String name)
    {
        if (name == null || name.isEmpty())
        {
            return "?";
        }
        String first = name.substring(0, 1).toUpperCase();
        for (int i = 1; i < name.length(); i++)
        {
            if (Character.isDigit(name.charAt(i)))
            {
                return first + name.charAt(i);
            }
        }
        for (int i = 1; i < name.length(); i++)
        {
            if (Character.isUpperCase(name.charAt(i)))
            {
                return first + Character.toLowerCase(name.charAt(i));
            }
        }
        return first;
    }

    @OnThread(Tag.Any)
    private static Map<String, ClassEntry> byName(List<ClassEntry> classes)
    {
        Map<String, ClassEntry> map = new LinkedHashMap<>();
        for (ClassEntry entry : classes)
        {
            map.put(entry.getName(), entry);
        }
        return map;
    }
}
