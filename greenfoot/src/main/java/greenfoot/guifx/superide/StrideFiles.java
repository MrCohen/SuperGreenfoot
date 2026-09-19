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

import javax.xml.parsers.DocumentBuilderFactory;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Reads what the new IDE needs straight from a Stride class's source file.  A Stride
 * class's Java source is generated when it compiles, so until then (or while it is out
 * of date) the Stride file is the only place that names, e.g., its superclass.
 */
@OnThread(Tag.Any)
public final class StrideFiles
{
    private StrideFiles()
    {
    }

    /**
     * The superclass named in a Stride file's class element (its "extends"), or null if
     * it names none or the file cannot be read.
     */
    public static String superclassOf(File strideFile)
    {
        if (strideFile == null || !strideFile.isFile())
        {
            return null;
        }
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // A Stride file has no DTD; refuse one rather than fetch or expand anything:
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            String sup = factory.newDocumentBuilder().parse(strideFile).getDocumentElement().getAttribute("extends");
            return sup.isEmpty() ? null : sup;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
