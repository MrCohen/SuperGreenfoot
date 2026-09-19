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

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 * Tests the project's class-image model, which replaces saving class images
 * through the Classic class diagram.
 */
public class ClassImagesTest extends TestCase
{
    private static Properties saved(String... keysAndValues)
    {
        Properties p = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2)
        {
            p.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return p;
    }

    public void testReadsOnlyClassImageEntries()
    {
        ClassImages images = new ClassImages(saved(
                "class.Player.image", "player.png",
                "class.Enemy.image", "bat.png",
                "class..image", "ignored.png",
                "classic.image", "ignored.png",
                "world.lastInstantiated", "MyWorld",
                "target1.name", "Player"));
        assertEquals("player.png", images.get("Player"));
        assertEquals("bat.png", images.get("Enemy"));
        assertNull(images.get("MyWorld"));
        assertNull(images.get(""));
    }

    public void testSaveWritesTheProjectsClassesOnly()
    {
        ClassImages images = new ClassImages(saved(
                "class.Player.image", "player.png",
                "class.Gone.image", "old.png"));
        Properties p = new Properties();
        images.save(p, Arrays.asList("Player", "MyWorld"));
        assertEquals("player.png", p.getProperty("class.Player.image"));
        assertNull(p.getProperty("class.Gone.image"));
        assertNull(p.getProperty("class.MyWorld.image"));
        assertEquals(1, p.size());
    }

    public void testSaveForgetsClassesThatAreGone()
    {
        ClassImages images = new ClassImages(saved("class.Gone.image", "old.png"));
        // Until the next save, a stale entry is still there (as in the saved file):
        assertEquals("old.png", images.get("Gone"));
        images.save(new Properties(), Collections.singletonList("Player"));
        assertNull(images.get("Gone"));
        // A new class of the same name no longer picks up the old image:
        Properties p = new Properties();
        images.save(p, Collections.singletonList("Gone"));
        assertTrue(p.isEmpty());
    }

    public void testSetAndRemove()
    {
        ClassImages images = new ClassImages(new Properties());
        images.set("Player", "player.png");
        assertEquals("player.png", images.get("Player"));
        images.set("Player", "hero.png");
        assertEquals("hero.png", images.get("Player"));
        images.set("Player", null);
        assertNull(images.get("Player"));
        Properties p = new Properties();
        images.save(p, Collections.singletonList("Player"));
        assertTrue(p.isEmpty());
    }

    public void testRenameMovesTheImage()
    {
        // A rename sets the image under the new name, then saves with the new class list:
        ClassImages images = new ClassImages(saved("class.Old.image", "rocket.png"));
        images.set("New", images.get("Old"));
        Properties p = new Properties();
        images.save(p, Collections.singletonList("New"));
        assertEquals("rocket.png", p.getProperty("class.New.image"));
        assertNull(p.getProperty("class.Old.image"));
        assertNull(images.get("Old"));
    }
}
