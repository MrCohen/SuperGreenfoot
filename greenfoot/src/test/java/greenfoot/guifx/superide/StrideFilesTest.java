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
package greenfoot.guifx.superide;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Tests for {@link StrideFiles}.
 */
public class StrideFilesTest extends TestCase
{
    private File write(String content) throws IOException
    {
        File file = File.createTempFile("StrideFilesTest", ".stride");
        file.deleteOnExit();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    public void testSuperclassIsRead() throws IOException
    {
        File file = write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<class name=\"Pengu\" extends=\"Mover\" extends-java=\"Mover\" enable=\"true\" strideversion=\"1\">"
                + "<imports/><implements/><fields/><constructors/><methods/></class>");
        assertEquals("Mover", StrideFiles.superclassOf(file));
    }

    public void testNoSuperclass() throws IOException
    {
        File file = write("<class name=\"Utility\" enable=\"true\" strideversion=\"1\"><methods/></class>");
        assertNull(StrideFiles.superclassOf(file));
    }

    public void testUnreadableFiles() throws IOException
    {
        assertNull(StrideFiles.superclassOf(null));
        assertNull(StrideFiles.superclassOf(new File("does-not-exist.stride")));
        assertNull(StrideFiles.superclassOf(write("<class name=\"Broken\" extends=\"Mover\"")));
        // A DOCTYPE (never in a Stride file) is refused rather than processed:
        assertNull(StrideFiles.superclassOf(write("<?xml version=\"1.0\"?><!DOCTYPE class [<!ENTITY x \"Mover\">]>"
                + "<class name=\"Evil\" extends=\"&x;\"/>")));
    }
}
