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

import javafx.scene.image.Image;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * One scenario class as the SuperGreenfoot IDE's class list shows it.
 */
@OnThread(Tag.Any)
public final class ClassEntry
{
    private final String name;
    private final String superName;
    private final Image image;

    /**
     * @param name       the class's simple name
     * @param superName  the superclass's name ("World", "greenfoot.Actor",
     *                   another scenario class...), or null for none
     * @param image      the class's image, or null to show a lettered tile
     */
    public ClassEntry(String name, String superName, Image image)
    {
        this.name = name;
        this.superName = superName;
        this.image = image;
    }

    public String getName()
    {
        return name;
    }

    /** The superclass name without a "greenfoot." prefix, or null. */
    public String getSuperName()
    {
        return ClassTreeModel.simple(superName);
    }

    public Image getImage()
    {
        return image;
    }
}
