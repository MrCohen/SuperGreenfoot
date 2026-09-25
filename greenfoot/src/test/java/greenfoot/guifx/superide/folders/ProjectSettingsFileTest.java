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

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * supergreenfoot.properties keeps the comment lines above its first setting
 * across a rewrite, and gets the stock header when it has none.
 */
public class ProjectSettingsFileTest
{
    @Test
    public void leadingCommentsAreTheLinesBeforeTheFirstSetting()
    {
        List<String> lines = Arrays.asList("# one", "", "! two", "version=1", "# not kept", "x=2");
        assertEquals("# one\n! two\n", ProjectSettingsFile.leadingComments(lines));
        assertEquals("", ProjectSettingsFile.leadingComments(Arrays.asList("version=1", "# after")));
        assertEquals("", ProjectSettingsFile.leadingComments(Arrays.asList()));
    }

    @Test
    public void ownCommentBlockSurvivesARewrite() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        File file = new File(dir, ProjectSettingsFile.FILE_NAME);
        Files.write(file.toPath(), Arrays.asList(
                "# Mr. Cohen's notes: folders were set up on day one.",
                "# Do not edit by hand.",
                "version=1",
                "# a comment in the middle",
                "folder.Enemies=Boar,Wolf"), StandardCharsets.ISO_8859_1);

        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        assertEquals("Boar,Wolf", settings.get("folder.Enemies"));
        settings.put("folder.Enemies", "Boar,Wolf,Troll");
        settings.save();

        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("# Mr. Cohen's notes: folders were set up on day one.\n# Do not edit by hand.\n"));
        assertTrue(text.contains("folder.Enemies=Boar,Wolf,Troll"));
        assertFalse("comments after the first setting are not kept", text.contains("in the middle"));
        assertFalse("the stock header is not added on top of the file's own", text.contains("Stock Greenfoot ignores"));
    }

    @Test
    public void aFreshFileGetsTheStockHeader() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        settings.put("folder.Enemies", "Boar");
        settings.save();
        String text = new String(Files.readAllBytes(new File(dir, ProjectSettingsFile.FILE_NAME).toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("# Super Greenfoot settings"));
        assertTrue(text.contains("rewrites this file"));
    }

    @Test
    public void anUnreadableFileIsCopiedAsideBeforeItIsReplaced() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        File file = new File(dir, ProjectSettingsFile.FILE_NAME);
        // A malformed unicode escape makes Properties.load throw
        byte[] broken = "version=1\nclass.Boar.folder=Enemies\nbad=\\u12\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file.toPath(), broken);

        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        assertTrue(settings.isUnreadable());
        settings.put("ui.panel.classes.open", "false");
        settings.save();

        File backup = new File(dir, ProjectSettingsFile.FILE_NAME + ".bak");
        assertTrue(backup.isFile());
        assertTrue(Arrays.equals(broken, Files.readAllBytes(backup.toPath())));
        assertFalse(settings.isUnreadable());
        assertEquals("false", ProjectSettingsFile.load(dir).get("ui.panel.classes.open"));

        // A later save of a readable file makes no further copies
        settings.put("ui.panel.classes.open", "true");
        settings.save();
        assertFalse(new File(dir, ProjectSettingsFile.FILE_NAME + ".bak2").exists());

        // A second unreadable file never overwrites the first copy
        Files.write(file.toPath(), broken);
        ProjectSettingsFile again = ProjectSettingsFile.load(dir);
        again.put("x", "1");
        again.save();
        assertTrue(Arrays.equals(broken, Files.readAllBytes(backup.toPath())));
        assertTrue(new File(dir, ProjectSettingsFile.FILE_NAME + ".bak2").isFile());
    }

    @Test
    public void defaultValuesDoNotCreateTheFile() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        File file = new File(dir, ProjectSettingsFile.FILE_NAME);
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        settings.putUnlessDefault("ui.panel.classes.open", "true", "true");
        settings.putUnlessDefault("ui.world.zoom", "fit", "fit");
        assertFalse(settings.isDirty());
        settings.save();
        assertFalse("opening and closing with defaults writes nothing", file.exists());

        settings.putUnlessDefault("ui.world.zoom", "pixel", "fit");
        assertTrue(settings.isDirty());
        settings.save();
        assertEquals("pixel", ProjectSettingsFile.load(dir).get("ui.world.zoom"));

        // Once present, going back to the default is a change that is written
        ProjectSettingsFile reloaded = ProjectSettingsFile.load(dir);
        reloaded.putUnlessDefault("ui.world.zoom", "fit", "fit");
        assertTrue(reloaded.isDirty());
        reloaded.save();
        assertEquals("fit", ProjectSettingsFile.load(dir).get("ui.world.zoom"));
    }

    @Test
    public void saveLeavesNoTemporaryFiles() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        settings.put("folder.Enemies", "Boar");
        settings.save();
        settings.put("folder.Enemies", "Wolf");
        settings.save();
        assertEquals(Arrays.asList(ProjectSettingsFile.FILE_NAME), Arrays.asList(dir.list()));
    }
}
