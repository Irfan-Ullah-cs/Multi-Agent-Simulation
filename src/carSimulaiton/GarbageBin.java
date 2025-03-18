package carSimulaiton;

import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.grid.Grid;
import repast.simphony.random.RandomHelper;

/**
 * Represents a garbage bin placed along the roads in the car simulation.
 * Adapted for a grid environment with visual representation based on fill level.
 */
public class GarbageBin {
    // Area type constants
    public static final String AREA_COMMERCIAL = "COMMERCIAL";
    public static final String AREA_RESIDENTIAL = "RESIDENTIAL";
    public static final String AREA_LOW_DENSITY = "LOW_DENSITY";
    
    // Bin properties
    private int id;
    private double capacity;
    private double currentFill;
    private String areaType;
    private Grid<Object> grid;
    
    // Fill rate per tick based on area type
    private double fillRate;
    
    // Status
    private boolean beingServiced = false;
    
    /**
     * Creates a new garbage bin.
     * 
     * @param grid The simulation grid
     * @param id Unique identifier for the bin
     * @param capacity Maximum capacity of the bin
     * @param areaType Type of area where the bin is located
     */
    public GarbageBin(Grid<Object> grid, int id, double capacity, String areaType) {
        this.grid = grid;
        this.id = id;
        this.capacity = capacity;
        this.areaType = areaType;
        
        // Start with a random fill level between 0 and 50% of capacity
        this.currentFill = RandomHelper.nextDoubleFromTo(0, capacity * 0.5);
        
        // Set fill rate based on area type
        if (AREA_COMMERCIAL.equals(areaType)) {
            this.fillRate = 2.0; // Commercial areas fill up faster
        } else if (AREA_RESIDENTIAL.equals(areaType)) {
            this.fillRate = 1.0; // Residential areas fill at a medium rate
        } else {
            this.fillRate = 0.5; // Low density areas fill up slower
        }
    }
    
    /**
     * Simulates garbage accumulation over time.
     * This method should be called on each tick of the simulation.
     */
    @ScheduledMethod(start = 1, interval = 1)
    public void step() {
        // Don't accumulate garbage while being serviced
        if (beingServiced) {
            return;
        }
        
        // Add some randomness to the fill rate
        double actualFillRate = fillRate * RandomHelper.nextDoubleFromTo(0.8, 1.2);
        
        // Increase the current fill level
        currentFill += actualFillRate;
        
        // Cap at maximum capacity
        if (currentFill > capacity) {
            currentFill = capacity;
        }
    }
    
    /**
     * Empties the bin (simulating a garbage truck collection).
     * @return The amount of garbage collected
     */
    public double empty() {
        double collected = currentFill;
        currentFill = 0;
        beingServiced = false;
        return collected;
    }
    
    /**
     * Reduces the bin content by a specific amount.
     * @param amount Amount to remove from the bin
     * @return The actual amount removed
     */
    public double reduceBy(double amount) {
        double toRemove = Math.min(currentFill, amount);
        currentFill -= toRemove;
        beingServiced = false;
        return toRemove;
    }
    
    /**
     * Returns current fill level as a percentage of capacity.
     */
    public double getFillPercentage() {
        return (currentFill / capacity) * 100.0;
    }
    
    /**
     * Checks if the bin is full (>= 90% capacity).
     */
    public boolean isFull() {
        return currentFill >= (capacity * 0.9);
    }
    
    /**
     * Mark this bin as being serviced by a collection vehicle.
     */
    public void markAsBeingServiced() {
        beingServiced = true;
    }
    
    /**
     * Check if this bin is currently being serviced.
     */
    public boolean isBeingServiced() {
        return beingServiced;
    }
    
    // Getters and setters
    
    public int getId() {
        return id;
    }
    
    public double getCapacity() {
        return capacity;
    }
    
    public double getCurrentFill() {
        return currentFill;
    }
    
    public String getAreaType() {
        return areaType;
    }
    
    /**
     * Returns a string representation of the bin based on its fill level.
     * This is used by Repast for visualization.
     * Different strings can be mapped to different images in the Repast style editor.
     */
    @Override
    public String toString() {
        double fillPercent = getFillPercentage();
        
        if (beingServiced) {
            return "bin-servicing";  // Special case when being serviced
        } else if (fillPercent >= 90) {
            return "bin-full";       // Red bin when very full (90-100%)
        } else if (fillPercent >= 70) {
            return "bin-high";       // Orange bin when high fill (70-90%)
        } else if (fillPercent >= 40) {
            return "bin-medium";     // Yellow bin when medium fill (40-70%)
        } else if (fillPercent >= 10) {
            return "bin-low";        // Light green bin when low fill (10-40%)
        } else {
            return "bin-empty";      // Green bin when nearly empty (0-10%)
        }
    }
    
    /**
     * Returns a human-readable description of the bin and its status.
     */
    public String getDescription() {
        return "Bin " + id + " (" + areaType + "): " + 
                String.format("%.1f", getFillPercentage()) + "% full";
    }
}