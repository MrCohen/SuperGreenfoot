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
package greenfoot.templates;

import greenfoot.TestUtilDelegate;
import greenfoot.core.Simulation;
import greenfoot.guifx.classes.TemplateManifest;
import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The classes offered by Edit &gt; Import Class: they compile against the current API,
 * behave as documented (the checks in src/test/templates/TemplateChecks.java), and each
 * has a manifest with a category and a thumbnail for the dialog.
 */
public class ImportableTemplatesTest extends TestCase
{
    @Override
    protected void setUp() throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        Simulation.initialize();
    }

    public void testTemplatesCompileAndPassTheirChecks() throws Exception
    {
        // The thumbnail scenes are compiled too, so they can't quietly stop compiling.
        ClassLoader loader = TemplateCompiler.compile("TemplateChecks", "TemplateThumbnails");
        Method[] methods = loader.loadClass("TemplateChecks").getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        List<String> failures = new ArrayList<>();
        int run = 0;
        for (Method check : methods)
        {
            if (!check.getName().startsWith("check") || !Modifier.isStatic(check.getModifiers())
                    || !Modifier.isPublic(check.getModifiers()) || check.getParameterCount() != 0)
            {
                continue;
            }
            run++;
            try
            {
                check.invoke(null);
            }
            catch (InvocationTargetException e)
            {
                failures.add(check.getName() + ": " + e.getCause());
            }
        }
        assertTrue("no checks found", run > 0);
        assertTrue(failures.size() + " of " + run + " checks failed:\n  " + String.join("\n  ", failures),
                failures.isEmpty());
    }

    public void testEveryTemplateHasACategoryAndThumbnail() throws Exception
    {
        for (File source : TemplateCompiler.templateSources())
        {
            TemplateManifest manifest = TemplateManifest.load(source);
            assertFalse(source + " has no category", TemplateManifest.UNCATEGORISED.equals(manifest.getCategory()));
            assertFalse(source + " has no description", manifest.getShortDescription().isEmpty());
            assertNotNull(source + " has no thumbnail file", manifest.getPreviewImageFile());
        }
    }
}
