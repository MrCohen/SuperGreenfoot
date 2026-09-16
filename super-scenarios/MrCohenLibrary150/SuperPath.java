import greenfoot.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * A simple, friendly Path that one or more Actors can follow.
 *
 * <h2>The quick version (no rotation at all)</h2>
 * <pre>
 *     // 1. Build a path out of points (in the World constructor, for example):
 *     Path path = new Path();
 *     path.addPoint(100, 200);
 *     path.addPoint(300, 100);
 *     path.addPoint(500, 400);
 *
 *     // 2. In your Actor's act() method, follow it:
 *     path.follow(this, 3);    // move 3 pixels along the path each act
 * </pre>
 *
 * That is the whole "easy mode" - no resolution, no setup call, no rotation.
 * The Actor slides along the path at the speed you ask for. When it reaches the
 * end it stops (unless the path loops - see {@link #setLoop}).
 *
 * <h2>Adding rotation (optional)</h2>
 * <pre>
 *     path.followAndTurn(this, 3);        // also FACE the direction of travel (snaps instantly)
 *     path.followAndTurn(this, 3, 2.5);   // ...but turn smoothly, at most 2.5 degrees per act
 * </pre>
 *
 * <h2>Sharing a path</h2>
 * The same Path object can be followed by any number of Actors at the same time.
 * Each Actor keeps its OWN progress automatically, so a fast follower and a slow
 * follower can share one path without interfering with each other.
 *
 * <h2>Doing your own thing (advanced)</h2>
 * If you would rather move the Actor yourself, ask the Path where a point is:
 * <pre>
 *     Coordinate c = path.getPointAtDistance(120);  // 120 px along the path
 *     Coordinate c = path.getPointAtFraction(0.5);  // exactly halfway along
 *     double total = path.getLength();              // how long the path is
 * </pre>
 * Each returned {@link Coordinate} carries the x, the y, and the angle of travel
 * at that spot, so you can use as much or as little of it as you like.
 *
 * @author Jordan Cohen
 * @version June 2026
 */
public class SuperPath
{
    // The waypoints that define the path.
    private ArrayList<Coordinate> points;

    // Per-Actor progress, measured as distance travelled along the path.
    // Using an IdentityHashMap means each Actor that follows this path keeps its
    // own progress, so one Path can be safely shared by many followers.
    private IdentityHashMap<Actor, Double> progress;

    // When true, the path joins its last point back to its first and followers
    // wrap around forever instead of stopping at the end.
    private boolean loop;

    // Cached arc-length information so we can sample the path by distance.
    // Rebuilt automatically whenever the points or loop setting change.
    private double[] cumulative;   // cumulative[i] = distance from start to point i
    private double totalLength;
    private boolean dirty;

    // ----------------------------------------------------------------- //
    //  Constructors
    // ----------------------------------------------------------------- //

    /**
     * Create a new, empty path that stops at its end (does not loop).
     * Add points with {@link #addPoint}.
     */
    public SuperPath ()
    {
        points = new ArrayList<Coordinate>();
        progress = new IdentityHashMap<Actor, Double>();
        loop = false;
        dirty = true;
    }

    /**
     * Create a new, empty path, choosing now whether it should loop.
     *
     * @param loop  true to make followers circle the path forever, false to stop at the end
     */
    public SuperPath (boolean loop)
    {
        this();
        this.loop = loop;
    }

    // ----------------------------------------------------------------- //
    //  Building the path
    // ----------------------------------------------------------------- //

    /**
     * Add a waypoint to the end of the path.
     *
     * @param x  the x coordinate of the new point
     * @param y  the y coordinate of the new point
     */
    public void addPoint (int x, int y)
    {
        points.add(new Coordinate(x, y));
        dirty = true;
    }

    /**
     * Add a waypoint to the end of the path, using precise (double) coordinates.
     *
     * @param x  the x coordinate of the new point
     * @param y  the y coordinate of the new point
     */
    public void addPoint (double x, double y)
    {
        points.add(new Coordinate(x, y));
        dirty = true;
    }

    /**
     * Add an existing Coordinate as a waypoint.
     *
     * @param point  the coordinate to add (only its x and y are used)
     */
    public void addPoint (Coordinate point)
    {
        points.add(new Coordinate(point.getPreciseX(), point.getPreciseY()));
        dirty = true;
    }

    /**
     * Remove every point, leaving an empty path. Also forgets every follower's
     * progress, since there is nothing left to follow.
     */
    public void clear ()
    {
        points.clear();
        progress.clear();
        dirty = true;
    }

    /**
     * Choose whether followers loop forever or stop at the end of the path.
     *
     * @param loop  true to loop, false to stop at the end
     */
    public void setLoop (boolean loop)
    {
        if (this.loop != loop) {
            this.loop = loop;
            dirty = true;
        }
    }

    // ----------------------------------------------------------------- //
    //  The easy way: have an Actor follow the path
    // ----------------------------------------------------------------- //

    /**
     * Move an Actor along the path WITHOUT changing its rotation. This is the
     * friendly, everyday method - just call it from the Actor's act() method.
     *
     * <p>The first time a given Actor follows the path it is placed at the start.
     * Each later call moves it forward by <code>speed</code> pixels. The Actor's
     * own progress is remembered, so several Actors can share one path.</p>
     *
     * @param mover  the Actor to move
     * @param speed  how many pixels to travel along the path this act
     * @return true if the Actor has reached the end of a non-looping path, false otherwise
     */
    public boolean follow (Actor mover, double speed)
    {
        Coordinate spot = advance(mover, speed);
        if (spot == null) {
            return false;
        }
        place(mover, spot);
        return isDone(mover);
    }

    /**
     * Move an Actor along the path AND turn it to face the direction of travel,
     * snapping to the new angle instantly.
     *
     * @param mover  the Actor to move
     * @param speed  how many pixels to travel along the path this act
     * @return true if the Actor has reached the end of a non-looping path, false otherwise
     */
    public boolean followAndTurn (Actor mover, double speed)
    {
        Coordinate spot = advance(mover, speed);
        if (spot == null) {
            return false;
        }
        place(mover, spot);
        face(mover, spot.getPreciseRotation());
        return isDone(mover);
    }

    /**
     * Move an Actor along the path and turn it SMOOTHLY toward the direction of
     * travel, rotating at most <code>turnSpeed</code> degrees this act. This is
     * the "advanced" version - use {@link #followAndTurn(Actor, double)} if you
     * want the Actor to face the right way immediately.
     *
     * @param mover      the Actor to move
     * @param speed      how many pixels to travel along the path this act
     * @param turnSpeed  the most degrees the Actor may turn this act (use a small number for a gentle turn)
     * @return true if the Actor has reached the end of a non-looping path, false otherwise
     */
    public boolean followAndTurn (Actor mover, double speed, double turnSpeed)
    {
        Coordinate spot = advance(mover, speed);
        if (spot == null) {
            return false;
        }
        place(mover, spot);
        turnToward(mover, spot.getPreciseRotation(), turnSpeed);
        return isDone(mover);
    }

    // ----------------------------------------------------------------- //
    //  Managing a follower's progress
    // ----------------------------------------------------------------- //

    /**
     * Put an Actor at the very start of the path and reset its progress, so the
     * next follow() call begins from the beginning.
     *
     * @param mover  the Actor to place
     */
    public void placeAtStart (Actor mover)
    {
        progress.put(mover, 0.0);
        Coordinate start = getStart();
        if (start != null) {
            place(mover, start);
        }
    }

    /**
     * Reset one Actor's progress back to the start of the path. The Actor is not
     * moved until the next follow() call.
     *
     * @param mover  the Actor whose progress should be reset
     */
    public void reset (Actor mover)
    {
        progress.remove(mover);
    }

    /**
     * Reset every follower's progress back to the start of the path.
     */
    public void resetAll ()
    {
        progress.clear();
    }

    /**
     * Forget an Actor completely. Call this when a follower is removed from the
     * world so the path does not hold on to it.
     *
     * @param mover  the Actor to forget
     */
    public void forget (Actor mover)
    {
        progress.remove(mover);
    }

    /**
     * How far along the path an Actor has travelled, in pixels.
     *
     * @param mover  the Actor to check
     * @return the distance travelled, or 0 if this Actor has not started yet
     */
    public double getProgress (Actor mover)
    {
        Double d = progress.get(mover);
        return d == null ? 0.0 : d;
    }

    /**
     * Jump an Actor to a chosen distance along the path. The Actor is not moved
     * until the next follow() call.
     *
     * @param mover     the Actor whose progress to set
     * @param distance  the distance along the path, in pixels
     */
    public void setProgress (Actor mover, double distance)
    {
        progress.put(mover, distance);
    }

    /**
     * Whether an Actor has reached the end of the path. Looping paths never end,
     * so this is always false when {@link #isLoop} is true.
     *
     * @param mover  the Actor to check
     * @return true if the Actor has reached the end of a non-looping path
     */
    public boolean isDone (Actor mover)
    {
        if (loop) {
            return false;
        }
        ensureBuilt();
        return getProgress(mover) >= totalLength && totalLength > 0;
    }

    // ----------------------------------------------------------------- //
    //  Asking the path about itself (great for doing your own movement)
    // ----------------------------------------------------------------- //

    /**
     * Find the point a given distance along the path. The returned Coordinate
     * also knows the angle of travel at that spot (see Coordinate.getRotation()).
     *
     * @param distance  the distance from the start, in pixels
     * @return a Coordinate at that distance, or null if the path has no points
     */
    public Coordinate getPointAtDistance (double distance)
    {
        ensureBuilt();
        if (points.isEmpty()) {
            return null;
        }
        if (points.size() == 1 || totalLength == 0) {
            Coordinate only = points.get(0);
            return new Coordinate(only.getPreciseX(), only.getPreciseY(), 0.0);
        }

        // Work out which distance we are really sampling.
        if (loop) {
            distance = distance % totalLength;
            if (distance < 0) {
                distance += totalLength;
            }
        } else {
            if (distance <= 0) {
                return pointOnSegment(0, 0.0);
            }
            if (distance >= totalLength) {
                return pointOnSegment(segmentCount() - 1, 1.0);
            }
        }

        // Find the segment that contains this distance.
        int seg = 0;
        while (seg < segmentCount() - 1 && cumulative[seg + 1] < distance) {
            seg++;
        }
        double segStart = cumulative[seg];
        double segLen = cumulative[seg + 1] - segStart;
        double t = (segLen == 0) ? 0.0 : (distance - segStart) / segLen;
        return pointOnSegment(seg, t);
    }

    /**
     * Find a point a fraction of the way along the path, where 0.0 is the start
     * and 1.0 is the end.
     *
     * @param fraction  a value from 0.0 (start) to 1.0 (end)
     * @return a Coordinate at that fraction of the path, or null if the path has no points
     */
    public Coordinate getPointAtFraction (double fraction)
    {
        ensureBuilt();
        return getPointAtDistance(fraction * totalLength);
    }

    /**
     * The total length of the path in pixels (including the closing segment back
     * to the start, if the path loops).
     *
     * @return the length of the path
     */
    public double getLength ()
    {
        ensureBuilt();
        return totalLength;
    }

    /**
     * The first point of the path, including the starting angle of travel.
     *
     * @return the start Coordinate, or null if the path is empty
     */
    public Coordinate getStart ()
    {
        if (points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            Coordinate p = points.get(0);
            return new Coordinate(p.getPreciseX(), p.getPreciseY(), 0.0);
        }
        return pointOnSegment(0, 0.0);
    }

    /**
     * The last point of the path, including the final angle of travel. On a
     * looping path this is the same place as the start.
     *
     * @return the end Coordinate, or null if the path is empty
     */
    public Coordinate getEnd ()
    {
        if (points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            Coordinate p = points.get(0);
            return new Coordinate(p.getPreciseX(), p.getPreciseY(), 0.0);
        }
        return pointOnSegment(segmentCount() - 1, 1.0);
    }

    /**
     * Get one of the waypoints you added, by its index (0 is the first point).
     *
     * @param index  the index of the waypoint
     * @return a copy of that waypoint, or null if the index is out of range
     */
    public Coordinate getPoint (int index)
    {
        if (index < 0 || index >= points.size()) {
            return null;
        }
        Coordinate p = points.get(index);
        return new Coordinate(p.getPreciseX(), p.getPreciseY());
    }

    /**
     * How many waypoints the path has.
     *
     * @return the number of points
     */
    public int getPointCount ()
    {
        return points.size();
    }

    /**
     * Whether the path has no points yet.
     *
     * @return true if the path is empty
     */
    public boolean isEmpty ()
    {
        return points.isEmpty();
    }

    /**
     * Whether followers loop around the path forever.
     *
     * @return true if the path loops
     */
    public boolean isLoop ()
    {
        return loop;
    }

    // ----------------------------------------------------------------- //
    //  Drawing the path (handy for seeing what you built)
    // ----------------------------------------------------------------- //

    /**
     * Draw the path onto an image (such as a World background) so you can see it,
     * using a default magenta line.
     *
     * @param image  the image to draw onto
     */
    public void drawOnto (GreenfootImage image)
    {
        drawOnto(image, Color.MAGENTA, 2);
    }

    /**
     * Draw the path onto an image in a chosen colour and thickness.
     *
     * @param image      the image to draw onto
     * @param color      the colour of the line
     * @param thickness  the thickness of the line in pixels
     */
    public void drawOnto (GreenfootImage image, Color color, int thickness)
    {
        if (image == null || points.size() < 2) {
            return;
        }
        java.awt.Graphics2D g = image.getAwtImage().createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                           java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(color.getRed(), color.getGreen(),
                                      color.getBlue(), color.getAlpha()));
        g.setStroke(new java.awt.BasicStroke(thickness,
                                             java.awt.BasicStroke.CAP_ROUND,
                                             java.awt.BasicStroke.JOIN_ROUND));
        int n = points.size();
        int segs = segmentCount();
        for (int i = 0; i < segs; i++) {
            Coordinate a = points.get(i);
            Coordinate b = points.get((i + 1) % n);
            g.drawLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
        g.dispose();
    }

    /**
     * Convenience method: draw a path onto an image with the default style.
     *
     * @param base  the image to draw onto
     * @param path  the path to draw
     */
    public static void drawPath (GreenfootImage base, SuperPath path)
    {
        if (path != null) {
            path.drawOnto(base);
        }
    }

    // ----------------------------------------------------------------- //
    //  Private helpers
    // ----------------------------------------------------------------- //

    /**
     * Advance a follower by speed and return the Coordinate it should be at, or
     * null if the path cannot be followed (fewer than two points).
     */
    private Coordinate advance (Actor mover, double speed)
    {
        ensureBuilt();
        if (points.size() < 2) {
            // Nothing to follow along; if there is a single point, sit on it.
            if (points.size() == 1) {
                Coordinate only = getStart();
                progress.put(mover, 0.0);
                return only;
            }
            return null;
        }

        Double current = progress.get(mover);
        double d;
        if (current == null) {
            d = 0.0;                    // first time: start at the beginning
        } else {
            d = current + speed;        // otherwise move forward
        }

        if (loop) {
            d = d % totalLength;
            if (d < 0) {
                d += totalLength;
            }
        } else {
            if (d < 0) {
                d = 0;
            }
            if (d > totalLength) {
                d = totalLength;
            }
        }

        progress.put(mover, d);
        return getPointAtDistance(d);
    }

    /**
     * Place an Actor at a Coordinate, using precise coordinates when possible.
     */
    private void place (Actor mover, Coordinate spot)
    {
        if (mover instanceof SuperSmoothMover) {
            ((SuperSmoothMover) mover).setLocation(spot.getPreciseX(), spot.getPreciseY());
        } else {
            mover.setLocation(spot.getX(), spot.getY());
        }
    }

    /**
     * Snap an Actor to a facing angle, using precise rotation when possible.
     */
    private void face (Actor mover, double angle)
    {
        if (mover instanceof SuperSmoothMover) {
            ((SuperSmoothMover) mover).setRotation(angle);
        } else {
            mover.setRotation((int) Math.round(angle));
        }
    }

    /**
     * Turn an Actor toward a target angle by at most maxTurn degrees.
     */
    private void turnToward (Actor mover, double target, double maxTurn)
    {
        double current = (mover instanceof SuperSmoothMover)
                       ? ((SuperSmoothMover) mover).getPreciseRotation()
                       : mover.getRotation();
        double diff = normalize(target - current);
        if (Math.abs(diff) <= maxTurn) {
            current = target;
        } else {
            current += Math.signum(diff) * maxTurn;
        }
        face(mover, current);
    }

    /**
     * Build a Coordinate t of the way (0..1) along segment number seg, with the
     * angle of that segment.
     */
    private Coordinate pointOnSegment (int seg, double t)
    {
        int n = points.size();
        Coordinate a = points.get(seg);
        Coordinate b = points.get((seg + 1) % n);
        double x = a.getPreciseX() + t * (b.getPreciseX() - a.getPreciseX());
        double y = a.getPreciseY() + t * (b.getPreciseY() - a.getPreciseY());
        double angle = Math.toDegrees(Math.atan2(b.getPreciseY() - a.getPreciseY(),
                                                 b.getPreciseX() - a.getPreciseX()));
        return new Coordinate(x, y, angle);
    }

    /**
     * The number of straight segments in the path (one extra closing segment
     * when the path loops).
     */
    private int segmentCount ()
    {
        int n = points.size();
        if (n < 2) {
            return 0;
        }
        return loop ? n : n - 1;
    }

    /**
     * Recompute the cached segment lengths if the path has changed.
     */
    private void ensureBuilt ()
    {
        if (!dirty) {
            return;
        }
        int segs = segmentCount();
        int n = points.size();
        cumulative = new double[segs + 1];
        cumulative[0] = 0.0;
        for (int i = 0; i < segs; i++) {
            Coordinate a = points.get(i);
            Coordinate b = points.get((i + 1) % n);
            double dx = b.getPreciseX() - a.getPreciseX();
            double dy = b.getPreciseY() - a.getPreciseY();
            cumulative[i + 1] = cumulative[i] + Math.sqrt(dx * dx + dy * dy);
        }
        totalLength = (segs >= 0) ? cumulative[segs] : 0.0;
        dirty = false;
    }

    /**
     * Normalise an angle to the range -180..180 degrees.
     */
    private static double normalize (double angle)
    {
        angle = angle % 360;
        if (angle > 180) {
            angle -= 360;
        }
        if (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    // ----------------------------------------------------------------- //
    //  Compatibility shims (kept only so older projects still compile)
    // ----------------------------------------------------------------- //

    /**
     * @param resolution  ignored
     * @deprecated The resolution parameter is no longer needed - just use
     *             {@link #Path()} or {@link #Path(boolean)}. Speed is now measured
     *             directly in pixels per act.
     */
    @Deprecated
    public SuperPath (double resolution)
    {
        this();
    }

    /**
     * @deprecated No longer required. Rotation along the path is now worked out
     *             automatically, so you can delete calls to this method.
     */
    @Deprecated
    public void calculateRotationVectors ()
    {
        // No-op: kept so existing setup code still compiles.
    }
}
