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

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

import bluej.utility.SortedProperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Saving project.greenfoot leaves the file untouched when nothing in it
 * would change, and rewrites it when something would.
 */
public class GreenfootProjectFileTest
{
    private Properties props(String... keyValues)
    {
        Properties p = new SortedProperties();
        for (int i = 0; i < keyValues.length; i += 2)
        {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    @Test
    public void unchangedContentIsNotRewritten() throws Exception
    {
        File dir = Files.createTempDirectory("gpf").toFile();
        GreenfootProjectFile file = new GreenfootProjectFile(dir);
        File onDisk = new File(dir, "project.greenfoot");
        assertTrue(file.create());
        file.save(props("main.class", "MyWorld", "simulation.speed", "50"));
        assertTrue(onDisk.isFile());
        long stamp = 1_000_000_000_000L;
        assertTrue(onDisk.setLastModified(stamp));

        file.save(props("main.class", "MyWorld", "simulation.speed", "50"));
        assertEquals("an identical save must not touch the file", stamp, onDisk.lastModified());

        file.save(props("main.class", "MyWorld", "simulation.speed", "60"));
        assertTrue("a changed save must write", onDisk.lastModified() != stamp);
        Properties back = new Properties();
        file.load(back);
        assertEquals("60", back.getProperty("simulation.speed"));
    }

    @Test
    public void hasContentComparesBytes() throws Exception
    {
        File f = Files.createTempFile("gpf", ".txt").toFile();
        Files.write(f.toPath(), "abc".getBytes("ISO-8859-1"));
        assertTrue(GreenfootProjectFile.hasContent(f, "abc".getBytes("ISO-8859-1")));
        assertTrue(!GreenfootProjectFile.hasContent(f, "abd".getBytes("ISO-8859-1")));
        assertTrue(!GreenfootProjectFile.hasContent(f, "ab".getBytes("ISO-8859-1")));
        assertTrue(!GreenfootProjectFile.hasContent(new File(f, "missing"), new byte[0]));
    }
}
