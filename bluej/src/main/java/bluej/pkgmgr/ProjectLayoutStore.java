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
package bluej.pkgmgr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import bluej.Config;
import bluej.utility.AtomicFiles;
import bluej.utility.Debug;
import bluej.utility.SortedProperties;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Where each scenario's window geometry is kept for this user: the IDE
 * window's position and size, and the editor windows'. It is per machine,
 * so it lives in the user's preferences folder ({@value #FILE_NAME}) keyed
 * by the scenario's path, not in the scenario, which travels between
 * machines and screens. Values are read on first use and written by
 * {@link #flush()} when something changed.
 *
 * <p>Each key is {@code <canonical scenario path>|<setting>}. Writing a
 * scenario's settings also stamps {@code <path>|}{@value #STAMP_KEY} with the
 * time. A flush re-reads the file and applies only this process's changes
 * on top, so two IDEs running at once keep each other's entries (the last
 * writer wins per key). Scenarios whose folder is gone are dropped, and only
 * the {@value #MAX_SCENARIOS} most recently written are kept; entries from
 * builds before the stamp count as the oldest.
 */
@OnThread(Tag.Any)
public final class ProjectLayoutStore
{
    /** The file in the user's preferences folder. */
    public static final String FILE_NAME = "scenario-layouts.properties";
    /** The per-scenario setting holding when its layout was last written (milliseconds). */
    static final String STAMP_KEY = "layout.lastWritten";
    /** How many scenarios are kept. */
    static final int MAX_SCENARIOS = 200;

    private static final String HEADER = "SuperGreenfoot: window positions and sizes per scenario, for this user";

    private static ProjectLayoutStore instance;

    private final File file;
    private final Predicate<File> scenarioExists;
    private final int maxScenarios;
    private SortedProperties props = new SortedProperties();
    /** This process's changes since the last flush, by full key; a null value is a removal. */
    private final Map<String, String> changes = new LinkedHashMap<>();
    private boolean loaded = false;

    ProjectLayoutStore(File file)
    {
        this(file, File::isDirectory, MAX_SCENARIOS);
    }

    ProjectLayoutStore(File file, Predicate<File> scenarioExists, int maxScenarios)
    {
        this.file = file;
        this.scenarioExists = scenarioExists;
        this.maxScenarios = maxScenarios;
    }

    /** The store in the user's preferences folder. */
    public static synchronized ProjectLayoutStore get()
    {
        if (instance == null)
        {
            instance = new ProjectLayoutStore(Config.getUserConfigFile(FILE_NAME));
        }
        return instance;
    }

    /** The value of {@code key} for the scenario in {@code projectDir}, or null. */
    public synchronized String get(File projectDir, String key)
    {
        return getRaw(pathOf(projectDir), key);
    }

    /** Set (or with null, remove) the value of {@code key} for the scenario in {@code projectDir}. */
    public synchronized void put(File projectDir, String key, String value)
    {
        putRaw(pathOf(projectDir), key, value);
    }

    /** The value of {@code key} for the scenario whose canonical path is {@code path}. */
    synchronized String getRaw(String path, String key)
    {
        load();
        return props.getProperty(path + "|" + key);
    }

    /** Set or remove the value of {@code key} for the scenario whose canonical path is {@code path}. */
    synchronized void putRaw(String path, String key, String value)
    {
        load();
        String full = path + "|" + key;
        String old = props.getProperty(full);
        if (value == null ? old == null : value.equals(old))
        {
            return;
        }
        set(props, full, value);
        changes.put(full, value);
        String stamp = Long.toString(System.currentTimeMillis());
        props.setProperty(path + "|" + STAMP_KEY, stamp);
        changes.put(path + "|" + STAMP_KEY, stamp);
    }

    /**
     * Write the file if anything changed since it was read or last written:
     * the file as it is now on disk, with this process's changes on top.
     */
    public synchronized void flush()
    {
        if (changes.isEmpty())
        {
            return;
        }
        SortedProperties merged = read();
        if (merged == null)
        {
            // Missing or unreadable: what this process knows is the best there is.
            merged = new SortedProperties();
            merged.putAll(props);
        }
        for (Map.Entry<String, String> change : changes.entrySet())
        {
            set(merged, change.getKey(), change.getValue());
        }
        prune(merged);
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            merged.store(out, HEADER, "\n");
            AtomicFiles.write(file.toPath(), out.toByteArray());
            props = merged;
            changes.clear();
        }
        catch (IOException e)
        {
            Debug.message("Could not write " + file + ": " + e.getMessage());
        }
    }

    private void load()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;
        SortedProperties read = read();
        if (read != null)
        {
            props = read;
            prune(props);
        }
    }

    /** The file as it is on disk, or null if it is missing or cannot be read. */
    private SortedProperties read()
    {
        if (!file.isFile())
        {
            return null;
        }
        SortedProperties read = new SortedProperties();
        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            read.load(in);
            return read;
        }
        catch (IOException | IllegalArgumentException e)
        {
            Debug.message("Could not read " + file + "; ignoring it: " + e.getMessage());
            return null;
        }
    }

    /**
     * Drop the scenarios whose folder no longer exists, then all but the
     * most recently written {@code maxScenarios}.
     */
    private void prune(SortedProperties p)
    {
        Map<String, List<String>> keysByPath = new HashMap<>();
        for (String key : p.stringPropertyNames())
        {
            int bar = key.lastIndexOf('|');
            if (bar > 0)
            {
                keysByPath.computeIfAbsent(key.substring(0, bar), k -> new ArrayList<>()).add(key);
            }
        }
        List<String> kept = new ArrayList<>();
        for (Map.Entry<String, List<String>> scenario : keysByPath.entrySet())
        {
            if (scenarioExists.test(new File(scenario.getKey())))
            {
                kept.add(scenario.getKey());
            }
            else
            {
                scenario.getValue().forEach(p::remove);
            }
        }
        if (kept.size() > maxScenarios)
        {
            kept.sort(Comparator.comparingLong((String path) -> stampOf(p, path)).reversed()
                    .thenComparing(Comparator.naturalOrder()));
            for (String path : kept.subList(maxScenarios, kept.size()))
            {
                keysByPath.get(path).forEach(p::remove);
            }
        }
    }

    private static long stampOf(SortedProperties p, String path)
    {
        try
        {
            return Long.parseLong(p.getProperty(path + "|" + STAMP_KEY, "0").trim());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static void set(SortedProperties p, String key, String value)
    {
        if (value == null)
        {
            p.remove(key);
        }
        else
        {
            p.setProperty(key, value);
        }
    }

    /**
     * A stored window coordinate or size, or null if it is missing or is not
     * a finite number (a hand-edited or damaged file), so the caller falls
     * back to its default rather than failing while the window opens.
     */
    public static Double parseNumber(String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            double d = Double.parseDouble(value.trim());
            return Double.isFinite(d) ? d : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** The property key for a scenario folder and a setting name. */
    static String keyFor(File projectDir, String key)
    {
        return pathOf(projectDir) + "|" + key;
    }

    /** The scenario folder's canonical path (its absolute path if that fails). */
    private static String pathOf(File projectDir)
    {
        try
        {
            return projectDir.getCanonicalPath();
        }
        catch (IOException e)
        {
            return projectDir.getAbsolutePath();
        }
    }
}
