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

import greenfoot.guifx.superide.OutputPane.Kind;
import greenfoot.guifx.superide.OutputPane.Line;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests how the Output panel turns printed chunks (which need not end at a line
 * break) into lines.
 */
public class OutputLinesTest extends TestCase
{
    private final List<Line> lines = new ArrayList<>();
    private boolean open = false;

    private void add(String chunk, Kind kind)
    {
        open = OutputPane.addChunk(lines, open, chunk, kind);
    }

    private void assertLines(String... expected)
    {
        assertEquals(expected.length, lines.size());
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals(expected[i], lines.get(i).text);
        }
    }

    public void testWholeLines()
    {
        add("one\ntwo\n", Kind.PRINTED);
        assertLines("one", "two");
        assertFalse(open);
    }

    public void testPrintThenPrintln()
    {
        // System.out.print("Score: "); System.out.println(30);
        add("Score: ", Kind.PRINTED);
        assertTrue(open);
        add("30", Kind.PRINTED);
        add("\n", Kind.PRINTED);
        assertLines("Score: 30");
        assertFalse(open);
    }

    public void testBlankLine()
    {
        add("a\n\nb\n", Kind.PRINTED);
        assertLines("a", "", "b");
        add("\n", Kind.PRINTED);
        assertLines("a", "", "b", "");
    }

    public void testErrorDoesNotJoinPrintedLine()
    {
        add("partial", Kind.PRINTED);
        add("oops\n", Kind.ERROR);
        assertLines("partial", "oops");
        assertEquals(Kind.PRINTED, lines.get(0).kind);
        assertEquals(Kind.ERROR, lines.get(1).kind);
        // The printed line was left unfinished, but the error came in between, so
        // more printed text starts a new line:
        add("more\n", Kind.PRINTED);
        assertLines("partial", "oops", "more");
    }

    public void testCarriageReturnsDropped()
    {
        add("line\r\n", Kind.PRINTED);
        assertLines("line");
    }
}
