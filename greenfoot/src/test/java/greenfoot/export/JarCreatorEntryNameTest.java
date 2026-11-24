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
package greenfoot.export;

import junit.framework.TestCase;

import java.io.File;
import java.nio.file.Files;

/**
 * A jar always holds the project file as /project.greenfoot, the name every
 * player reads, whatever it is called on disk; a zip keeps the real name.
 */
public class JarCreatorEntryNameTest extends TestCase
{
    private File dir;

    @Override
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory("jarname").toFile();
    }

    private File touch(String name) throws Exception
    {
        File f = new File(dir, name);
        Files.write(f.toPath(), new byte[0]);
        return f;
    }

    public void testTheNewNameGoesIntoAJarAsTheOldOne() throws Exception
    {
        File f = touch("__project.greenfoot");
        assertEquals("project.greenfoot", JarCreator.entryName(dir, f, "", false));
    }

    public void testALegacyFileKeepsItsName() throws Exception
    {
        File f = touch("project.greenfoot");
        assertEquals("project.greenfoot", JarCreator.entryName(dir, f, "", false));
    }

    public void testWithBothOnlyTheNewOneGoesIn() throws Exception
    {
        File legacy = touch("project.greenfoot");
        File current = touch("__project.greenfoot");
        assertEquals("project.greenfoot", JarCreator.entryName(dir, current, "", false));
        assertNull(JarCreator.entryName(dir, legacy, "", false));
    }

    public void testAZipKeepsTheRealName() throws Exception
    {
        File f = touch("__project.greenfoot");
        assertEquals("Demo/__project.greenfoot", JarCreator.entryName(dir, f, "Demo/", true));
    }

    public void testOtherFilesAndSubfoldersAreUntouched() throws Exception
    {
        File f = touch("MyWorld.class");
        assertEquals("MyWorld.class", JarCreator.entryName(dir, f, "", false));
        File sub = new File(dir, "images");
        assertTrue(sub.mkdir());
        File nested = new File(sub, "__project.greenfoot");
        Files.write(nested.toPath(), new byte[0]);
        assertEquals("images/__project.greenfoot", JarCreator.entryName(dir, nested, "images/", false));
    }
}
