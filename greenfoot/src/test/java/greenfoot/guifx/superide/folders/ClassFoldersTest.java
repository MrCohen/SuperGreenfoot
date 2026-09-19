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
package greenfoot.guifx.superide.folders;

import junit.framework.TestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the SuperGreenfoot IDE's virtual class folders and the per-scenario
 * settings file they are stored in.
 */
public class ClassFoldersTest extends TestCase
{
    private static final List<String> CLASSES = List.of("MyWorld", "Player", "Enemy", "Slime", "Coin", "Utility");

    private File dir;

    @Override
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory("sgf-folders").toFile();
    }

    @Override
    protected void tearDown() throws Exception
    {
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : files)
            {
                f.delete();
            }
        }
        dir.delete();
    }

    private File settingsFile()
    {
        return new File(dir, ProjectSettingsFile.FILE_NAME);
    }

    private void writeRaw(String text) throws Exception
    {
        Files.write(settingsFile().toPath(), text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private String readRaw() throws Exception
    {
        return new String(Files.readAllBytes(settingsFile().toPath()), StandardCharsets.ISO_8859_1);
    }

    /** Save the folders to disk and read them back through a fresh load. */
    private ClassFolders roundTrip(ClassFolders folders, List<String> classes) throws Exception
    {
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        folders.writeTo(settings, classes);
        settings.save();
        return ClassFolders.load(ProjectSettingsFile.load(dir));
    }

    private ClassFolders sample()
    {
        ClassFolders folders = new ClassFolders();
        folders.addFolder("Worlds");
        folders.addFolder("Characters");
        folders.addFolder("Pickups");
        folders.setFolder("MyWorld", "Worlds");
        folders.setFolder("Player", "Characters");
        folders.setFolder("Enemy", "Characters");
        folders.setFolder("Slime", "Characters");
        folders.setFolder("Coin", "Pickups");
        folders.setOpen("Pickups", false);
        return folders;
    }

    public void testMissingFileMeansNoFoldersAndNothingIsWritten() throws Exception
    {
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        assertFalse(settings.isUnreadable());
        ClassFolders folders = ClassFolders.load(settings);
        assertTrue(folders.getFolderNames().isEmpty());
        assertEquals(List.of("Coin", "Enemy", "MyWorld", "Player", "Slime", "Utility"), folders.getUnfiled(CLASSES));

        folders.writeTo(settings, CLASSES);
        settings.save();
        assertFalse("an empty model must not create the file", settingsFile().exists());
    }

    public void testRoundTripKeepsOrderOpenStateAndAssignments() throws Exception
    {
        ClassFolders loaded = roundTrip(sample(), CLASSES);
        assertEquals(List.of("Worlds", "Characters", "Pickups"), loaded.getFolderNames());
        assertTrue(loaded.isOpen("Worlds"));
        assertFalse(loaded.isOpen("Pickups"));
        assertEquals(List.of("Enemy", "Player", "Slime"), loaded.getClassesIn("Characters", CLASSES));
        assertEquals("Pickups", loaded.getFolder("Coin"));
        assertEquals(List.of("Utility"), loaded.getUnfiled(CLASSES));
        assertTrue(readRaw().contains("version=1\n"));
    }

    public void testSaveIsDeterministicWithNumericKeyOrder() throws Exception
    {
        ClassFolders folders = new ClassFolders();
        for (int i = 0; i < 12; i++)
        {
            folders.addFolder("F" + i);
        }
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        folders.writeTo(settings, CLASSES);
        settings.save();
        String first = readRaw();
        assertFalse("no timestamp line", first.matches("(?s).*\\d{2}:\\d{2}:\\d{2}.*"));
        assertFalse("'\\n' line ends only", first.contains("\r"));
        assertTrue(first.indexOf("folders.9.name") < first.indexOf("folders.10.name"));
        assertTrue(first.indexOf("folders.2.name") < first.indexOf("folders.11.name"));

        // Saving the same model again writes the same bytes (and a no-op save writes nothing).
        settingsFile().delete();
        ProjectSettingsFile again = ProjectSettingsFile.load(dir);
        folders.writeTo(again, CLASSES);
        again.save();
        assertEquals(first, readRaw());
        long modified = settingsFile().lastModified();
        ProjectSettingsFile third = ProjectSettingsFile.load(dir);
        ClassFolders.load(third).writeTo(third, CLASSES);
        assertFalse(third.isDirty());
        third.save();
        assertEquals(modified, settingsFile().lastModified());
    }

    public void testUnknownKeysSurviveASave() throws Exception
    {
        writeRaw("# from a newer SuperGreenfoot\n"
            + "version=3\n"
            + "view.leftPanel.open=false\n"
            + "future.thing=\\ leading space and = sign\n"
            + "class.Player.colour=orange\n"
            + "folders.count=1\n"
            + "folders.0.name=Old\n");
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        ClassFolders folders = ClassFolders.load(settings);
        folders.addFolder("New");
        folders.setFolder("Player", "New");
        folders.writeTo(settings, CLASSES);
        settings.save();

        ProjectSettingsFile reread = ProjectSettingsFile.load(dir);
        assertEquals("3", reread.get("version"));
        assertEquals("false", reread.get("view.leftPanel.open"));
        assertEquals(" leading space and = sign", reread.get("future.thing"));
        assertEquals("orange", reread.get("class.Player.colour"));
        assertEquals(List.of("Old", "New"), ClassFolders.load(reread).getFolderNames());
    }

    public void testRenameFolderMovesItsClasses() throws Exception
    {
        ClassFolders folders = sample();
        folders.renameFolder("characters", "  Cast ");
        assertEquals(List.of("Worlds", "Cast", "Pickups"), folders.getFolderNames());
        assertEquals("Cast", folders.getFolder("Player"));
        ClassFolders loaded = roundTrip(folders, CLASSES);
        assertEquals(List.of("Enemy", "Player", "Slime"), loaded.getClassesIn("Cast", CLASSES));
        assertFalse(loaded.hasFolder("Characters"));
    }

    public void testDeleteFolderUnfilesClassesWithoutDeletingThem() throws Exception
    {
        ClassFolders folders = sample();
        folders.deleteFolder("Characters");
        assertEquals(List.of("Worlds", "Pickups"), folders.getFolderNames());
        assertEquals(List.of("Enemy", "Player", "Slime", "Utility"), folders.getUnfiled(CLASSES));
        ClassFolders loaded = roundTrip(folders, CLASSES);
        assertEquals("", loaded.getFolder("Player"));
        assertFalse(readRaw().contains("class.Player.folder"));
    }

    public void testMoveFolderAndUnfileClass() throws Exception
    {
        ClassFolders folders = sample();
        folders.moveFolder("Pickups", 0);
        assertEquals(List.of("Pickups", "Worlds", "Characters"), folders.getFolderNames());
        folders.moveFolder("Pickups", 99);
        assertEquals(List.of("Worlds", "Characters", "Pickups"), folders.getFolderNames());
        folders.setFolder("Coin", "");
        assertEquals("", folders.getFolder("Coin"));
        folders.addFolder("First", 0);
        assertEquals(List.of("First", "Worlds", "Characters", "Pickups"), roundTrip(folders, CLASSES).getFolderNames());
    }

    public void testClassRenameAndRemoval()
    {
        ClassFolders folders = sample();
        folders.classRenamed("Player", "Hero");
        assertEquals("Characters", folders.getFolder("Hero"));
        assertEquals("", folders.getFolder("Player"));
        folders.classRemoved("Hero");
        assertEquals("", folders.getFolder("Hero"));
    }

    public void testClassInMissingFolderIsUnfiledButKept() throws Exception
    {
        writeRaw("folders.count=1\nfolders.0.name=Worlds\nclass.Player.folder=Gone\nclass.MyWorld.folder=worlds\n");
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        ClassFolders folders = ClassFolders.load(settings);
        assertEquals("", folders.getFolder("Player"));
        assertEquals("folder names match ignoring case", "Worlds", folders.getFolder("MyWorld"));
        folders.addFolder("Extra");
        folders.writeTo(settings, CLASSES);
        settings.save();
        assertEquals("Gone", ProjectSettingsFile.load(dir).get("class.Player.folder"));
        // Recreating the folder brings the class back.
        ClassFolders again = ClassFolders.load(ProjectSettingsFile.load(dir));
        again.addFolder("Gone");
        assertEquals("Gone", again.getFolder("Player"));
    }

    public void testStaleClassEntriesDropOnlyWhenTheClassListIsGiven() throws Exception
    {
        ClassFolders folders = sample();
        folders.setFolder("Removed", "Worlds");
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        folders.writeTo(settings, null);
        settings.save();
        assertEquals("Worlds", ProjectSettingsFile.load(dir).get("class.Removed.folder"));

        ProjectSettingsFile withList = ProjectSettingsFile.load(dir);
        ClassFolders.load(withList).writeTo(withList, CLASSES);
        withList.save();
        assertNull(ProjectSettingsFile.load(dir).get("class.Removed.folder"));
        assertEquals("Characters", ProjectSettingsFile.load(dir).get("class.Player.folder"));
    }

    public void testUnreadableFileIsIgnoredAndLeftAloneUntilFoldersChange() throws Exception
    {
        String corrupt = "folders.count=1\nfolders.0.name=\\uZZZZ\n";
        writeRaw(corrupt);
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        assertTrue(settings.isUnreadable());
        ClassFolders folders = ClassFolders.load(settings);
        assertTrue(folders.getFolderNames().isEmpty());

        // Opening and saving without changes leaves the file as it was.
        folders.writeTo(settings, CLASSES);
        settings.save();
        assertEquals(corrupt, readRaw());

        // A real change replaces it.
        folders.addFolder("Worlds");
        folders.writeTo(settings, CLASSES);
        settings.save();
        assertFalse(settings.isUnreadable());
        assertEquals(List.of("Worlds"), ClassFolders.load(ProjectSettingsFile.load(dir)).getFolderNames());
    }

    public void testInvalidAndDuplicateFolderEntriesAreSkippedOnLoad() throws Exception
    {
        writeRaw("folders.count=5\nfolders.0.name=Worlds\nfolders.1.name=WORLDS\nfolders.2.name=a/b\n"
            + "folders.4.name=  Pickups  \nfolders.4.open=FALSE\n");
        ClassFolders folders = ClassFolders.load(ProjectSettingsFile.load(dir));
        assertEquals(List.of("Worlds", "Pickups"), folders.getFolderNames());
        assertFalse(folders.isOpen("pickups"));
    }

    public void testFolderNameRules()
    {
        ClassFolders folders = sample();
        assertNotNull(folders.checkName("", null));
        assertNotNull(folders.checkName("   ", null));
        assertNotNull(folders.checkName("a/b", null));
        assertNotNull(folders.checkName("a\\b", null));
        assertNotNull(folders.checkName("tab\there", null));
        assertNotNull(folders.checkName("x".repeat(ClassFolders.MAX_NAME_LENGTH + 1), null));
        assertNull(folders.checkName("x".repeat(ClassFolders.MAX_NAME_LENGTH), null));
        assertNotNull("duplicates ignore case", folders.checkName("worlds", null));
        assertNull("a folder may be renamed to a new spelling of its name", folders.checkName("WORLDS", "Worlds"));
        assertNull(folders.checkName("Enemies & Bosses: #1!", null));
        try
        {
            folders.addFolder("Pickups");
            fail("duplicate folder accepted");
        }
        catch (IllegalArgumentException expected)
        {
        }
        try
        {
            folders.setFolder("Player", "Nowhere");
            fail("class moved into a missing folder");
        }
        catch (IllegalArgumentException expected)
        {
        }
    }

    public void testSpecialAndNonAsciiNamesRoundTrip() throws Exception
    {
        ClassFolders folders = new ClassFolders();
        folders.addFolder("Énemis = bad: #1!");
        folders.addFolder("敵");
        folders.setFolder("Enemy", "敵");
        ClassFolders loaded = roundTrip(folders, CLASSES);
        assertEquals(List.of("Énemis = bad: #1!", "敵"), loaded.getFolderNames());
        assertEquals("敵", loaded.getFolder("Enemy"));
        String raw = readRaw();
        for (char c : raw.toCharArray())
        {
            assertTrue("file is plain ASCII", c < 0x80);
        }
    }

    public void testSaveLeavesNoTemporaryFiles() throws Exception
    {
        roundTrip(sample(), CLASSES);
        roundTrip(sample(), CLASSES);
        File[] files = dir.listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        assertEquals(ProjectSettingsFile.FILE_NAME, files[0].getName());
    }

    public void testListenersHearChangesButNotNoOps()
    {
        ClassFolders folders = sample();
        List<String> heard = new ArrayList<>();
        ClassFolders.Listener listener = () -> heard.add("changed");
        folders.addListener(listener);
        folders.setFolder("Player", "Characters");
        folders.setOpen("Worlds", true);
        folders.moveFolder("Worlds", 0);
        assertTrue(heard.isEmpty());
        folders.setFolder("Player", "Worlds");
        folders.setOpen("Worlds", false);
        folders.renameFolder("Pickups", "Loot");
        assertEquals(3, heard.size());
        folders.removeListener(listener);
        folders.deleteFolder("Loot");
        assertEquals(3, heard.size());
    }
}
