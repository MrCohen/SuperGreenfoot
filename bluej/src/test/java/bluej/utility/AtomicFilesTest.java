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
package bluej.utility;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class AtomicFilesTest
{
    @Test
    public void replacesAndLeavesNoTemporaryFile() throws Exception
    {
        Path dir = Files.createTempDirectory("atomic");
        Path file = dir.resolve("project.greenfoot");
        Files.write(file, "old".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.write(file, "new".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
        assertEquals(1, dir.toFile().list().length);
    }

    @Test
    public void aFolderThatTakesNoNewFilesIsWrittenInPlace() throws Exception
    {
        // A shared scenario folder may let a user write its files but not add any:
        // upstream wrote in place there, and so must we.
        Path dir = Files.createTempDirectory("atomic");
        assumeTrue(Files.getFileAttributeView(dir, java.nio.file.attribute.PosixFileAttributeView.class) != null);
        Path file = dir.resolve("project.greenfoot");
        Files.write(file, "old".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        try
        {
            AtomicFiles.write(file, "new".getBytes(StandardCharsets.UTF_8));
            assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
        }
        finally
        {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
