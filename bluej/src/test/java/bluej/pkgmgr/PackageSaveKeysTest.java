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

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Saving a package must not drop keys it did not write itself (main.class,
 * project.name, scenario.lock...), while the blocks it rewrites in full
 * every time (targets, dependencies, editor windows) must not linger from
 * an earlier save.
 */
public class PackageSaveKeysTest
{
    @Test
    public void managedKeysAreTheOnesSaveRewrites()
    {
        assertTrue(Package.isManagedKey("target1.name"));
        assertTrue(Package.isManagedKey("target116.showInterface"));
        assertTrue(Package.isManagedKey("dependency520.type"));
        assertTrue(Package.isManagedKey("package.numTargets"));
        assertTrue(Package.isManagedKey("package.editor.x"));
        assertTrue(Package.isManagedKey("readme.height"));
        assertTrue(Package.isManagedKey("editor.fx.0.width"));
        assertTrue(Package.isManagedKey("class.Boar.image"));
        assertTrue(Package.isManagedKey("shm.size"));

        assertFalse(Package.isManagedKey("main.class"));
        assertFalse(Package.isManagedKey("project.name"));
        assertFalse(Package.isManagedKey("project.charset"));
        assertFalse(Package.isManagedKey("scenario.lock"));
        assertFalse(Package.isManagedKey("scenario.hideControls"));
        assertFalse(Package.isManagedKey("world.lastInstantiated"));
        assertFalse(Package.isManagedKey("simulation.speed"));
        assertFalse(Package.isManagedKey("publish.title"));
        assertFalse(Package.isManagedKey("player.name"));
        // Names that merely start with the same letters are not blocks
        assertFalse(Package.isManagedKey("targetFrameRate"));
        assertFalse(Package.isManagedKey("target.default"));
        assertFalse(Package.isManagedKey("dependencyCheck"));
    }

    @Test
    public void unknownKeysSurviveASaveAndStaleBlocksDoNot()
    {
        Properties lastSaved = new Properties();
        lastSaved.setProperty("main.class", "TitleWorld");
        lastSaved.setProperty("project.name", "TenthRealm");
        lastSaved.setProperty("simulation.speed", "50");
        lastSaved.setProperty("package.numTargets", "3");
        lastSaved.setProperty("target3.name", "Gone");
        lastSaved.setProperty("dependency9.from", "A");
        lastSaved.setProperty("editor.fx.1.x", "10");
        lastSaved.setProperty("class.Gone.image", "gone.png");
        lastSaved.setProperty("shm.size", "999");

        Properties props = new Properties();
        Package.keepUnmanagedKeys(lastSaved, props);
        // What the IDE puts afterwards wins over the old value
        props.setProperty("simulation.speed", "60");

        assertEquals("TitleWorld", props.getProperty("main.class"));
        assertEquals("TenthRealm", props.getProperty("project.name"));
        assertEquals("60", props.getProperty("simulation.speed"));
        assertNull(props.getProperty("package.numTargets"));
        assertNull(props.getProperty("target3.name"));
        assertNull(props.getProperty("dependency9.from"));
        assertNull(props.getProperty("editor.fx.1.x"));
        assertNull(props.getProperty("class.Gone.image"));
        assertNull(props.getProperty("shm.size"));
    }
}
