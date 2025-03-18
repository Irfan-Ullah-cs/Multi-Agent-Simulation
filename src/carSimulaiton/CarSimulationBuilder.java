package carSimulaiton;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import repast.simphony.context.Context;
import repast.simphony.context.space.grid.GridFactory;
import repast.simphony.context.space.grid.GridFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridBuilderParameters;
import repast.simphony.space.grid.SimpleGridAdder;
import repast.simphony.space.grid.WrapAroundBorders;
import repast.simphony.random.RandomHelper;

public class CarSimulationBuilder implements ContextBuilder<Object> {
    @Override
    public Context<Object> build(Context<Object> context) {
        context.setId("CarSimulation");

        // Create a 50x50 grid
        GridFactory gridFactory = GridFactoryFinder.createGridFactory(null);
        Grid<Object> grid = gridFactory.createGrid("Grid", context,
                new GridBuilderParameters<Object>(
                        new WrapAroundBorders(),
                        new SimpleGridAdder<Object>(),
                        true, // Prevent multiple occupancy
                        50, 50));

        // Create roads and intersections with bidirectional support
        createBidirectionalRoadNetwork(context, grid);
        
        // Add traffic lights at intersections
        addTrafficLights(context, grid);
        
        // Add garbage bins on the sides of roads (reduced to 5)
        addLimitedGarbageBins(context, grid, 5);
        
        // Add cars, starting on roads (reduced from 10 to 5 to decrease congestion)
        addCars(context, grid, 5);  

        return context;
    }
    
    /**
     * Creates a road network with bidirectional roads instead of one-way roads.
     */
    private void createBidirectionalRoadNetwork(Context<Object> context, Grid<Object> grid) {
        int[] roadPositions = {10, 20, 30, 40};
        
        // Add horizontal roads - all bidirectional
        for (int i = 0; i < roadPositions.length; i++) {
            int y = roadPositions[i];
            
            for (int x = 0; x < 50; x++) {
                Road road;
                if (isIntersection(x, y, roadPositions)) {
                    road = new Road(Road.RoadType.INTERSECTION, Road.Direction.ALL);
                } else {
                    road = new Road(Road.RoadType.HORIZONTAL, Road.Direction.BIDIRECTIONAL);
                }
                context.add(road);
                grid.moveTo(road, x, y);
            }
        }
        
        // Add vertical roads - all bidirectional
        for (int i = 0; i < roadPositions.length; i++) {
            int x = roadPositions[i];
            
            for (int y = 0; y < 50; y++) {
                // Skip intersections as they were already added
                if (!isIntersection(x, y, roadPositions)) {
                    Road road = new Road(Road.RoadType.VERTICAL, Road.Direction.BIDIRECTIONAL);
                    context.add(road);
                    grid.moveTo(road, x, y);
                }
            }
        }
        
        System.out.println("Road network created with bidirectional roads and intersections.");
    }
    
    private boolean isIntersection(int x, int y, int[] roadPositions) {
        boolean xIsRoadPos = false;
        boolean yIsRoadPos = false;
        
        for (int pos : roadPositions) {
            if (x == pos) xIsRoadPos = true;
            if (y == pos) yIsRoadPos = true;
        }
        
        return xIsRoadPos && yIsRoadPos;
    }
    
    private void addTrafficLights(Context<Object> context, Grid<Object> grid) {
        int[] roadPositions = {10, 20, 30, 40};
        
        // Add traffic lights at intersections with different initial states
        // to avoid all lights being synchronized
        TrafficLight.LightState[] states = {
            TrafficLight.LightState.GREEN,
            TrafficLight.LightState.YELLOW,
            TrafficLight.LightState.RED
        };
        
        int stateIndex = 0;
        
        // Add traffic lights at intersections
        for (int x : roadPositions) {
            for (int y : roadPositions) {
                TrafficLight light = new TrafficLight(states[stateIndex % states.length]);
                context.add(light);
                grid.moveTo(light, x, y);
                stateIndex++;
            }
        }
        
        System.out.println("Traffic lights added at all intersections with varied initial states.");
    }
    
    /**
     * Add a limited number of garbage bins to the simulation.
     * This method adds exactly the specified number of bins, strategically placed.
     * 
     * @param context The simulation context
     * @param grid The simulation grid
     * @param numBins The target number of bins to add
     */
    private void addLimitedGarbageBins(Context<Object> context, Grid<Object> grid, int numBins) {
        int[] roadPositions = {10, 20, 30, 40};
        int binCounter = 1;
        
        // Fixed bin positions to distribute them across different areas
        int[][] strategicPositions = {
            // Commercial area (upper right)
            {35, 39},  // Near horizontal road
            
            // Residential area (bottom left)
            {15, 11},  // Near horizontal road
            
            // Low density areas
            {45, 25},  // Right side
            {15, 39},  // Upper left
            {35, 11}   // Bottom right
        };
        
        // Limit to the requested number of bins
        int binsToDeploy = Math.min(numBins, strategicPositions.length);
        
        // Add the strategic bins
        for (int i = 0; i < binsToDeploy; i++) {
            int x = strategicPositions[i][0];
            int y = strategicPositions[i][1];
            
            // Check if position is available
            if (isPositionAvailable(grid, x, y)) {
                // Choose area type based on quadrant
                String areaType = getAreaTypeByLocation(x, y);
                double capacity = RandomHelper.nextDoubleFromTo(60.0, 150.0);
                
                GarbageBin bin = new GarbageBin(grid, binCounter++, capacity, areaType);
                context.add(bin);
                grid.moveTo(bin, x, y);
            }
        }
        
        System.out.println("Added " + (binCounter - 1) + " garbage bins along the sides of roads.");
    }
    
    /**
     * Determines if a position is near an intersection (including the intersection itself).
     */
    private boolean isNearIntersection(int x, int y, int[] roadPositions) {
        // Check if this is an intersection
        if (isIntersection(x, y, roadPositions)) {
            return true;
        }
        
        // Check if it's within 2 units of an intersection
        for (int xPos : roadPositions) {
            for (int yPos : roadPositions) {
                if (Math.abs(x - xPos) <= 2 && Math.abs(y - yPos) <= 2) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a position is available (no road or other object).
     */
    private boolean isPositionAvailable(Grid<Object> grid, int x, int y) {
        return ((Collection<Object>) grid.getObjectsAt(x, y)).isEmpty();
    }
    
    /**
     * Determines an area type based on the position in the grid.
     */
    private String getAreaTypeByLocation(int x, int y) {
        // Dividing the grid into four quadrants
        // Upper right (x > 25, y > 25): Commercial
        if (x > 25 && y > 25) {
            return "COMMERCIAL";
        }
        // Bottom left (x < 25, y < 25): Residential
        else if (x < 25 && y < 25) {
            return "RESIDENTIAL";
        }
        // Others: Low density
        else {
            return "LOW_DENSITY";
        }
    }
    
    private void addCars(Context<Object> context, Grid<Object> grid, int numCars) {
        int[] roadPositions = {10, 20, 30, 40};
        
        for (int i = 0; i < numCars; i++) {
            Car car = new Car(grid);
            context.add(car);
            
            // Place cars randomly on roads (not at intersections)
            boolean placed = false;
            while (!placed) {
                // Choose random road segment
                boolean isHorizontal = RandomHelper.nextIntFromTo(0, 1) == 0;
                int roadIndex = RandomHelper.nextIntFromTo(0, roadPositions.length - 1);
                int roadPos = roadPositions[roadIndex];
                
                // Choose a position along the road
                int otherPos = RandomHelper.nextIntFromTo(0, 49);
                
                // Determine x and y based on road orientation
                int x = isHorizontal ? otherPos : roadPos;
                int y = isHorizontal ? roadPos : otherPos;
                
                // Skip intersections
                if (isIntersection(x, y, roadPositions)) {
                    continue;
                }
                
                // Check if location is available (has road but no car)
                Road foundRoad = null;
                boolean hasCar = false;
                
                for (Object obj : grid.getObjectsAt(x, y)) {
                    if (obj instanceof Road) {
                        foundRoad = (Road) obj;
                    }
                    if (obj instanceof Car) {
                        hasCar = true;
                    }
                }
                
                if (foundRoad != null && !hasCar) {
                    grid.moveTo(car, x, y);
                    
                    // Set initial direction based on road type
                    Car.Direction carDirection;
                    
                    if (foundRoad.getType() == Road.RoadType.HORIZONTAL) {
                        // For horizontal roads, randomly choose EAST or WEST
                        carDirection = (RandomHelper.nextDouble() < 0.5) ? 
                                      Car.Direction.EAST : Car.Direction.WEST;
                    } else {
                        // For vertical roads, randomly choose NORTH or SOUTH
                        carDirection = (RandomHelper.nextDouble() < 0.5) ? 
                                      Car.Direction.NORTH : Car.Direction.SOUTH;
                    }
                    
                    car.setInitialDirection(carDirection);
                    placed = true;
                    System.out.println("Car placed at (" + x + ", " + y + ") with direction " + carDirection);
                }
            }
        }
        
        System.out.println("Added " + numCars + " cars to the simulation.");
    }
}