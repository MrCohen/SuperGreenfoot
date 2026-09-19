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
package greenfoot.guifx.superide.folders;

import bluej.utility.Debug;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The virtual folders that organise a scenario's classes in the SuperGreenfoot
 * IDE's class list.
 *
 * <p>Folders only exist in the IDE: class files stay flat in the scenario
 * folder and in the default package, so the Classic IDE, the compiler and
 * exports are unaffected. Folders are one level deep for now; '/' is not
 * allowed in names so that it can mean nesting later.
 *
 * <p>Classes are identified by simple name (scenario classes are in the default
 * package). A class whose folder no longer exists counts as unfiled, but its
 * entry is kept, and entries for classes that no longer exist are only
 * dropped when a save is given the current class list.
 */
@OnThread(Tag.FXPlatform)
public class ClassFolders
{
    /** The longest folder name allowed. */
    public static final int MAX_NAME_LENGTH = 64;

    private static final String FOLDER_COUNT_KEY = "folders.count";
    private static final String FOLDER_PREFIX = "folders.";
    private static final Pattern FOLDER_KEY = Pattern.compile("folders\\.(count|\\d+\\.(name|open))");
    private static final Pattern CLASS_FOLDER_KEY = Pattern.compile("class\\.([^.]+)\\.folder");

    /** Told whenever folders or class assignments change. */
    @OnThread(Tag.FXPlatform)
    public interface Listener
    {
        void foldersChanged();
    }

    /** Folder name to whether it is expanded, in display order. */
    private final LinkedHashMap<String, Boolean> folders = new LinkedHashMap<>();
    /** Class simple name to folder name as stored (may name a missing folder). */
    private final Map<String, String> classFolder = new TreeMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    /** Empty folders: every class unfiled. */
    public ClassFolders()
    {
    }

    /**
     * Read folders and class assignments from the settings. Invalid or
     * duplicate folder entries are skipped (and reported).
     */
    public static ClassFolders load(ProjectSettingsFile settings)
    {
        ClassFolders result = new ClassFolders();
        int count = Math.max(0, settings.getInt(FOLDER_COUNT_KEY, 0));
        for (int i = 0; i < count; i++)
        {
            String name = settings.get(FOLDER_PREFIX + i + ".name");
            if (name == null)
            {
                continue;
            }
            String trimmed = name.trim();
            String problem = result.checkName(trimmed, null);
            if (problem != null)
            {
                Debug.message("Ignoring folder \"" + name + "\" in " + settings.getFile() + ": " + problem);
                continue;
            }
            boolean open = !"false".equalsIgnoreCase(settings.get(FOLDER_PREFIX + i + ".open", "true").trim());
            result.folders.put(trimmed, open);
        }
        for (String key : settings.getKeys())
        {
            Matcher m = CLASS_FOLDER_KEY.matcher(key);
            if (m.matches())
            {
                String folder = settings.get(key).trim();
                if (!folder.isEmpty())
                {
                    result.classFolder.put(m.group(1), folder);
                }
            }
        }
        return result;
    }

    /**
     * Write the folders and class assignments into the settings, replacing
     * what was there and leaving every other key alone. If currentClasses is
     * not null, entries for classes not in it are dropped; if it is null,
     * every entry is kept.
     */
    public void writeTo(ProjectSettingsFile settings, Collection<String> currentClasses)
    {
        Set<String> known = currentClasses == null ? null : new HashSet<>(currentClasses);
        for (String key : settings.getKeys())
        {
            if (FOLDER_KEY.matcher(key).matches() || CLASS_FOLDER_KEY.matcher(key).matches())
            {
                if (!isStillWritten(key, known))
                {
                    settings.put(key, null);
                }
            }
        }
        settings.put(FOLDER_COUNT_KEY, folders.isEmpty() ? null : Integer.toString(folders.size()));
        int i = 0;
        for (Map.Entry<String, Boolean> folder : folders.entrySet())
        {
            settings.put(FOLDER_PREFIX + i + ".name", folder.getKey());
            settings.put(FOLDER_PREFIX + i + ".open", folder.getValue().toString());
            i++;
        }
        for (Map.Entry<String, String> entry : classFolder.entrySet())
        {
            if (known == null || known.contains(entry.getKey()))
            {
                settings.put("class." + entry.getKey() + ".folder", entry.getValue());
            }
        }
    }

    /**
     * Whether a folder or class-folder key will be rewritten by writeTo, so
     * need not be removed first (removing then re-adding the same value would
     * wrongly mark the file as changed).
     */
    private boolean isStillWritten(String key, Set<String> known)
    {
        if (key.equals(FOLDER_COUNT_KEY))
        {
            return !folders.isEmpty();
        }
        Matcher cm = CLASS_FOLDER_KEY.matcher(key);
        if (cm.matches())
        {
            String cls = cm.group(1);
            return classFolder.containsKey(cls) && (known == null || known.contains(cls));
        }
        // folders.<i>.name / .open
        try
        {
            int index = Integer.parseInt(key.substring(FOLDER_PREFIX.length(), key.indexOf('.', FOLDER_PREFIX.length())));
            return index < folders.size();
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(Listener listener)
    {
        listeners.remove(listener);
    }

    private void fireChanged()
    {
        for (Listener listener : new ArrayList<>(listeners))
        {
            listener.foldersChanged();
        }
    }

    /** The folder names in display order. */
    public List<String> getFolderNames()
    {
        return Collections.unmodifiableList(new ArrayList<>(folders.keySet()));
    }

    /** Whether a folder with this name exists (ignoring case). */
    public boolean hasFolder(String name)
    {
        return findFolder(name) != null;
    }

    /** Whether the folder is expanded in the class list. */
    public boolean isOpen(String folder)
    {
        String actual = findFolder(folder);
        return actual != null && folders.get(actual);
    }

    /** Expand or collapse a folder in the class list. */
    public void setOpen(String folder, boolean open)
    {
        String actual = requireFolder(folder);
        if (folders.get(actual) != open)
        {
            folders.put(actual, open);
            fireChanged();
        }
    }

    /**
     * The folder a class is in, with the folder's own spelling, or "" if the
     * class is unfiled or its folder no longer exists.
     */
    public String getFolder(String className)
    {
        String stored = classFolder.get(className);
        if (stored == null)
        {
            return "";
        }
        String actual = findFolder(stored);
        return actual == null ? "" : actual;
    }

    /** The classes from allClasses that are in the given folder, sorted. */
    public List<String> getClassesIn(String folder, Collection<String> allClasses)
    {
        String actual = findFolder(folder);
        List<String> result = new ArrayList<>();
        if (actual != null)
        {
            for (String cls : allClasses)
            {
                if (actual.equals(getFolder(cls)))
                {
                    result.add(cls);
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    /** The classes from allClasses that are in no (existing) folder, sorted. */
    public List<String> getUnfiled(Collection<String> allClasses)
    {
        List<String> result = new ArrayList<>();
        for (String cls : allClasses)
        {
            if (getFolder(cls).isEmpty())
            {
                result.add(cls);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Check a proposed folder name. Returns null if it is fine, or a message
     * saying what is wrong. When renaming, pass the folder's current name so
     * that it doesn't count as a clash with itself.
     */
    public String checkName(String name, String renaming)
    {
        if (name == null || name.trim().isEmpty())
        {
            return "A folder needs a name.";
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH)
        {
            return "Folder names can be at most " + MAX_NAME_LENGTH + " characters.";
        }
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0)
        {
            return "Folder names can't contain / or \\.";
        }
        for (int i = 0; i < trimmed.length(); i++)
        {
            if (Character.isISOControl(trimmed.charAt(i)))
            {
                return "Folder names can't contain control characters.";
            }
        }
        String existing = findFolder(trimmed);
        if (existing != null && (renaming == null || !existing.equalsIgnoreCase(renaming)))
        {
            return "There is already a folder called " + existing + ".";
        }
        return null;
    }

    /** Add an expanded folder at the end. The name is trimmed. */
    public void addFolder(String name)
    {
        addFolder(name, folders.size());
    }

    /** Add an expanded folder at a position in the display order. */
    public void addFolder(String name, int index)
    {
        String problem = checkName(name, null);
        if (problem != null)
        {
            throw new IllegalArgumentException(problem);
        }
        List<Map.Entry<String, Boolean>> entries = snapshot();
        int at = Math.max(0, Math.min(index, entries.size()));
        entries.add(at, Map.entry(name.trim(), Boolean.TRUE));
        replaceFolders(entries);
        fireChanged();
    }

    /** Rename a folder; its classes move with it. */
    public void renameFolder(String oldName, String newName)
    {
        String actual = requireFolder(oldName);
        String problem = checkName(newName, actual);
        if (problem != null)
        {
            throw new IllegalArgumentException(problem);
        }
        String trimmed = newName.trim();
        if (trimmed.equals(actual))
        {
            return;
        }
        List<Map.Entry<String, Boolean>> entries = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : snapshot())
        {
            entries.add(e.getKey().equals(actual) ? Map.entry(trimmed, e.getValue()) : e);
        }
        replaceFolders(entries);
        for (Map.Entry<String, String> e : classFolder.entrySet())
        {
            if (e.getValue().equalsIgnoreCase(actual))
            {
                e.setValue(trimmed);
            }
        }
        fireChanged();
    }

    /** Delete a folder. Its classes become unfiled; no class is deleted. */
    public void deleteFolder(String name)
    {
        String actual = requireFolder(name);
        folders.remove(actual);
        classFolder.values().removeIf(f -> f.equalsIgnoreCase(actual));
        fireChanged();
    }

    /** Move a folder to a new position in the display order. */
    public void moveFolder(String name, int newIndex)
    {
        String actual = requireFolder(name);
        List<Map.Entry<String, Boolean>> entries = snapshot();
        int from = new ArrayList<>(folders.keySet()).indexOf(actual);
        Map.Entry<String, Boolean> entry = entries.remove(from);
        int at = Math.max(0, Math.min(newIndex, entries.size()));
        if (at == from)
        {
            return;
        }
        entries.add(at, entry);
        replaceFolders(entries);
        fireChanged();
    }

    /**
     * Put a class in a folder, or unfile it with "". The folder must exist.
     */
    public void setFolder(String className, String folder)
    {
        String target = folder == null || folder.trim().isEmpty() ? null : requireFolder(folder);
        String old = classFolder.get(className);
        if (target == null)
        {
            if (old == null)
            {
                return;
            }
            classFolder.remove(className);
        }
        else
        {
            if (target.equals(old))
            {
                return;
            }
            classFolder.put(className, target);
        }
        fireChanged();
    }

    /** A class was renamed: it keeps its folder. */
    public void classRenamed(String oldName, String newName)
    {
        String folder = classFolder.remove(oldName);
        if (folder != null)
        {
            classFolder.put(newName, folder);
            fireChanged();
        }
    }

    /** A class was deleted: forget its folder. */
    public void classRemoved(String className)
    {
        if (classFolder.remove(className) != null)
        {
            fireChanged();
        }
    }

    private String findFolder(String name)
    {
        if (name == null)
        {
            return null;
        }
        String trimmed = name.trim();
        if (folders.containsKey(trimmed))
        {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String f : folders.keySet())
        {
            if (f.toLowerCase(Locale.ROOT).equals(lower))
            {
                return f;
            }
        }
        return null;
    }

    private String requireFolder(String name)
    {
        String actual = findFolder(name);
        if (actual == null)
        {
            throw new IllegalArgumentException("There is no folder called " + name + ".");
        }
        return actual;
    }

    /** The folders as immutable entries, in display order. */
    private List<Map.Entry<String, Boolean>> snapshot()
    {
        List<Map.Entry<String, Boolean>> entries = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : folders.entrySet())
        {
            entries.add(Map.entry(e.getKey(), e.getValue()));
        }
        return entries;
    }

    private void replaceFolders(List<Map.Entry<String, Boolean>> entries)
    {
        folders.clear();
        for (Map.Entry<String, Boolean> e : entries)
        {
            folders.put(e.getKey(), e.getValue());
        }
    }
}
