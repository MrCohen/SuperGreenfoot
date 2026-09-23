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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The per-user layout store keeps window geometry per scenario folder,
 * writes only when something changed, and reads back what it wrote.
 */
public class ProjectLayoutStoreTest
{
    @Test
    public void valuesAreKeyedByScenarioAndSurviveAReload() throws Exception
    {
        File prefs = Files.createTempDirectory("prefs").toFile();
        File storeFile = new File(prefs, ProjectLayoutStore.FILE_NAME);
        File scenarioA = Files.createTempDirectory("scenarioA").toFile();
        File scenarioB = Files.createTempDirectory("scenarioB").toFile();

        ProjectLayoutStore store = new ProjectLayoutStore(storeFile);
        assertNull(store.get(scenarioA, "width"));
        store.flush();
        assertFalse("nothing to write yet", storeFile.exists());

        store.put(scenarioA, "width", "1333");
        store.put(scenarioA, "xPosition", "1806");
        store.put(scenarioB, "width", "800");
        store.flush();
        assertTrue(storeFile.isFile());

        ProjectLayoutStore again = new ProjectLayoutStore(storeFile);
        assertEquals("1333", again.get(scenarioA, "width"));
        assertEquals("1806", again.get(scenarioA, "xPosition"));
        assertEquals("800", again.get(scenarioB, "width"));
        assertNull(again.get(scenarioB, "xPosition"));

        long stamp = 1_000_000_000_000L;
        assertTrue(storeFile.setLastModified(stamp));
        again.put(scenarioA, "width", "1333");
        again.flush();
        assertEquals("an unchanged value does not rewrite the file", stamp, storeFile.lastModified());

        again.put(scenarioA, "width", null);
        again.flush();
        assertNull(new ProjectLayoutStore(storeFile).get(scenarioA, "width"));
    }

    @Test
    public void keyUsesTheCanonicalPath() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        File viaDot = new File(dir, ".");
        assertEquals(ProjectLayoutStore.keyFor(dir, "width"), ProjectLayoutStore.keyFor(viaDot, "width"));
        assertTrue(ProjectLayoutStore.keyFor(dir, "width").endsWith("|width"));
    }
}
