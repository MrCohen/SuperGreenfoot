/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.collision;

import greenfoot.Actor;
import greenfoot.ActorVisitor;

/**
 * A collision query to check for actors within a certain range of a certain
 * point
 * 
 * @author Davin McCall
 */
public class InRangeQuery
    implements CollisionQuery
{
    /** x-coordinate of the center of the circle. In pixels. */
    private int x;
    /** y-coordinate of the center of the circle. In pixels. */
    private int y;
    /** radius of the circle. In pixels. */
    private int r;

    /**
     * Initialise with the given circle. Units are in pixels!
     */
    public void init(int x, int y, int r)
    {
        this.x = x;
        this.y = y;
        this.r = r;
    }

    /**
     * Return true if the distance from some point on the actor to the center of
     * the circle, is less than or equal to the radius of the circle.
     */
    public boolean checkCollision(Actor actor)
    {
        int actorX = ActorVisitor.toPixelX(actor, ActorVisitor.getX(actor));
        int actorY = ActorVisitor.toPixelY(actor, ActorVisitor.getY(actor));   
        
        if (r < 0) {
            // No distance can be within a negative radius. Guarded because the
            // comparison below would square it and let some actors through.
            return false;
        }

        long dx = actorX - x;
        long dy = actorY - y;
        // Same result as (int) Math.sqrt(dx*dx + dy*dy) <= r, without the square root:
        // floor(sqrt(d2)) <= r  <=>  d2 < (r + 1)^2
        long d2 = dx * dx + dy * dy;
        long lim = (long) r + 1;
        return d2 < lim * lim;
    }

}
