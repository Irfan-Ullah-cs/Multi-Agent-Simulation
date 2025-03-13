package carSimulaiton;

import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import java.awt.Color;

public class Road {
    private RoadType type;
    private Direction direction;
    
    public enum RoadType {
        HORIZONTAL,
        VERTICAL,
        INTERSECTION
    }
    
    public enum Direction {
        EASTBOUND,  // Left to right
        WESTBOUND,  // Right to left
        NORTHBOUND, // Bottom to top
        SOUTHBOUND, // Top to bottom
        ALL         // For intersections, allows all directions
    }
    
    public Road(RoadType type, Direction direction) {
        this.type = type;
        
        // Set appropriate direction based on road type
        if (type == RoadType.INTERSECTION) {
            this.direction = Direction.ALL;
        } else {
            this.direction = direction;
        }
    }
    
    public RoadType getType() {
        return type;
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    public boolean allowsDirection(Car.Direction carDirection) {
        if (direction == Direction.ALL) {
            return true; // Intersections allow all directions
        }
        
        switch (direction) {
            case EASTBOUND:
                return carDirection == Car.Direction.EAST;
            case WESTBOUND:
                return carDirection == Car.Direction.WEST;
            case NORTHBOUND:
                return carDirection == Car.Direction.NORTH;
            case SOUTHBOUND:
                return carDirection == Car.Direction.SOUTH;
            default:
                return false;
        }
    }
    
    // This method will be used by a custom style class to determine road color
    public Color getColor() {
        switch(type) {
            case HORIZONTAL:
                if (direction == Direction.EASTBOUND) {
                    return new Color(150, 150, 120);  // Slightly yellowish for eastbound
                } else {
                    return new Color(120, 150, 150);  // Slightly bluish for westbound
                }
            case VERTICAL:
                if (direction == Direction.NORTHBOUND) {
                    return new Color(150, 120, 150);  // Slightly purplish for northbound
                } else {
                    return new Color(120, 150, 120);  // Slightly greenish for southbound
                }
            case INTERSECTION:
                return new Color(100, 100, 100);  // Dark gray for intersections
            default:
                return Color.GRAY;
        }
    }
}