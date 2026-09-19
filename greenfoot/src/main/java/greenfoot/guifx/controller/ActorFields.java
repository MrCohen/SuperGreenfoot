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
package greenfoot.guifx.controller;

import bluej.debugger.DebuggerField;
import bluej.debugger.DebuggerObject;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * An actor's position, rotation and depth, read with the debugger straight from the
 * fields of greenfoot.Actor (as the debugger's actor highlight does), so no user code
 * such as an overridden getX() runs.  Fields that a scenario's Actor class lacks are
 * left as NaN.
 */
@OnThread(Tag.Any)
public final class ActorFields
{
    private static final String ACTOR = "greenfoot.Actor";
    private static final String WORLD = "greenfoot.World";

    /** getX() and getY() */
    public final int x, y;
    /** getPreciseX() and getPreciseY(); NaN if unknown. */
    public final double preciseX, preciseY;
    /** getPreciseRotation(); NaN if unknown (then {@link #rotation} applies). */
    public final double preciseRotation;
    /** getRotation() */
    public final int rotation;
    /** The rotation the image is drawn at; NaN if unknown. */
    public final double imageRotation;
    /** getZ(); NaN if unknown. */
    public final double z;
    public final int imageWidth, imageHeight;
    public final int cellSize;

    private ActorFields(int x, int y, double preciseX, double preciseY, double preciseRotation, int rotation,
                        double imageRotation, double z, int imageWidth, int imageHeight, int cellSize)
    {
        this.x = x;
        this.y = y;
        this.preciseX = preciseX;
        this.preciseY = preciseY;
        this.preciseRotation = preciseRotation;
        this.rotation = rotation;
        this.imageRotation = imageRotation;
        this.z = z;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.cellSize = cellSize;
    }

    /**
     * Read the fields of the given actor.  Returns null if the actor is not in a world
     * (it has been removed), or its fields cannot be read (the debug VM has gone, or the
     * object has been collected).  Call off the FX thread: it talks to the debug VM.
     */
    public static ActorFields read(DebuggerObject actor)
    {
        try
        {
            if (actor == null || actor.isNullObject())
            {
                return null;
            }
            List<DebuggerField> fields = actor.getFields();
            DebuggerObject world = objectField(fields, ACTOR, "world");
            if (world == null || world.isNullObject())
            {
                return null;
            }
            List<DebuggerField> worldFields = world.getFields();
            int cellSize = (int) number(worldFields, WORLD, "cellSize", 1);
            return new ActorFields(
                    (int) number(fields, ACTOR, "x", 0),
                    (int) number(fields, ACTOR, "y", 0),
                    number(fields, ACTOR, "preciseX", Double.NaN),
                    number(fields, ACTOR, "preciseY", Double.NaN),
                    number(fields, ACTOR, "preciseRotation", Double.NaN),
                    (int) number(fields, ACTOR, "rotation", 0),
                    number(fields, ACTOR, "imageRotation", Double.NaN),
                    number(fields, ACTOR, "z", Double.NaN),
                    (int) number(fields, ACTOR, "imageWidth", 0),
                    (int) number(fields, ACTOR, "imageHeight", 0),
                    Math.max(1, cellSize));
        }
        catch (RuntimeException e)
        {
            // The VM went away or the object was collected while we were reading:
            return null;
        }
    }

    /** The centre of the actor in world pixels (x). */
    public double pixelX()
    {
        double cellX = Double.isNaN(preciseX) ? x : preciseX;
        return cellX * cellSize + cellSize / 2.0;
    }

    /** The centre of the actor in world pixels (y). */
    public double pixelY()
    {
        double cellY = Double.isNaN(preciseY) ? y : preciseY;
        return cellY * cellSize + cellSize / 2.0;
    }

    /** The angle the actor's image is drawn at, in degrees. */
    public double drawnRotation()
    {
        if (!Double.isNaN(imageRotation))
        {
            return imageRotation;
        }
        return Double.isNaN(preciseRotation) ? rotation : preciseRotation;
    }

    private static DebuggerObject objectField(List<DebuggerField> fields, String className, String name)
    {
        for (DebuggerField f : fields)
        {
            if (f.getDeclaringClassName().equals(className) && f.getName().equals(name))
            {
                return f.getValueObject();
            }
        }
        return null;
    }

    private static double number(List<DebuggerField> fields, String className, String name, double missing)
    {
        for (DebuggerField f : fields)
        {
            if (f.getDeclaringClassName().equals(className) && f.getName().equals(name))
            {
                try
                {
                    return Double.parseDouble(f.getValueString());
                }
                catch (NumberFormatException e)
                {
                    return missing;
                }
            }
        }
        return missing;
    }
}
