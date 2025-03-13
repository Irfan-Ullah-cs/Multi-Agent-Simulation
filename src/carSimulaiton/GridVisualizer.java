package carSimulaiton;


import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;

/**
 * This class provides a simple console-based visualization of the simulation state.
 */
public class GridVisualizer {
    private Grid<Object> grid;
    
    public GridVisualizer(Grid<Object> grid) {
        this.grid = grid;
    }
    
    @ScheduledMethod(start = 1, interval = 5)
    public void visualize() {
        int width = grid.getDimensions().getWidth();
        int height = grid.getDimensions().getHeight();
        
        // Only visualize a portion of the grid if it's large
        int maxVisWidth = Math.min(width, 20);
        int maxVisHeight = Math.min(height, 20);
        
        System.out.println("\n===== GRID VISUALIZATION =====");
        
        // Print the grid
        for (int y = 0; y < maxVisHeight; y++) {
            StringBuilder row = new StringBuilder();
            
            for (int x = 0; x < maxVisWidth; x++) {
                char cellChar = ' ';
                boolean hasCar = false;
                boolean hasTrafficLight = false;
                boolean hasRoad = false;
                Car.Direction carDirection = null;
                TrafficLight.LightState lightState = null;
                
                for (Object obj : grid.getObjectsAt(x, y)) {
                    if (obj instanceof Car) {
                        hasCar = true;
                        carDirection = ((Car) obj).getCurrentDirection();
                    } else if (obj instanceof TrafficLight) {
                        hasTrafficLight = true;
                        lightState = ((TrafficLight) obj).getState();
                    } else if (obj instanceof Road) {
                        hasRoad = true;
                    }
                }
                
                // Show what's in this cell
                if (hasCar) {
                    // Car takes precedence in visualization
                    if (carDirection != null) {
                        switch (carDirection) {
                            case EAST:  cellChar = '>'; break;
                            case WEST:  cellChar = '<'; break;
                            case NORTH: cellChar = '^'; break;
                            case SOUTH: cellChar = 'v'; break;
                            default:    cellChar = 'C'; break;
                        }
                    } else {
                        cellChar = 'C';  // Generic car
                    }
                } else if (hasTrafficLight) {
                    // Traffic light
                    if (lightState != null) {
                        switch (lightState) {
                            case RED:    cellChar = 'R'; break;
                            case YELLOW: cellChar = 'Y'; break;
                            case GREEN:  cellChar = 'G'; break;
                            default:     cellChar = 'T'; break;
                        }
                    } else {
                        cellChar = 'T';  // Generic traffic light
                    }
                } else if (hasRoad) {
                    // Road
                    cellChar = '.';
                }
                
                row.append(cellChar);
            }
            
            System.out.println(row.toString());
        }
        
        System.out.println("==============================");
        System.out.println("Legend: ");
        System.out.println("> = Car facing East");
        System.out.println("< = Car facing West");
        System.out.println("^ = Car facing North");
        System.out.println("v = Car facing South");
        System.out.println("R = Red Traffic Light");
        System.out.println("Y = Yellow Traffic Light");
        System.out.println("G = Green Traffic Light");
        System.out.println(". = Road");
        System.out.println("==============================\n");
    }
}
