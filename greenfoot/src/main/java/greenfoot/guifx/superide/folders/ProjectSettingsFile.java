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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The per-project settings file of the SuperGreenfoot IDE,
 * {@value #FILE_NAME} in the scenario folder.
 *
 * <p>It holds state that only SuperGreenfoot uses: the new IDE's class folders
 * and view state, and the classes folded in either IDE's class list (the
 * Classic IDE reads and writes only those, through
 * {@link greenfoot.guifx.classes.ClassFolds}, and its class column's width,
 * through {@code greenfoot.guifx.ClassColumn}). Stock Greenfoot never reads it,
 * and it is kept out of project.greenfoot because the Classic IDE and stock
 * Greenfoot rewrite that file from scratch and would drop keys they don't know.
 *
 * <p>The file is a Java properties file. Keys this version doesn't know are
 * kept and written back unchanged. Saving is atomic and deterministic
 * (sorted keys, no timestamp, '\n' line ends) so that the file diffs cleanly
 * in git and syncs cleanly through Dropbox. If the file exists but can't be
 * read, it is treated as empty and left alone until something is changed.
 */
@OnThread(Tag.Any)
public class ProjectSettingsFile
{
    /** The name of the settings file in the scenario folder. */
    public static final String FILE_NAME = "supergreenfoot.properties";
    /** The format version this code writes. */
    public static final int VERSION = 1;
    /** The key holding the format version. */
    public static final String VERSION_KEY = "version";

    private static final String HEADER =
        "# Super Greenfoot settings for this scenario (class folders, folded classes, view state).\n"
        + "# Stock Greenfoot ignores this file. Safe to delete: folders and folds are then lost,\n"
        + "# but no code is affected.\n";

    private final File file;
    private final Map<String, String> values = new HashMap<>();
    private boolean dirty = false;
    private boolean unreadable = false;

    private ProjectSettingsFile(File file)
    {
        this.file = file;
    }

    /**
     * Read the settings file of the scenario in the given folder. A missing
     * file gives empty settings; an unreadable file also gives empty settings
     * (and is reported), and {@link #isUnreadable()} returns true.
     */
    public static ProjectSettingsFile load(File projectDir)
    {
        ProjectSettingsFile settings = new ProjectSettingsFile(new File(projectDir, FILE_NAME));
        settings.read();
        return settings;
    }

    private void read()
    {
        if (!file.exists())
        {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            props.load(in);
        }
        catch (IOException | IllegalArgumentException e)
        {
            unreadable = true;
            Debug.message("Could not read " + file + "; ignoring it until folders change: " + e.getMessage());
            return;
        }
        for (String key : props.stringPropertyNames())
        {
            values.put(key, props.getProperty(key));
        }
    }

    /** The settings file (which may not exist yet). */
    public File getFile()
    {
        return file;
    }

    /** True if the file exists but could not be read, so its contents were ignored. */
    public boolean isUnreadable()
    {
        return unreadable;
    }

    /** True if there are changes that {@link #save()} would write. */
    public boolean isDirty()
    {
        return dirty;
    }

    /** The value for a key, or null. */
    public String get(String key)
    {
        return values.get(key);
    }

    /** The value for a key, or the given default. */
    public String get(String key, String defaultValue)
    {
        String value = values.get(key);
        return value == null ? defaultValue : value;
    }

    /** The value for a key as an int, or the default if absent or not a number. */
    public int getInt(String key, int defaultValue)
    {
        String value = values.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    /**
     * Set a value; a null value removes the key. Setting a key to the value it
     * already has is not a change.
     */
    public void put(String key, String value)
    {
        if (value == null)
        {
            if (values.remove(key) != null)
            {
                dirty = true;
            }
        }
        else if (!value.equals(values.put(key, value)))
        {
            dirty = true;
        }
    }

    /** All keys, in the order they are written. */
    public List<String> getKeys()
    {
        List<String> keys = new ArrayList<>(values.keySet());
        keys.sort(KEY_ORDER);
        return Collections.unmodifiableList(keys);
    }

    /**
     * Write the settings if anything changed since loading or the last save.
     * Does nothing when nothing changed, so an unreadable file is left in
     * place until something is actually changed.
     */
    public void save() throws IOException
    {
        if (!dirty)
        {
            return;
        }
        if (!values.containsKey(VERSION_KEY))
        {
            values.put(VERSION_KEY, Integer.toString(VERSION));
        }
        Path target = file.toPath();
        Path dir = target.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(dir, "." + FILE_NAME + ".", ".tmp");
        try
        {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.ISO_8859_1))
            {
                out.write(toText());
            }
            try
            {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
        dirty = false;
        unreadable = false;
    }

    /** The exact text {@link #save()} writes (ASCII, '\n' line ends). */
    String toText()
    {
        StringBuilder sb = new StringBuilder(HEADER);
        for (String key : getKeys())
        {
            sb.append(escape(key, true)).append('=').append(escape(values.get(key), false)).append('\n');
        }
        return sb.toString();
    }

    /**
     * Escape for a properties file: backslash escapes for the special
     * characters, \\uXXXX for anything outside printable ASCII, and spaces
     * escaped in keys and at the start of values.
     */
    private static String escape(String s, boolean isKey)
    {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '\\': out.append("\\\\"); break;
                case '\t': out.append("\\t"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\f': out.append("\\f"); break;
                case '=': case ':': case '#': case '!':
                    out.append('\\').append(c);
                    break;
                case ' ':
                    if (isKey || i == 0)
                    {
                        out.append('\\');
                    }
                    out.append(' ');
                    break;
                default:
                    if (c < 0x20 || c > 0x7e)
                    {
                        out.append(String.format("\\u%04X", (int) c));
                    }
                    else
                    {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    /**
     * Key order: runs of digits compare as numbers, so "folders.10.name"
     * comes after "folders.9.name"; everything else compares as text.
     */
    static final Comparator<String> KEY_ORDER = (a, b) -> {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length())
        {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb))
            {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int cmp = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (cmp != 0)
                {
                    return cmp;
                }
            }
            else
            {
                if (ca != cb)
                {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        int rest = Integer.compare(a.length() - i, b.length() - j);
        return rest != 0 ? rest : a.compareTo(b);
    };
}
