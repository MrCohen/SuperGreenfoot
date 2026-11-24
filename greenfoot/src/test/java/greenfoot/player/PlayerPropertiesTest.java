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
package greenfoot.player;

import junit.framework.TestCase;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Properties;

/**
 * The player finds the project file under either name. Kept apart from
 * PlayerSessionTest, whose headless run is sensitive to what ran before it.
 */
public class PlayerPropertiesTest extends TestCase
{
    /** A scenario run from its folder may name its project file either way; the new name wins. */
    public void testLoadPropertiesReadsEitherProjectFileName() throws Exception
    {
        File dir = Files.createTempDirectory("sgf-props").toFile();
        Files.writeString(new File(dir, "project.greenfoot").toPath(), "world.lastInstantiated=Old\nproject.name=Legacy\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toURI().toURL()}, null)) {
            assertEquals("Old", PlayerSession.loadProperties(loader).getProperty("world.lastInstantiated"));
        }
        Files.writeString(new File(dir, "__project.greenfoot").toPath(), "world.lastInstantiated=New\n");
        Files.writeString(new File(dir, "standalone.properties").toPath(), "main.class=Main\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toURI().toURL()}, null)) {
            Properties p = PlayerSession.loadProperties(loader);
            assertEquals("New", p.getProperty("world.lastInstantiated"));
            assertNull("only one project file is read", p.getProperty("project.name"));
            assertEquals("Main", p.getProperty("main.class"));
        }
    }
}
