import greenfoot.*;
import java.util.ArrayList;
/**
 * Path Class for Greenfoot.
 * 
 * This allows for a custom Path to be defined and then followed by
 * an Actor.
 * 
 * 
 * 
 * @author Jordan Cohen
 * @author ChatGPT (wrote the calculateReturnPath() method)
 * 
 * 
 */
import greenfoot.*;
import java.util.ArrayList;

public class PathOld {
    private ArrayList<Coordinate> pathPoints;
    private ArrayList<Integer> rotationAngles;
    private int currentPointIndex;
    private double resolution; // Custom resolution for rotation vectors

    public PathOld(double resolution) {
        pathPoints = new ArrayList<Coordinate>();
        rotationAngles = new ArrayList<Integer>();
        currentPointIndex = 0;
        this.resolution = resolution;
    }

    public void addPoint(int x, int y) {
        pathPoints.add(new Coordinate(x, y));
    }

    public void calculateRotationVectors() {
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Coordinate currentPoint = pathPoints.get(i);
            Coordinate nextPoint = pathPoints.get(i + 1);
            double dx = nextPoint.getX() - currentPoint.getX();
            double dy = nextPoint.getY() - currentPoint.getY();
            int numSteps = (int) (Math.sqrt(dx * dx + dy * dy) / resolution);

            for (int step = 0; step < numSteps; step++) {
                double progress = (double) step / numSteps;
                int angle = (int) Math.toDegrees(Math.atan2(dy, dx));
                rotationAngles.add(angle);
            }
        }
    }

  public Coordinate move(Actor a, double speed) {
    if (pathPoints.isEmpty()) {
        // No path available
        return null;
    }

    Coordinate nextPoint = pathPoints.get(currentPointIndex);
    double deltaX = nextPoint.getX() - a.getX();
    double deltaY = nextPoint.getY() - a.getY();

    double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

    if (distance < 1) {
        // We have reached the current point, so move to the next one
        currentPointIndex = (currentPointIndex + 1) % pathPoints.size();
        nextPoint = pathPoints.get(currentPointIndex);
        deltaX = nextPoint.getX() - a.getX();
        deltaY = nextPoint.getY() - a.getY();
        distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    // Calculate the rotation angle to the next point
    int newRotation = (int) Math.toDegrees(Math.atan2(deltaY, deltaX));

    // Normalize the angle to be within [0, 360] degrees
    newRotation = (newRotation + 360) % 360;

    // Adjust speed based on resolution to ensure smooth movement
    double adjustedSpeed = speed / resolution;

    // Calculate the new position
    double newX = a.getX() + (deltaX / distance) * adjustedSpeed;
    double newY = a.getY() + (deltaY / distance) * adjustedSpeed;

    // Set the new rotation and position
    a.setRotation(newRotation);
    a.setLocation((int) newX, (int) newY);

    return new Coordinate(newX, newY, newRotation);
}


    // Method to add additional points between specified points to maintain resolution
    public void addResolutionPoints() {
        ArrayList<Coordinate> newPathPoints = new ArrayList<Coordinate>();

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            newPathPoints.add(pathPoints.get(i));

            Coordinate currentPoint = pathPoints.get(i);
            Coordinate nextPoint = pathPoints.get(i + 1);
            double dx = nextPoint.getX() - currentPoint.getX();
            double dy = nextPoint.getY() - currentPoint.getY();
            int numSteps = (int) (Math.sqrt(dx * dx + dy * dy) / resolution);

            for (int step = 1; step < numSteps; step++) {
                double progress = (double) step / numSteps;
                double newX = currentPoint.getX() + progress * dx;
                double newY = currentPoint.getY() + progress * dy;
                newPathPoints.add(new Coordinate(newX, newY));
            }
        }

        newPathPoints.add(pathPoints.get(pathPoints.size() - 1));
        pathPoints = newPathPoints;

        // Recalculate rotation vectors for the updated path
        rotationAngles.clear();
        calculateRotationVectors();
    }
}

