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
package greenfoot;

import greenfoot.util.GreenfootUtil;
import junit.framework.TestCase;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Tests the Save API against a temporary directory.
 */
public class SaveTest extends TestCase
{
    private File dir;

    @Override
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory("sgf-save").toFile();
        GreenfootUtil.initialise(new TestUtilDelegate() {
            @Override
            public File getSaveDirectory()
            {
                return dir;
            }
        });
        Save.resetForTesting();
    }

    public void testValuesRoundTripThroughDisk()
    {
        assertEquals(7, Save.getInt("lives", 7));
        Save.putInt("lives", 3);
        Save.putString("name", "Ada");
        Save.putDouble("ratio", 0.25);
        Save.putBoolean("sound", false);
        assertEquals(3, Save.getInt("lives", 0));
        Save.flush();
        assertTrue(new File(dir, "save.properties").isFile());

        // Reload from disk
        Save.resetForTesting();
        assertEquals(3, Save.getInt("lives", 0));
        assertEquals("Ada", Save.getString("name", ""));
        assertEquals(0.25, Save.getDouble("ratio", 1), 1e-12);
        assertFalse(Save.getBoolean("sound", true));
        assertTrue(Save.contains("lives"));
        assertEquals(List.of("lives", "name", "ratio", "sound"), Save.getKeys());

        Save.remove("name");
        Save.flush();
        Save.resetForTesting();
        assertFalse(Save.contains("name"));
        Save.clear();
        assertTrue(Save.getKeys().isEmpty());
    }

    public void testBadNumbersFallBackToDefault()
    {
        Save.putString("lives", "many");
        assertEquals(9, Save.getInt("lives", 9));
        assertEquals(1.5, Save.getDouble("lives", 1.5), 0);
    }

    public void testHighScoresSortedAndPersisted()
    {
        Save.submitScore("classic", "Ada", 120);
        Save.submitScore("classic", "Bob", 300);
        Save.submitScore("classic", "Cy", 120);   // same score as Ada, later: after Ada
        Save.submitScore("classic", "Dee", 50);
        List<Save.ScoreEntry> top = Save.getTopScores("classic", 3);
        assertEquals(3, top.size());
        assertEquals("Bob", top.get(0).getPlayer());
        assertEquals(300, top.get(0).getScore());
        assertEquals("Ada", top.get(1).getPlayer());
        assertEquals("Cy", top.get(2).getPlayer());
        assertEquals(120, Save.getBestScore("classic", "Ada"));
        assertEquals(Integer.MIN_VALUE, Save.getBestScore("classic", "Nobody"));

        Save.resetForTesting();
        assertEquals(4, Save.getTopScores("classic", 10).size());
        assertTrue(Save.getTopScores("other", 10).isEmpty());
        Save.clearScores("classic");
        assertTrue(Save.getTopScores("classic", 10).isEmpty());
    }

    public void testWorksInMemoryWithoutDirectory()
    {
        GreenfootUtil.initialise(new TestUtilDelegate());   // getSaveDirectory() returns null
        Save.resetForTesting();
        Save.putInt("x", 1);
        assertEquals(1, Save.getInt("x", 0));
        Save.submitScore("t", "A", 5);
        assertEquals(1, Save.getTopScores("t", 5).size());
        Save.flush();   // no directory: must not throw
    }
}
