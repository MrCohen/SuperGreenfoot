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
package bluej.views;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A comment list built straight from the parser's properties matches one
 * read back from those properties stored as a .ctxt file.
 */
public class CommentListTest
{
    private Properties sample()
    {
        Properties p = new Properties();
        p.setProperty("numComments", "2");
        p.setProperty("comment0.target", "Boar(int, int)");
        p.setProperty("comment0.text", "A boar at the given cell.");
        p.setProperty("comment0.params", "x y");
        p.setProperty("comment1.target", "void act()");
        p.setProperty("comment1.text", "One step.");
        return p;
    }

    @Test
    public void fromPropertiesMatchesFromStream() throws Exception
    {
        CommentList direct = new CommentList();
        direct.load(sample());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sample().store(out, "BlueJ class context");
        CommentList viaFile = new CommentList();
        viaFile.load(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(2, direct.numComments());
        assertEquals(viaFile.numComments(), direct.numComments());
        Iterator<Comment> a = direct.getComments();
        Iterator<Comment> b = viaFile.getComments();
        while (a.hasNext())
        {
            assertTrue(b.hasNext());
            Comment ca = a.next();
            Comment cb = b.next();
            assertEquals(cb.getTarget(), ca.getTarget());
            assertEquals(cb.getText(), ca.getText());
            assertArrayEquals(cb.getParamNames(), ca.getParamNames());
        }
        assertFalse(b.hasNext());
    }
}
