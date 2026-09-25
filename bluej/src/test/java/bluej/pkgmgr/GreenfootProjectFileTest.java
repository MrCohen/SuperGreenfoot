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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;

import bluej.utility.SortedProperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    private static String text(File f) throws Exception
    {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
    }

    @Test
    public void aFatFileIsSavedSlimInGreenfootMode() throws Exception
    {
        File dir = Files.createTempDirectory("gpf").toFile();
        File onDisk = new File(dir, "project.greenfoot");
        // As stock Greenfoot 3.9 writes it: a class-diagram layout, window
        // geometry, editor windows, and a few settings of the scenario's own
        Files.write(onDisk.toPath(), String.join("\n",
                "#Greenfoot project file",
                "class.Boar.image=boar.png",
                "dependency1.from=MyWorld",
                "dependency1.to=Boar",
                "dependency1.type=UsesDependency",
                "editor.fx.0.height=700",
                "editor.fx.0.width=800",
                "editor.fx.0.x=10",
                "editor.fx.0.y=20",
                "height=600",
                "main.class=MyWorld",
                "package.numDependencies=1",
                "package.numTargets=2",
                "project.charset=UTF-8",
                "project.name=Boars",
                "publish.title=Boar Hunt",
                "readme.height=60",
                "readme.name=@README",
                "readme.width=48",
                "readme.x=10",
                "readme.y=10",
                "simulation.speed=50",
                "target1.height=50",
                "target1.name=Boar",
                "target1.type=ClassTarget",
                "target1.width=80",
                "target1.x=0",
                "target1.y=0",
                "target2.name=MyWorld",
                "version=3.0.0",
                "width=850",
                "world.lastInstantiated=MyWorld",
                "xPosition=40",
                "yPosition=40",
                "").getBytes(StandardCharsets.ISO_8859_1));
        GreenfootProjectFile file = new GreenfootProjectFile(dir);
        Properties lastSaved = new SortedProperties();
        file.load(lastSaved);

        // What the Greenfoot IDE puts on a save (GreenfootStage/controller)
        Properties frame = new Properties();
        frame.setProperty("simulation.speed", "60");
        frame.setProperty("world.lastInstantiated", "MyWorld");
        frame.setProperty("version", "3.0.0");
        file.save(Package.propertiesToSave(lastSaved, frame));

        Properties back = new Properties();
        file.load(back);
        for (String key : back.stringPropertyNames())
        {
            assertFalse("managed key written: " + key, Package.isManagedKey(key));
        }
        assertEquals("MyWorld", back.getProperty("main.class"));
        assertEquals("Boars", back.getProperty("project.name"));
        assertEquals("Boar Hunt", back.getProperty("publish.title"));
        assertEquals("UTF-8", back.getProperty("project.charset"));
        assertEquals("60", back.getProperty("simulation.speed"));
        assertEquals(8, text(onDisk).split("\n").length);
    }

    @Test
    public void lineEndsAreAlwaysLfAndCrlfInputIsRead() throws Exception
    {
        File dir = Files.createTempDirectory("gpf").toFile();
        File onDisk = new File(dir, "project.greenfoot");
        // As Greenfoot writes it on Windows
        byte[] crlf = "#Greenfoot project file\r\nmain.class=MyWorld\r\nsimulation.speed=50\r\n"
                .getBytes(StandardCharsets.ISO_8859_1);
        Files.write(onDisk.toPath(), crlf);
        GreenfootProjectFile file = new GreenfootProjectFile(dir);
        Properties p = new SortedProperties();
        file.load(p);
        assertEquals("MyWorld", p.getProperty("main.class"));
        assertEquals("50", p.getProperty("simulation.speed"));

        file.save(p);
        String lf = "#Greenfoot project file\nmain.class=MyWorld\nsimulation.speed=50\n";
        assertEquals("saved with '\\n' whatever the platform", lf, text(onDisk));

        // An LF file saved again is left alone, on every platform
        long stamp = 1_000_000_000_000L;
        assertTrue(onDisk.setLastModified(stamp));
        Properties again = new Properties();
        file.load(again);
        file.save(again);
        assertEquals(stamp, onDisk.lastModified());
    }

    @Test
    public void aFailedSaveLeavesTheOldFileWhole() throws Exception
    {
        File dir = Files.createTempDirectory("gpf").toFile();
        File onDisk = new File(dir, "project.greenfoot");
        GreenfootProjectFile file = new GreenfootProjectFile(dir);
        assertTrue(file.create());
        file.save(props("main.class", "MyWorld"));
        String before = text(onDisk);

        // The file is writable but no temporary file can be made beside it
        assertTrue(dir.setWritable(false));
        try
        {
            file.save(props("main.class", "OtherWorld"));
            fail("the save cannot succeed");
        }
        catch (IOException e)
        {
            // expected
        }
        finally
        {
            dir.setWritable(true);
        }
        assertEquals(before, text(onDisk));
        assertEquals(Arrays.asList("project.greenfoot"), Arrays.asList(dir.list()));

        file.save(props("main.class", "OtherWorld"));
        assertTrue(text(onDisk).contains("main.class=OtherWorld"));
        assertEquals("no temporary file is left behind", Arrays.asList("project.greenfoot"), Arrays.asList(dir.list()));
    }

    @Test
    public void aReadOnlyFileIsNotReplaced() throws Exception
    {
        File dir = Files.createTempDirectory("gpf").toFile();
        File onDisk = new File(dir, "project.greenfoot");
        GreenfootProjectFile file = new GreenfootProjectFile(dir);
        assertTrue(file.create());
        file.save(props("main.class", "MyWorld"));
        assertTrue(onDisk.setWritable(false));
        try
        {
            // Unchanged: nothing to do, no complaint
            file.save(props("main.class", "MyWorld"));
            try
            {
                file.save(props("main.class", "OtherWorld"));
                fail("a read-only project file must not be replaced");
            }
            catch (IOException e)
            {
                assertTrue(e.getMessage().contains("not writable"));
            }
            assertTrue(text(onDisk).contains("main.class=MyWorld"));
        }
        finally
        {
            onDisk.setWritable(true);
        }
    }
}
