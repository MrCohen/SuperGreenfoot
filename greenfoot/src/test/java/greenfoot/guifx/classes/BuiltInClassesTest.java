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

import junit.framework.TestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The reserved class names are exactly the documented API, so a class added to
 * the user Javadoc is reserved too (and one taken out is freed).
 */
public class BuiltInClassesTest extends TestCase
{
    public void testReservedNamesAreTheDocumentedApi() throws Exception
    {
        // The tests run in the greenfoot module's folder.
        String gradle = new String(Files.readAllBytes(new File("build.gradle").toPath()), StandardCharsets.UTF_8);
        int start = gradle.indexOf("task userJavadoc");
        assertTrue("userJavadoc task not found in build.gradle", start >= 0);
        String includes = gradle.substring(gradle.indexOf("includes", start), gradle.indexOf("]", start));
        List<String> documented = new ArrayList<>();
        Matcher m = Pattern.compile("'greenfoot/(\\w+)\\.java'").matcher(includes);
        while (m.find())
        {
            documented.add(m.group(1));
        }
        Collections.sort(documented);
        List<String> reserved = new ArrayList<>(BuiltInClasses.API_CLASSES);
        Collections.sort(reserved);
        assertEquals(documented, reserved);
    }

    public void testIsReserved()
    {
        assertTrue(BuiltInClasses.isReserved("SuperWindow"));
        assertTrue(BuiltInClasses.isReserved("Color"));
        assertTrue(BuiltInClasses.isReserved("Actor"));
        // Case matters in Java, and internal public classes are not the API:
        assertFalse(BuiltInClasses.isReserved("superwindow"));
        assertFalse(BuiltInClasses.isReserved("ActorSet"));
        assertFalse(BuiltInClasses.isReserved("Rocket"));
    }
}
