package carSimulaiton;

import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.random.RandomHelper;
import repast.simphony.context.Context;
import repast.simphony.util.ContextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced Car class with improved navigation and deadlock prevention.
 */
public class Car {
    private Grid<Object> grid;
    private Direction currentDirection;
    private int turnsWithoutMove = 0;
    private static final int MAX_TURNS_WITHOUT_MOVE = 3;
    private static final int TELEPORT_THRESHOLD = 10; // After this many failed yields, teleport the car
    private int yieldsWithoutProgress = 0;
    
    // Garbage collection related attributes
    private int id;
    private String type = "Standard";
    private double garbageCapacity = 100.0;
    private double currentLoad = 0.0;
    
    // Collection status
    private boolean isCollectingFromBin = false;
    private int collectionCounter = 0;
    private static final int COLLECTION_DURATION = 5;
    
    // Performance metrics
    private int collectionsCompleted = 0;
    private double totalDistance = 0.0;
    
    // Target tracking
    private Integer targetBinId = null;
    private GridPoint targetDestination = null;
    private GridPoint previousPosition = null;
    
    // Target cooldown system
    private Map<Integer, Long> lastEmptyTime = new HashMap<>();
    private static final long EMPTY_COOLDOWN = 5000; // 5 seconds cooldown
    
    // Known garbage bins
    private Map<Integer, BinInfo> knownBins = new HashMap<>();
    
    // Status tracking
    private String status = "idle";
    private long lastStatusChangeTime = System.currentTimeMillis();
    private static final long STUCK_THRESHOLD = 10000; // 10 seconds
    
    // Garbage collection depot - center of the map
    private GridPoint depotLocation = null;
    private boolean returningToDepot = false;
    
    // Bin assignment system (static to be shared among all vehicles)
    private static Map<Integer, Integer> binAssignments = new ConcurrentHashMap<>(); // binId -> carId
    
    // Route memory to avoid getting stuck in loops
    private List<GridPoint> recentPositions = new ArrayList<>();
    private static final int MEMORY_LENGTH = 10; // Remember last 10 positions
    
    // Anti-deadlock system
    private List<GridPoint> blockedPositions = new ArrayList<>();
    private static final int MAX_BLOCKED_POSITIONS = 20;

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
        
        public Direction getOpposite() {
            switch (this) {
                case NORTH: return SOUTH;
                case EAST: return WEST;
                case SOUTH: return NORTH;
                case WEST: return EAST;
                default: return NORTH;
            }
        }
    }
    
    /**
     * Class to store information about a known garbage bin.
     */
    private class BinInfo {
        int id;
        int x;
        int y;
        double fillLevel;
        double capacity;
        String areaType;
        boolean isUrgent;
        long lastUpdated;
        
        BinInfo(int id, int x, int y, double fillLevel, double capacity, String areaType, boolean isUrgent) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.fillLevel = fillLevel;
            this.capacity = capacity;
            this.areaType = areaType;
            this.isUrgent = isUrgent;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        double getFillPercentage() {
            return (fillLevel / capacity) * 100;
        }
        
        GridPoint getLocation() {
            return new GridPoint(x, y);
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdated > 30000; // 30 seconds
        }
        
        @Override
        public String toString() {
            return "Bin " + id + " (" + areaType + "): " + String.format("%.1f", getFillPercentage()) + "% full";
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
        
        // Assign a unique ID based on time and hashcode
        this.id = Math.abs((int)(System.currentTimeMillis() % 10000) + this.hashCode() % 1000);
        
        // Direction will be set when placed on a road
        this.currentDirection = null;
        
        // Set depot location to center of grid
        int width = grid.getDimensions().getWidth();
        int height = grid.getDimensions().getHeight();
        this.depotLocation = new GridPoint(width/2, height/2);
    }
    
    /**
     * Constructor with ID for when we need specific IDs
     */
    public Car(Grid<Object> grid, int id) {
        this(grid);
        this.id = id;
    }
    
    public Car(Grid<Object> grid, int id, String type, double garbageCapacity) {
        this(grid, id);
        this.type = type;
        this.garbageCapacity = garbageCapacity;
    }
    
    public void setInitialDirection(Direction direction) {
        this.currentDirection = direction;
    }
    
    public Direction getCurrentDirection() {
        return currentDirection;
    }

    @ScheduledMethod(start = 1, interval = 1)
    public void step() {
        GridPoint currentPos = grid.getLocation(this);
        
        // Track position history for loop detection
        trackPosition(currentPos);
        
        // Track distance traveled
        if (previousPosition != null) {
            totalDistance += getDistance(previousPosition, currentPos);
        }
        previousPosition = currentPos;
        
        // Check if collecting from a bin
        if (isCollectingFromBin) {
            collectionCounter++;
            if (collectionCounter >= COLLECTION_DURATION) {
                finishCollection();
            } else {
                // Skip rest of step while collecting
                System.out.println("Car " + id + " collecting from bin " + targetBinId + 
                                 ": " + collectionCounter + "/" + COLLECTION_DURATION);
                return;
            }
        }
        
        // Check if need to return to depot (>90% full)
        if (currentLoad >= garbageCapacity * 0.9 && !returningToDepot) {
            returnToDepot();
            return;
        }
        
        // Check if we've reached the depot
        if (returningToDepot) {
            if (checkDepotReached(currentPos)) {
                return;
            }
        }
        
        // If we have a target bin, check if we've reached it
        if (targetBinId != null && !returningToDepot) {
            if (checkBinReached(currentPos)) {
                return;
            }
        }
        
        // Clean up stale data periodically
        cleanStaleData();
        
        // Legacy car behavior with deadlock prevention
        handleCarMovementWithDeadlockPrevention(currentPos);
        
        // If the car has moved after handling car movement, update currentPos
        GridPoint newPos = grid.getLocation(this);
        if (!newPos.equals(currentPos)) {
            currentPos = newPos;
            // Reset counter because car moved successfully
            turnsWithoutMove = 0;
            yieldsWithoutProgress = 0;
        }
        
        // Try to find a garbage bin to target after normal movement if we don't have one
        if (targetBinId == null && !returningToDepot && !isCollectingFromBin) {
            scanForGarbageBins(currentPos);
            findNewTarget();
        }
    }
    
    /**
     * Track position history to detect and avoid loops
     */
    private void trackPosition(GridPoint currentPos) {
        recentPositions.add(currentPos);
        if (recentPositions.size() > MEMORY_LENGTH) {
            recentPositions.remove(0); // Remove oldest position
        }
        
        // Also track blocked positions
        if (turnsWithoutMove >= MAX_TURNS_WITHOUT_MOVE) {
            blockedPositions.add(currentPos);
            if (blockedPositions.size() > MAX_BLOCKED_POSITIONS) {
                blockedPositions.remove(0); // Remove oldest blocked position
            }
        }
    }
    
    /**
     * Check if position is in recent history (loop detection)
     */
    private boolean isPositionInRecentHistory(GridPoint pos) {
        int occurrences = 0;
        for (GridPoint p : recentPositions) {
            if (p.equals(pos)) {
                occurrences++;
                if (occurrences >= 2) { // Position appears multiple times
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Handle the car movement with enhanced deadlock prevention
     */
    private void handleCarMovementWithDeadlockPrevention(GridPoint currentPos) {
        // If this is a new car without direction, set direction based on road
        if (currentDirection == null) {
            assignDirectionBasedOnRoad(currentPos);
            if (currentDirection == null) {
                // Still no direction, use a default
                currentDirection = Direction.EAST;
            }
        }
        
        // Don't follow normal traffic rules if we have a target destination
        if (targetDestination != null) {
            moveTowardTargetWithAvoidance(currentPos);
            return;
        }
        
        // Check if we're at an intersection
        boolean atIntersection = isAtIntersection(currentPos);
        
        // Get all possible moves from this position
        List<Direction> possibleMoves = getAllowedMoves(currentPos);
        
        // Filter out recently blocked positions
        possibleMoves = filterBlockedPositions(currentPos, possibleMoves);
        
        // Debug output
        if (atIntersection) {
            System.out.println("Car " + id + " at intersection (" + currentPos.getX() + ", " + currentPos.getY() + 
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
            System.out.println("Car " + id + " at (" + currentPos.getX() + ", " + currentPos.getY() + 
                             ") is blocked. Turns without move: " + turnsWithoutMove);
            
            // Wait for a fixed number of turns, then just yield the space
            if (turnsWithoutMove >= MAX_TURNS_WITHOUT_MOVE) {
                enhancedYieldAtRoadblock();
            }
        }
    }
    
    /**
     * Filter out directions that would lead to recently blocked positions
     */
    private List<Direction> filterBlockedPositions(GridPoint currentPos, List<Direction> possibleMoves) {
        List<Direction> filteredMoves = new ArrayList<>();
        
        for (Direction dir : possibleMoves) {
            // Calculate next position for this direction
            int newX = currentPos.getX() + dir.getDx();
            int newY = currentPos.getY() + dir.getDy();
            
            // Handle grid wrapping
            newX = (newX + grid.getDimensions().getWidth()) % grid.getDimensions().getWidth();
            newY = (newY + grid.getDimensions().getHeight()) % grid.getDimensions().getHeight();
            
            GridPoint potentialNext = new GridPoint(newX, newY);
            
            // Skip if this position has been blocked recently
            boolean recentlyBlocked = false;
            for (GridPoint blocked : blockedPositions) {
                if (blocked.equals(potentialNext)) {
                    recentlyBlocked = true;
                    break;
                }
            }
            
            // Skip if this would create a loop in recent movement
            if (!recentlyBlocked && !isPositionInRecentHistory(potentialNext)) {
                filteredMoves.add(dir);
            }
        }
        
        // If all directions are filtered out, reset and use original list
        // (better to move in a loop than not move at all)
        if (filteredMoves.isEmpty() && !possibleMoves.isEmpty()) {
            return possibleMoves;
        }
        
        return filteredMoves;
    }
    
    /**
     * Move towards the target destination with improved pathfinding
     */
    private void moveTowardTargetWithAvoidance(GridPoint currentPos) {
        if (targetDestination == null) return;
        
        // Get direction to target
        Direction targetDirection = getDirectionToTarget(currentPos, targetDestination);
        
        // Prepare list of possible directions in order of preference
        List<Direction> preferredDirections = new ArrayList<>();
        
        // First choice: direct route to target
        preferredDirections.add(targetDirection);
        
        // Alternative routes (perpendicular to target direction)
        preferredDirections.add(targetDirection.getLeft());
        preferredDirections.add(targetDirection.getRight());
        
        // Last resort: reverse direction (away from target)
        // preferredDirections.add(targetDirection.getOpposite());
        
        // Filter out recently blocked positions
        preferredDirections = filterBlockedPositions(currentPos, preferredDirections);
        
        // Try each direction in order of preference
        for (Direction dir : preferredDirections) {
            if (isValidMove(currentPos, dir)) {
                move(currentPos, dir);
                return;
            }
        }
        
        // If no valid moves, try any possible move
        List<Direction> allDirections = getAllowedMoves(currentPos);
        if (!allDirections.isEmpty()) {
            Direction randomDir = allDirections.get(RandomHelper.nextIntFromTo(0, allDirections.size() - 1));
            move(currentPos, randomDir);
        } else {
            // If completely stuck, increment counter
            turnsWithoutMove++;
            if (turnsWithoutMove >= MAX_TURNS_WITHOUT_MOVE) {
                enhancedYieldAtRoadblock();
            }
        }
    }
    
    private void assignDirectionBasedOnRoad(GridPoint pos) {
        for (Object obj : grid.getObjectsAt(pos.getX(), pos.getY())) {
            if (obj instanceof Road) {
                Road road = (Road) obj;
                
                // For bidirectional roads, choose a random direction
                if (road.getDirection() == Road.Direction.BIDIRECTIONAL) {
                    if (road.getType() == Road.RoadType.HORIZONTAL) {
                        // For horizontal roads, randomly choose EAST or WEST
                        currentDirection = (RandomHelper.nextDouble() < 0.5) ? 
                                         Direction.EAST : Direction.WEST;
                    } else {
                        // For vertical roads, randomly choose NORTH or SOUTH
                        currentDirection = (RandomHelper.nextDouble() < 0.5) ? 
                                         Direction.NORTH : Direction.SOUTH;
                    }
                    return;
                }
                
                // For directional roads, follow the road direction
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

    private List<Direction> getAllowedMoves(GridPoint currentPos) {
        List<Direction> allowedMoves = new ArrayList<>();
        boolean atIntersection = isAtIntersection(currentPos);
        
        // Try all directions at intersections
        if (atIntersection) {
            for (Direction dir : Direction.values()) {
                if (isValidMove(currentPos, dir)) {
                    allowedMoves.add(dir);
                }
            }
            return allowedMoves;
        }
        
        // On normal roads, prioritize certain directions
        
        // First try current direction
        if (isValidMove(currentPos, currentDirection)) {
            allowedMoves.add(currentDirection);
        }
        
        // Then try left and right turns
        if (isValidMove(currentPos, currentDirection.getLeft())) {
            allowedMoves.add(currentDirection.getLeft());
        }
        
        if (isValidMove(currentPos, currentDirection.getRight())) {
            allowedMoves.add(currentDirection.getRight());
        }
        
        // Only as a last resort, try going backwards
        if (allowedMoves.isEmpty() && isValidMove(currentPos, currentDirection.getOpposite())) {
            allowedMoves.add(currentDirection.getOpposite());
        }
        
        return allowedMoves;
    }

    private Direction decideTurnAtIntersection(List<Direction> possibleMoves) {
        // Safety check
        if (possibleMoves.isEmpty()) {
            return currentDirection;
        }
        
        // If we have a destination, prioritize direction toward it
        if (targetDestination != null) {
            GridPoint currentPos = grid.getLocation(this);
            Direction targetDir = getDirectionToTarget(currentPos, targetDestination);
            
            if (possibleMoves.contains(targetDir)) {
                return targetDir;
            }
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

    /**
     * Enhanced yielding with teleportation for severe deadlocks
     */
    private void enhancedYieldAtRoadblock() {
        GridPoint currentPos = grid.getLocation(this);
        System.out.println("Car " + id + " has been blocked at (" + currentPos.getX() + ", " + currentPos.getY() + 
                         ") for too long. Yielding...");
        
        // Increment yield counter for deadlock detection
        yieldsWithoutProgress++;
        
        // If we've tried to yield too many times without success, teleport the car
        if (yieldsWithoutProgress >= TELEPORT_THRESHOLD) {
            teleportToRandomRoad();
            return;
        }
        
        // Try to back up (reverse direction)
        Direction reverseDirection = currentDirection.getOpposite();
        
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
            
            System.out.println("Car " + id + " backed up to (" + backX + ", " + backY + ") and is now facing " + currentDirection);
            turnsWithoutMove = 0;
        } else {
            // If can't back up, try to turn around in place
            List<Direction> possibleMoves = getAllowedMoves(currentPos);
            if (!possibleMoves.isEmpty()) {
                // Change direction without moving
                currentDirection = possibleMoves.get(RandomHelper.nextIntFromTo(0, possibleMoves.size() - 1));
                System.out.println("Car " + id + " couldn't back up, changed direction to " + currentDirection);
                turnsWithoutMove = 0;
            } else {
                System.out.println("Car " + id + " is completely blocked and cannot move in any direction.");
                // Reset counter to avoid continuous "yielding" attempts
                turnsWithoutMove = 0;
            }
        }
    }
    
    /**
     * Teleport the car to a random road position to resolve severe deadlocks
     */
    private void teleportToRandomRoad() {
        System.out.println("Car " + id + " is severely deadlocked. Teleporting to a new location...");
        
        // Find a random road segment to teleport to
        Context<Object> context = ContextUtils.getContext(this);
        List<Road> allRoads = new ArrayList<>();
        
        for (Object obj : context.getObjects(Road.class)) {
            Road road = (Road) obj;
            if (road.getType() != Road.RoadType.INTERSECTION) {
                allRoads.add(road);
            }
        }
        
        if (allRoads.isEmpty()) {
            System.out.println("Error: No road segments found for teleportation");
            return;
        }
        
        // Try up to 20 times to find an unoccupied road position
        for (int i = 0; i < 20; i++) {
            Road randomRoad = allRoads.get(RandomHelper.nextIntFromTo(0, allRoads.size() - 1));
            GridPoint roadPos = grid.getLocation(randomRoad);
            
            // Check if position is available (no car there)
            boolean hasCar = false;
            for (Object obj : grid.getObjectsAt(roadPos.getX(), roadPos.getY())) {
                if (obj instanceof Car && obj != this) {
                    hasCar = true;
                    break;
                }
            }
            
            if (!hasCar) {
                // Position is available, teleport here
                grid.moveTo(this, roadPos.getX(), roadPos.getY());
                
                // Set appropriate direction based on road type
                if (randomRoad.getType() == Road.RoadType.HORIZONTAL) {
                    currentDirection = (RandomHelper.nextDouble() < 0.5) ? Direction.EAST : Direction.WEST;
                } else {
                    currentDirection = (RandomHelper.nextDouble() < 0.5) ? Direction.NORTH : Direction.SOUTH;
                }
                
                System.out.println("Car " + id + " teleported to (" + roadPos.getX() + ", " + roadPos.getY() + 
                                 ") and is now facing " + currentDirection);
                
                // Reset counters
                turnsWithoutMove = 0;
                yieldsWithoutProgress = 0;
                
                // Clear movement history
                recentPositions.clear();
                blockedPositions.clear();
                
                return;
            }
        }
        
        System.out.println("Failed to find teleport destination for Car " + id + " after 20 attempts");
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
    
    //----------------------------------------------------------------
    // Garbage Collection Functionality
    //----------------------------------------------------------------
    
    /**
     * Scan the area around the car for garbage bins
     */
    private void scanForGarbageBins(GridPoint currentPos) {
        // Look for garbage bins in immediate vicinity (5 cell radius)
        int scanRadius = 5;
        int centerX = currentPos.getX();
        int centerY = currentPos.getY();
        
        for (int x = centerX - scanRadius; x <= centerX + scanRadius; x++) {
            for (int y = centerY - scanRadius; y <= centerY + scanRadius; y++) {
                // Handle grid wrapping
                int gridX = (x + grid.getDimensions().getWidth()) % grid.getDimensions().getWidth();
                int gridY = (y + grid.getDimensions().getHeight()) % grid.getDimensions().getHeight();
                
                for (Object obj : grid.getObjectsAt(gridX, gridY)) {
                    if (obj instanceof GarbageBin) {
                        GarbageBin bin = (GarbageBin) obj;
                        
                        // Only track bins that are at least 70% full
                        if (bin.getFillPercentage() >= 70.0) {
                            boolean isUrgent = bin.getFillPercentage() >= 90.0;
                            
                            // Add/update bin info
                            BinInfo binInfo = new BinInfo(
                                bin.getId(),
                                gridX,
                                gridY,
                                bin.getCurrentFill(),
                                bin.getCapacity(),
                                bin.getAreaType(),
                                isUrgent
                            );
                            
                            knownBins.put(bin.getId(), binInfo);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Remove stale data from vehicle's knowledge base.
     */
    private void cleanStaleData() {
        // Remove stale bin information
        List<Integer> staleBins = new ArrayList<>();
        for (Map.Entry<Integer, BinInfo> entry : knownBins.entrySet()) {
            if (entry.getValue().isStale()) {
                staleBins.add(entry.getKey());
            }
        }
        
        for (Integer binId : staleBins) {
            knownBins.remove(binId);
        }
        
        // Remove old entries from lastEmptyTime
        List<Integer> oldEntries = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> entry : lastEmptyTime.entrySet()) {
            if (currentTime - entry.getValue() > EMPTY_COOLDOWN) {
                oldEntries.add(entry.getKey());
            }
        }
        
        for (Integer binId : oldEntries) {
            lastEmptyTime.remove(binId);
        }
    }
    
    /**
     * Find a new garbage bin to target based on distance and fill level.
     */
    private void findNewTarget() {
        if (returningToDepot || isCollectingFromBin || targetBinId != null) {
            return;
        }
        
        // Get current position
        GridPoint myPoint = grid.getLocation(this);
        
        // Find best bin to target
        BinInfo bestBin = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (BinInfo bin : knownBins.values()) {
            // Skip if bin is assigned to another car
            if (isBinAssigned(bin.id) && !isBinAssignedToMe(bin.id)) {
                continue;
            }
            
            // Skip if we don't have capacity
            if (!hasCapacityFor(bin.fillLevel)) {
                continue;
            }
            
            // Skip if recently emptied
            if (lastEmptyTime.containsKey(bin.id) && 
                System.currentTimeMillis() - lastEmptyTime.get(bin.id) < EMPTY_COOLDOWN) {
                continue;
            }
            
            // Calculate distance using grid distance (Manhattan distance)
            double distance = getDistance(myPoint, bin.getLocation());
            
            // Calculate score (prioritizing closer bins and higher fill levels)
            double distanceScore = 1000.0 / (distance * distance + 1.0); // Inverse square distance
            double fillScore = bin.getFillPercentage() / 100.0;  // 0.0 - 1.0
            double urgencyBonus = bin.isUrgent ? 1.5 : 1.0;      // 50% bonus for urgent bins
            
            double score = distanceScore * (0.7 + 0.3 * fillScore) * urgencyBonus;
            
            if (score > bestScore) {
                bestScore = score;
                bestBin = bin;
            }
        }
        
        // Target the best bin if found
        if (bestBin != null) {
            // Try to get assignment
            if (assignBin(bestBin.id)) {
                targetBin(bestBin.id, bestBin.getLocation());
            }
        }
    }
    
    /**
     * Calculate grid distance between two points (Manhattan distance)
     */
    private double getDistance(GridPoint p1, GridPoint p2) {
        int dx = Math.abs(p1.getX() - p2.getX());
        int dy = Math.abs(p1.getY() - p2.getY());
        
        // Account for grid wrapping - take the shorter distance
        int width = grid.getDimensions().getWidth();
        int height = grid.getDimensions().getHeight();
        
        dx = Math.min(dx, width - dx);
        dy = Math.min(dy, height - dy);
        
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Get the direction to a target position
     */
    private Direction getDirectionToTarget(GridPoint current, GridPoint target) {
        // Calculate the delta
        int dx = target.getX() - current.getX();
        int dy = target.getY() - current.getY();
        
        // Handle grid wrapping
        int width = grid.getDimensions().getWidth();
        int height = grid.getDimensions().getHeight();
        
        // Check if wrapping would be shorter
        if (Math.abs(dx) > width / 2) {
            dx = (dx > 0) ? dx - width : dx + width;
        }
        if (Math.abs(dy) > height / 2) {
            dy = (dy > 0) ? dy - height : dy + height;
        }
        
        // Determine the dominant direction
        if (Math.abs(dx) > Math.abs(dy)) {
            return (dx > 0) ? Direction.EAST : Direction.WEST;
        } else {
            return (dy > 0) ? Direction.SOUTH : Direction.NORTH;
        }
    }
    
    /**
     * Target a specific bin for collection.
     */
    private void targetBin(int binId, GridPoint binLocation) {
        // Set as our target
        targetBinId = binId;
        targetDestination = binLocation;
        
        // Update status
        BinInfo binInfo = knownBins.get(binId);
        String areaType = (binInfo != null) ? binInfo.areaType : "UNKNOWN";
        boolean isUrgent = (binInfo != null) && binInfo.isUrgent;
        double distance = getDistance(grid.getLocation(this), binLocation);
        
        System.out.println("Car " + id + " targeting bin " + binId + 
                          " (" + areaType + ")" + (isUrgent ? " (URGENT)" : "") + 
                          " at distance " + String.format("%.2f", distance));
        
        updateStatus("heading to bin " + binId);
        
        // Clear movement history to avoid loop detection interfering with targeting
        recentPositions.clear();
        blockedPositions.clear();
    }
    
    /**
     * Check if vehicle has reached its target bin.
     */
    private boolean checkBinReached(GridPoint currentPos) {
        if (targetBinId == null || targetDestination == null) {
            return false;
        }
        
        double distance = getDistance(currentPos, targetDestination);
        
        // If we're close enough to the bin (adjacent)
        if (distance <= 1.0) {
            System.out.println("Car " + id + " reached bin " + targetBinId);
            
            // Start collection process
            isCollectingFromBin = true;
            collectionCounter = 0;
            updateStatus("collecting from bin " + targetBinId);
            
            // Reset counters and history on successful bin reach
            turnsWithoutMove = 0;
            yieldsWithoutProgress = 0;
            recentPositions.clear();
            blockedPositions.clear();
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Finish the collection process for the current bin.
     */
    private void finishCollection() {
        isCollectingFromBin = false;
        collectionCounter = 0;
        
        if (targetBinId == null) {
            System.out.println("Car " + id + " finished collecting but has no target bin ID");
            updateStatus("idle");
            return;
        }
        
        try {
            // Empty the bin and update vehicle load
            double collectedAmount = emptyTargetBin();
            
            // Update our load (cap at capacity)
            currentLoad = Math.min(garbageCapacity, currentLoad + collectedAmount);
            
            // Track completion
            collectionsCompleted++;
            
            // Record last empty time
            lastEmptyTime.put(targetBinId, System.currentTimeMillis());
            
            // Release target
            releaseTargetBin();
            
            // Check if we need to return to depot
            if (currentLoad >= garbageCapacity * 0.9) {
                returnToDepot();
            } else {
                // Immediately look for a new target if we're not returning to depot
                findNewTarget();
            }
        } catch (Exception e) {
            System.out.println("Vehicle " + id + " (" + type + ") encountered error during collection: " + e.getMessage());
            // Ensure we clean up properly even if there's an error
            if (targetBinId != null) {
                releaseTargetBin();
            }
            updateStatus("idle");
        }
    }
    
    /**
     * Empty the target bin.
     * 
     * @return Amount collected from the bin
     */
    private double emptyTargetBin() {
        double collectedAmount = 0;
        
        Context<Object> context = ContextUtils.getContext(this);
        for (Object obj : context.getObjects(GarbageBin.class)) {
            GarbageBin bin = (GarbageBin) obj;
            if (bin.getId() == targetBinId) {
                double fillLevel = bin.getCurrentFill();
                double availableCapacity = garbageCapacity - currentLoad;
                
                if (fillLevel <= availableCapacity) {
                    // Can completely empty the bin
                    collectedAmount = bin.getCurrentFill();
                    bin.empty();
                    System.out.println("Vehicle " + id + " (" + type + ") completely emptied bin " + targetBinId);
                } else {
                    // Can only partially empty the bin
                    collectedAmount = availableCapacity;
                    bin.reduceBy(availableCapacity);
                    System.out.println("Vehicle " + id + " (" + type + ") partially emptied bin " + targetBinId + 
                                     " - vehicle now at " + String.format("%.1f", (currentLoad/garbageCapacity*100)) + "% capacity");
                }
                break;
            }
        }
        
        return collectedAmount;
    }
    
    /**
     * Release the currently targeted bin.
     */
    private void releaseTargetBin() {
        if (targetBinId == null) return;
        
        releaseBinAssignment(targetBinId);
        
        System.out.println("Vehicle " + id + " (" + type + ") releasing target bin " + targetBinId);
        
        targetBinId = null;
        targetDestination = null;
        updateStatus("seeking target");
    }
    
    /**
     * Start returning to the depot.
     */
    private void returnToDepot() {
        // Clear any current target
        if (targetBinId != null) {
            releaseTargetBin();
        }
        
        // Set depot as destination
        targetDestination = depotLocation;
        returningToDepot = true;
        updateStatus("returning to depot");
        
        System.out.println("Car " + id + " returning to depot with " + 
                         String.format("%.1f", (currentLoad/garbageCapacity*100)) + "% load");
                         
        // Clear movement history to avoid loop detection interfering with depot return
        recentPositions.clear();
        blockedPositions.clear();
    }
    
    /**
     * Check if vehicle has reached the depot.
     */
    private boolean checkDepotReached(GridPoint currentPos) {
        if (!returningToDepot) return false;
        
        double distance = getDistance(currentPos, depotLocation);
        
        if (distance <= 1.0) {
            System.out.println("Car " + id + " reached depot - unloading " + 
                             String.format("%.1f", currentLoad) + " units");
            
            // Empty the vehicle
            currentLoad = 0.0;
            returningToDepot = false;
            targetDestination = null;
            updateStatus("unloaded at depot");
            
            // Reset counters and history on successful depot reach
            turnsWithoutMove = 0;
            yieldsWithoutProgress = 0;
            recentPositions.clear();
            blockedPositions.clear();
            
            // Immediately look for a new target
            findNewTarget();
            return true;
        }
        
        return false;
    }
    
    /**
     * Update the status of the car and record the time of status change.
     */
    private void updateStatus(String newStatus) {
        if (!this.status.equals(newStatus)) {
            this.status = newStatus;
            this.lastStatusChangeTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Check if vehicle has capacity for a given amount.
     */
    private boolean hasCapacityFor(double amount) {
        return (currentLoad + amount <= garbageCapacity);
    }
    
    //----------------------------------------------------------------
    // Bin Assignment System (Static, shared across all cars)
    //----------------------------------------------------------------
    
    /**
     * Assign a bin to this car.
     */
    private boolean assignBin(int binId) {
        synchronized (binAssignments) {
            if (binAssignments.containsKey(binId)) {
                // Already assigned to some car
                int assignedCarId = binAssignments.get(binId);
                if (assignedCarId == this.id) {
                    // Already assigned to us
                    return true;
                }
                return false;
            }
            
            // Not assigned, claim it
            binAssignments.put(binId, this.id);
            System.out.println("Car " + id + " assigned to bin " + binId);
            return true;
        }
    }
    
    /**
     * Release assignment of a bin.
     */
    private void releaseBinAssignment(int binId) {
        synchronized (binAssignments) {
            // Only remove if it's assigned to us
            if (binAssignments.containsKey(binId) && binAssignments.get(binId) == this.id) {
                binAssignments.remove(binId);
                System.out.println("Car " + id + " released assignment to bin " + binId);
            }
        }
    }
    
    /**
     * Check if a bin is already assigned to any car.
     */
    private boolean isBinAssigned(int binId) {
        synchronized (binAssignments) {
            return binAssignments.containsKey(binId);
        }
    }
    
    /**
     * Check if a bin is assigned to this car.
     */
    private boolean isBinAssignedToMe(int binId) {
        synchronized (binAssignments) {
            return binAssignments.containsKey(binId) && binAssignments.get(binId) == this.id;
        }
    }
    
    //----------------------------------------------------------------
    // Getters for stats and debugging
    //----------------------------------------------------------------
    
    public int getId() {
        return id;
    }
    
    public String getType() {
        return type;
    }
    
    public String getStatus() {
        return status;
    }
    
    public double getCurrentLoad() {
        return currentLoad;
    }
    
    public double getCapacity() {
        return garbageCapacity;
    }
    
    public int getCollectionsCompleted() {
        return collectionsCompleted;
    }
    
    public double getTotalDistance() {
        return totalDistance;
    }
}