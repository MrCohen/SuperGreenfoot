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
package greenfoot.guifx.classes;

import bluej.utility.Debug;
import greenfoot.util.GreenfootUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * The optional description of one importable class shown in Edit &gt; Import
 * Class: its display name, category, short and long descriptions, and its
 * preview thumbnail. Read from a {@code <Stem>.properties} file beside the
 * class's source (or {@code .class}) file:
 * <pre>
 * display.name=Text Box
 * category=Text &amp; UI
 * description.short=A box of text you can recolour and re-centre.
 * description.long=A simple actor for showing one or more lines of text...
 * preview.image=TextBox-thumb.png
 * </pre>
 *
 * <p>A missing or unreadable manifest is not an error: every getter falls
 * back to something sensible (the class's own name, {@link #UNCATEGORISED},
 * no description, no thumbnail), so a class with no manifest -- including
 * anything a user drops into their own class folder -- still shows up.
 */
@OnThread(Tag.Any)
public class TemplateManifest
{
    /** The category shown for a template with no {@code category} entry (or no manifest at all). */
    public static final String UNCATEGORISED = "Other";

    private static final String KEY_DISPLAY_NAME = "display.name";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_SHORT_DESCRIPTION = "description.short";
    private static final String KEY_LONG_DESCRIPTION = "description.long";
    private static final String KEY_PREVIEW_IMAGE = "preview.image";

    private final String displayName;
    private final String category;
    private final String shortDescription;
    private final String longDescription;
    private final File previewImageFile;

    private TemplateManifest(String displayName, String category, String shortDescription,
            String longDescription, File previewImageFile)
    {
        this.displayName = displayName;
        this.category = category;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
        this.previewImageFile = previewImageFile;
    }

    /**
     * Load the manifest belonging to the given class file. Never fails: a
     * missing {@code <Stem>.properties} (or one that can't be read) gives a
     * manifest whose getters fall back to the class's own name, with no
     * category, description or thumbnail.
     *
     * @param classFile  the class's source, class or Stride file; only its
     *                   name (stem) and parent folder are used
     */
    public static TemplateManifest load(File classFile)
    {
        String stem = GreenfootUtil.removeExtension(classFile.getName());
        File propertiesFile = new File(classFile.getParentFile(), stem + ".properties");
        Properties p = new Properties();
        if (propertiesFile.isFile())
        {
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(propertiesFile), StandardCharsets.UTF_8))
            {
                p.load(reader);
            }
            catch (IOException e)
            {
                Debug.reportError("Could not read " + propertiesFile, e);
            }
        }

        String previewImageName = p.getProperty(KEY_PREVIEW_IMAGE);
        File previewImageFile = null;
        if (previewImageName != null)
        {
            File candidate = new File(classFile.getParentFile(), previewImageName);
            // Only point at it if it's actually there, same caution findImage() uses:
            // passing a non-existent file's URL to a JavaFX Image throws.
            if (candidate.isFile())
            {
                previewImageFile = candidate;
            }
        }

        return new TemplateManifest(
                p.getProperty(KEY_DISPLAY_NAME, stem),
                p.getProperty(KEY_CATEGORY, UNCATEGORISED),
                p.getProperty(KEY_SHORT_DESCRIPTION, ""),
                p.getProperty(KEY_LONG_DESCRIPTION, ""),
                previewImageFile);
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getCategory()
    {
        return category;
    }

    public String getShortDescription()
    {
        return shortDescription;
    }

    public String getLongDescription()
    {
        return longDescription;
    }

    /**
     * The template's preview thumbnail file, or null if it has none (no
     * manifest, no {@code preview.image} entry, or the named file isn't
     * there).
     */
    public File getPreviewImageFile()
    {
        return previewImageFile;
    }
}
