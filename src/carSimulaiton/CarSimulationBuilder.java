package carSimulaiton;

import java.util.Collection;

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

        // Create roads and intersections
        createRoadNetwork(context, grid);
        
        // Add traffic lights at intersections
        addTrafficLights(context, grid);
        
        // Add cars, starting on roads
        addCars(context, grid, 20);  // 20 cars


        return context;
    }
    
    private void createRoadNetwork(Context<Object> context, Grid<Object> grid) {
        int[] roadPositions = {10, 20, 30, 40};
        
        // Add horizontal roads with alternating directions
        for (int i = 0; i < roadPositions.length; i++) {
            int y = roadPositions[i];
            Road.Direction direction = (i % 2 == 0) ? Road.Direction.EASTBOUND : Road.Direction.WESTBOUND;
            
            for (int x = 0; x < 50; x++) {
                Road road;
                if (isIntersection(x, y, roadPositions)) {
                    road = new Road(Road.RoadType.INTERSECTION, Road.Direction.ALL);
                } else {
                    road = new Road(Road.RoadType.HORIZONTAL, direction);
                }
                context.add(road);
                grid.moveTo(road, x, y);
            }
        }
        
        // Add vertical roads with alternating directions
        for (int i = 0; i < roadPositions.length; i++) {
            int x = roadPositions[i];
            Road.Direction direction = (i % 2 == 0) ? Road.Direction.NORTHBOUND : Road.Direction.SOUTHBOUND;
            
            for (int y = 0; y < 50; y++) {
                // Skip intersections as they were already added
                if (!isIntersection(x, y, roadPositions)) {
                    Road road = new Road(Road.RoadType.VERTICAL, direction);
                    context.add(road);
                    grid.moveTo(road, x, y);
                }
            }
        }
        
        System.out.println("Road network created with one-way roads and intersections.");
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
                    
                    // Set initial direction based on road direction
                    Car.Direction carDirection = null;
                    switch (foundRoad.getDirection()) {
                        case EASTBOUND:
                            carDirection = Car.Direction.EAST;
                            break;
                        case WESTBOUND:
                            carDirection = Car.Direction.WEST;
                            break;
                        case NORTHBOUND:
                            carDirection = Car.Direction.NORTH;
                            break;
                        case SOUTHBOUND:
                            carDirection = Car.Direction.SOUTH;
                            break;
                        default:
                            carDirection = Car.Direction.values()[RandomHelper.nextIntFromTo(0, 3)];
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