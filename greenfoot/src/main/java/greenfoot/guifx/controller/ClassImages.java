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
package greenfoot.guifx.controller;

import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The image chosen for each class in a project: the "class.&lt;name&gt;.image" entries
 * of the project file.  The images are read from the project file when the project
 * opens and written back with every save, independently of any window's class
 * display.
 *
 * <p>The entries follow the project file: a save writes an entry for each class the
 * project has (that has an image), and afterwards only those entries remain, so an
 * entry for a class that has gone (deleted or renamed) lasts until the next save,
 * just as it does in the saved file.
 */
@OnThread(Tag.FXPlatform)
public class ClassImages
{
    private static final String PREFIX = "class.";
    private static final String SUFFIX = ".image";

    // Class name (qualified) to image file name (in the project's images folder):
    private final Map<String, String> images = new HashMap<>();

    /**
     * Read the class images from the project's saved properties.
     */
    public ClassImages(Properties savedProperties)
    {
        for (String key : savedProperties.stringPropertyNames())
        {
            if (key.startsWith(PREFIX) && key.endsWith(SUFFIX)
                    && key.length() > PREFIX.length() + SUFFIX.length())
            {
                String className = key.substring(PREFIX.length(), key.length() - SUFFIX.length());
                images.put(className, savedProperties.getProperty(key));
            }
        }
    }

    /**
     * The image file name for the given class, or null if it has none of its own.
     */
    public String get(String qualifiedName)
    {
        return images.get(qualifiedName);
    }

    /**
     * Set (or, with null, remove) the image file name for the given class.
     */
    public void set(String qualifiedName, String imageFileName)
    {
        if (imageFileName == null)
        {
            images.remove(qualifiedName);
        }
        else
        {
            images.put(qualifiedName, imageFileName);
        }
    }

    /**
     * Add the images of the given classes to the properties being saved, and forget
     * the images of any other classes (they are no longer in the saved file).
     *
     * @param p           The properties being saved
     * @param classNames  The (qualified) names of the project's classes
     */
    public void save(Properties p, Collection<String> classNames)
    {
        Map<String, String> saved = new HashMap<>();
        for (String className : classNames)
        {
            String imageFileName = images.get(className);
            if (imageFileName != null)
            {
                p.put(PREFIX + className + SUFFIX, imageFileName);
                saved.put(className, imageFileName);
            }
        }
        images.clear();
        images.putAll(saved);
    }
}
