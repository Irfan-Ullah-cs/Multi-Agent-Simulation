package carSimulaiton;

import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.random.RandomHelper;
import java.util.ArrayList;
import java.util.List;

public class Car {
	private Grid<Object> grid;
    private Direction currentDirection;
    private int turnsWithoutMove = 0;
    private static final int MAX_TURNS_WITHOUT_MOVE = 3;

    public enum Direction {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public int getDx() { return dx; }
        public int getDy() { return dy; }

        public Direction getLeft() {
            switch (this) {
                case NORTH: return WEST;
                case EAST: return NORTH;
                case SOUTH: return EAST;
                case WEST: return SOUTH;
                default: return NORTH;
            }
        }

        public Direction getRight() {
            switch (this) {
                case NORTH: return EAST;
                case EAST: return SOUTH;
                case SOUTH: return WEST;
                case WEST: return NORTH;
                default: return NORTH;
            }
        }
    }

    public String toString() {
        // The direction will determine which image to use
        if (currentDirection != null) {
            switch (currentDirection) {
                case EAST:
                    return "car_east";
                case WEST:
                    return "car_west";
                case NORTH:
                    return "car_north";
                case SOUTH:
                    return "car_south";
            }
        }
        return "car_east"; // Default direction
    }

    public Car(Grid<Object> grid) {
        this.grid = grid;
        // Direction will be set when placed on a road
        this.currentDirection = null;
    }
    
    public void setInitialDirection(Direction direction) {
        this.currentDirection = direction;
    }
    
    // Essential getter for the visualization
    public Direction getCurrentDirection() {
        return currentDirection;
    }

    @ScheduledMethod(start = 1, interval = 1)
    public void step() {
        GridPoint currentPos = grid.getLocation(this);
        
        // If this is a new car without direction, set direction based on road
        if (currentDirection == null) {
            assignDirectionBasedOnRoad(currentPos);
            if (currentDirection == null) {
                // Still no direction, use a default
                currentDirection = Direction.EAST;
            }
        }
        
        // Check if we're at an intersection
        boolean atIntersection = isAtIntersection(currentPos);
        
        // Get all possible moves from this position
        List<Direction> possibleMoves = getAllowedMoves(currentPos);
        
        // Debug output
        if (atIntersection) {
            System.out.println("Car at intersection (" + currentPos.getX() + ", " + currentPos.getY() + 
                              "), direction: " + currentDirection + ", possible moves: " + possibleMoves);
        }

        // Make a move if possible
        if (!possibleMoves.isEmpty()) {
            Direction moveDirection;
            
            if (atIntersection) {
                // At intersections, make a turning decision
                moveDirection = decideTurnAtIntersection(possibleMoves);
            } else {
                // On regular roads, prioritize current direction
                if (possibleMoves.contains(currentDirection)) {
                    moveDirection = currentDirection;
                } else {
                    // If current direction is blocked, use any valid move
                    moveDirection = possibleMoves.get(0);
                }
            }
            
            // Move the car
            move(currentPos, moveDirection);
            turnsWithoutMove = 0; // Reset the counter
        } else {
            // Car is blocked in all directions
            turnsWithoutMove++;
            System.out.println("Car at (" + currentPos.getX() + ", " + currentPos.getY() + 
                             ") is blocked. Turns without move: " + turnsWithoutMove);
            
            // Wait for a fixed number of turns, then just yield the space
            if (turnsWithoutMove >= MAX_TURNS_WITHOUT_MOVE) {
                yieldAtRoadblock();
            }
        }
    }

    private void assignDirectionBasedOnRoad(GridPoint pos) {
        for (Object obj : grid.getObjectsAt(pos.getX(), pos.getY())) {
            if (obj instanceof Road) {
                Road road = (Road) obj;
                switch (road.getDirection()) {
                    case EASTBOUND:
                        currentDirection = Direction.EAST;
                        return;
                    case WESTBOUND:
                        currentDirection = Direction.WEST;
                        return;
                    case NORTHBOUND:
                        currentDirection = Direction.NORTH;
                        return;
                    case SOUTHBOUND:
                        currentDirection = Direction.SOUTH;
                        return;
                    case ALL:
                        // For intersections, choose randomly
                        Direction[] directions = Direction.values();
                        currentDirection = directions[RandomHelper.nextIntFromTo(0, directions.length - 1)];
                        return;
                }
            }
        }
    }

    // Method to get all allowed moves from current position
    private List<Direction> getAllowedMoves(GridPoint currentPos) {
        List<Direction> allowedMoves = new ArrayList<>();
        boolean atIntersection = isAtIntersection(currentPos);
        
        // Directions to check
        Direction[] directionsToCheck;
        
        if (atIntersection) {
            // At intersections, check all four directions
            directionsToCheck = Direction.values();
        } else {
            // On regular roads, check current direction with left and right as fallbacks
            directionsToCheck = new Direction[]{
                currentDirection,
                currentDirection.getLeft(),
                currentDirection.getRight()
            };
        }
        
        // Check all candidate directions
        for (Direction dir : directionsToCheck) {
            if (isValidMove(currentPos, dir)) {
                allowedMoves.add(dir);
            }
        }
        
        return allowedMoves;
    }

    // Better turning decision logic at intersections
    private Direction decideTurnAtIntersection(List<Direction> possibleMoves) {
        // Safety check
        if (possibleMoves.isEmpty()) {
            return currentDirection;
        }
        
        // Prefer to continue in same direction if possible
        if (possibleMoves.contains(currentDirection)) {
            // 70% chance to go straight if possible
            if (RandomHelper.nextDouble() < 0.7) {
                return currentDirection;
            }
        }
        
        // Get turn options (left and right)
        List<Direction> turnOptions = new ArrayList<>();
        if (possibleMoves.contains(currentDirection.getLeft())) {
            turnOptions.add(currentDirection.getLeft());
        }
        if (possibleMoves.contains(currentDirection.getRight())) {
            turnOptions.add(currentDirection.getRight());
        }
        
        // If we have turn options, randomly choose one
        if (!turnOptions.isEmpty()) {
            return turnOptions.get(RandomHelper.nextIntFromTo(0, turnOptions.size() - 1));
        }
        
        // If we can't turn, pick any valid move
        return possibleMoves.get(RandomHelper.nextIntFromTo(0, possibleMoves.size() - 1));
    }

    private boolean isAtIntersection(GridPoint point) {
        for (Object obj : grid.getObjectsAt(point.getX(), point.getY())) {
            if (obj instanceof Road) {
                Road road = (Road) obj;
                return road.getType() == Road.RoadType.INTERSECTION;
            }
        }
        return false;
    }

    // Replace tryToRelocate with yieldAtRoadblock
    // This simply makes the car "yield" by backing up if it's stuck
    private void yieldAtRoadblock() {
        GridPoint currentPos = grid.getLocation(this);
        System.out.println("Car has been blocked at (" + currentPos.getX() + ", " + currentPos.getY() + 
                         ") for too long. Yielding...");
        
        // Try to back up (reverse direction)
        Direction reverseDirection = getReverseDirection(currentDirection);
        
        // Check if backing up is possible
        int backX = currentPos.getX() + reverseDirection.getDx();
        int backY = currentPos.getY() + reverseDirection.getDy();
        
        // Handle grid wrapping
        backX = (backX + grid.getDimensions().getWidth()) % grid.getDimensions().getWidth();
        backY = (backY + grid.getDimensions().getHeight()) % grid.getDimensions().getHeight();
        
        boolean canBackUp = false;
        boolean hasRoad = false;
        
        for (Object obj : grid.getObjectsAt(backX, backY)) {
            if (obj instanceof Road) {
                hasRoad = true;
            }
            if (obj instanceof Car) {
                // Space is occupied, can't back up
                canBackUp = false;
                break;
            } else if (hasRoad) {
                canBackUp = true;
            }
        }
        
        if (canBackUp) {
            // Back up one space
            grid.moveTo(this, backX, backY);
            
            // After backing up, try to find a new direction
            List<Direction> possibleMoves = getAllowedMoves(new GridPoint(backX, backY));
            if (!possibleMoves.isEmpty()) {
                // Pick a new direction that's not the reverse of our original direction
                for (Direction dir : possibleMoves) {
                    if (dir != currentDirection) {
                        currentDirection = dir;
                        break;
                    }
                }
                // If no alternative, use any direction
                if (possibleMoves.size() > 0 && currentDirection == reverseDirection) {
                    currentDirection = possibleMoves.get(0);
                }
            }
            
            System.out.println("Car backed up to (" + backX + ", " + backY + ") and is now facing " + currentDirection);
            turnsWithoutMove = 0;
        } else {
            // If can't back up, try to turn around in place
            List<Direction> possibleMoves = getAllowedMoves(currentPos);
            if (!possibleMoves.isEmpty()) {
                // Change direction without moving
                currentDirection = possibleMoves.get(RandomHelper.nextIntFromTo(0, possibleMoves.size() - 1));
                System.out.println("Car couldn't back up, changed direction to " + currentDirection);
                turnsWithoutMove = 0;
            } else {
                System.out.println("Car is completely blocked and cannot move in any direction.");
                // Reset counter to avoid continuous "yielding" attempts
                turnsWithoutMove = 0;
            }
        }
    }

    // Helper to get reverse direction
    private Direction getReverseDirection(Direction dir) {
        switch (dir) {
            case NORTH: return Direction.SOUTH;
            case SOUTH: return Direction.NORTH;
            case EAST: return Direction.WEST;
            case WEST: return Direction.EAST;
            default: return Direction.NORTH;
        }
    }

    private void move(GridPoint currentPos, Direction direction) {
        int newX = currentPos.getX() + direction.getDx();
        int newY = currentPos.getY() + direction.getDy();

        // Handle grid wrapping
        newX = (newX + grid.getDimensions().getWidth()) % grid.getDimensions().getWidth();
        newY = (newY + grid.getDimensions().getHeight()) % grid.getDimensions().getHeight();

        // Move the car
        grid.moveTo(this, newX, newY);
        currentDirection = direction; // Update the current direction
    }

    // Improved validation for moves
    private boolean isValidMove(GridPoint currentPos, Direction direction) {
        int newX = currentPos.getX() + direction.getDx();
        int newY = currentPos.getY() + direction.getDy();

        // Handle grid wrapping
        newX = (newX + grid.getDimensions().getWidth()) % grid.getDimensions().getWidth();
        newY = (newY + grid.getDimensions().getHeight()) % grid.getDimensions().getHeight();

        // Check for traffic light state
        boolean hasRedLight = false;
        for (Object obj : grid.getObjectsAt(newX, newY)) {
            if (obj instanceof TrafficLight) {
                TrafficLight light = (TrafficLight) obj;
                if (light.getState() == TrafficLight.LightState.RED) {
                    hasRedLight = true;
                    break;
                }
            }
        }
        
        // If red light is present, cannot move
        if (hasRedLight) {
            return false;
        }

        // Check if there's a road at this location that allows this direction
        boolean validRoad = false;
        boolean hasCarInPath = false;
        
        for (Object obj : grid.getObjectsAt(newX, newY)) {
            if (obj instanceof Road) {
                Road road = (Road) obj;
                if (road.allowsDirection(direction) || road.getType() == Road.RoadType.INTERSECTION) {
                    validRoad = true;
                }
            }
            if (obj instanceof Car) {
                hasCarInPath = true;
            }
        }
        
        // Return true if there's a valid road and no car in the way
        return validRoad && !hasCarInPath;
    }
}