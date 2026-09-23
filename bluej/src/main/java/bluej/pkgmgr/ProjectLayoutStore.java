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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import bluej.Config;
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
 */
@OnThread(Tag.Any)
public final class ProjectLayoutStore
{
    /** The file in the user's preferences folder. */
    public static final String FILE_NAME = "scenario-layouts.properties";

    private static ProjectLayoutStore instance;

    private final File file;
    private final SortedProperties props = new SortedProperties();
    private boolean loaded = false;
    private boolean dirty = false;

    ProjectLayoutStore(File file)
    {
        this.file = file;
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
        load();
        return props.getProperty(keyFor(projectDir, key));
    }

    /** Set (or with null, remove) the value of {@code key} for the scenario in {@code projectDir}. */
    public synchronized void put(File projectDir, String key, String value)
    {
        load();
        String full = keyFor(projectDir, key);
        String old = props.getProperty(full);
        if (value == null ? old == null : value.equals(old))
        {
            return;
        }
        if (value == null)
        {
            props.remove(full);
        }
        else
        {
            props.setProperty(full, value);
        }
        dirty = true;
    }

    /** Write the file if anything changed since it was read or last written. */
    public synchronized void flush()
    {
        if (!dirty)
        {
            return;
        }
        try (OutputStream out = new FileOutputStream(file))
        {
            props.store(out, "SuperGreenfoot: window positions and sizes per scenario, for this user");
            dirty = false;
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
        if (!file.isFile())
        {
            return;
        }
        try (InputStream in = new FileInputStream(file))
        {
            props.load(in);
        }
        catch (IOException | IllegalArgumentException e)
        {
            Debug.message("Could not read " + file + "; starting empty: " + e.getMessage());
            props.clear();
        }
    }

    /** The property key for a scenario folder and a setting name. */
    static String keyFor(File projectDir, String key)
    {
        String path;
        try
        {
            path = projectDir.getCanonicalPath();
        }
        catch (IOException e)
        {
            path = projectDir.getAbsolutePath();
        }
        return path + "|" + key;
    }
}
