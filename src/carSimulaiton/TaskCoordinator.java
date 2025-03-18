package carSimulaiton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import repast.simphony.space.continuous.NdPoint;

/**
 * Central coordinator for task allocation in the garbage collection system.
 * This class handles the global coordination of bin assignments to vehicles.
 */
public class TaskCoordinator {
    // Singleton instance
    private static TaskCoordinator instance = null;
    
    // Map bin IDs to assigned vehicle IDs
    private Map<Integer, Integer> binAssignments = new HashMap<>();
    
    // Track which bins are currently being serviced
    private Set<Integer> binsBeingServiced = new HashSet<>();
    
    // Track recently emptied bins (to avoid immediate reassignment)
    private Map<Integer, Long> recentlyEmptiedBins = new HashMap<>();
    private static final long EMPTY_COOLDOWN = 5000; // 5 seconds
    
    // Track assignment times to detect stale assignments
    private Map<Integer, Long> assignmentTimes = new HashMap<>();
    private static final long ASSIGNMENT_TIMEOUT = 20000; // 20 seconds
    
    // Track vehicle-bin assignments historically
    private Map<String, Integer> assignmentHistory = new HashMap<>(); // key: "vehicleId-binId", value: count
    private static final int MAX_REPEATED_ASSIGNMENTS = 3; // Maximum times a vehicle can be assigned to same bin
    
    /**
     * Private constructor for singleton pattern
     */
    private TaskCoordinator() {
        // Private constructor
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized TaskCoordinator getInstance() {
        if (instance == null) {
            instance = new TaskCoordinator();
        }
        return instance;
    }
    
    /**
     * Request a bin assignment.
     * 
     * @param vehicleId ID of the requesting vehicle
     * @param binId ID of the bin to request
     * @return true if bin was assigned, false if already assigned or being serviced
     */
    public synchronized boolean requestBinAssignment(int vehicleId, int binId) {
        // Check for and clean up stale assignments first
        cleanupStaleAssignments();
        
        // Check if bin is already assigned or serviced
        if (binAssignments.containsKey(binId) || binsBeingServiced.contains(binId)) {
            return false;
        }
        
        // Check if recently emptied
        Long emptyTime = recentlyEmptiedBins.get(binId);
        if (emptyTime != null && System.currentTimeMillis() - emptyTime < EMPTY_COOLDOWN) {
            return false;
        }
        
        // Check if this vehicle has been repeatedly assigned to this bin and failed
        String assignmentKey = vehicleId + "-" + binId;
        int assignmentCount = assignmentHistory.getOrDefault(assignmentKey, 0);
        if (assignmentCount >= MAX_REPEATED_ASSIGNMENTS) {
            System.out.println("TaskCoordinator: Rejecting assignment of Bin " + binId + 
                             " to Vehicle " + vehicleId + " due to repeated failed assignments");
            return false;
        }
        
        // Assign bin to vehicle
        binAssignments.put(binId, vehicleId);
        assignmentTimes.put(binId, System.currentTimeMillis());
        assignmentHistory.put(assignmentKey, assignmentCount + 1);
        System.out.println("TaskCoordinator: Bin " + binId + " assigned to Vehicle " + vehicleId);
        return true;
    }
    
    /**
     * Mark a bin as being serviced.
     * 
     * @param vehicleId ID of the vehicle servicing the bin
     * @param binId ID of the bin being serviced
     * @return true if operation was successful
     */
    public synchronized boolean markBinBeingServiced(int vehicleId, int binId) {
        // Check if bin is assigned to this vehicle
        Integer assignedVehicle = binAssignments.get(binId);
        if (assignedVehicle != null && assignedVehicle == vehicleId) {
            binsBeingServiced.add(binId);
            System.out.println("TaskCoordinator: Bin " + binId + " now being serviced by Vehicle " + vehicleId);
            return true;
        }
        return false;
    }
    
    /**
     * Release a bin assignment (e.g., when bin has been emptied)
     * 
     * @param vehicleId ID of the vehicle that was assigned to the bin
     * @param binId ID of the bin to release
     */
    public synchronized void releaseBin(int vehicleId, int binId) {
        // Check if bin is assigned to this vehicle
        Integer assignedVehicle = binAssignments.get(binId);
        if (assignedVehicle != null && assignedVehicle == vehicleId) {
            binAssignments.remove(binId);
            binsBeingServiced.remove(binId);
            assignmentTimes.remove(binId);
            recentlyEmptiedBins.put(binId, System.currentTimeMillis());
            
            // Reset the assignment history for successful collection
            String assignmentKey = vehicleId + "-" + binId;
            assignmentHistory.put(assignmentKey, 0);
            
            System.out.println("TaskCoordinator: Bin " + binId + " released by Vehicle " + vehicleId);
        }
    }
    
    /**
     * Force release a bin assignment (for stale assignments)
     * 
     * @param binId ID of the bin to force release
     */
    private synchronized void forceReleaseBin(int binId) {
        Integer vehicleId = binAssignments.get(binId);
        if (vehicleId != null) {
            System.out.println("TaskCoordinator: Force releasing stale assignment of Bin " + binId + 
                             " from Vehicle " + vehicleId);
            binAssignments.remove(binId);
            binsBeingServiced.remove(binId);
            assignmentTimes.remove(binId);
        }
    }
    
    /**
     * Force release all assignments for a specific bin
     * This is a public method that can be called by bins when they detect recurring timeouts
     * 
     * @param binId ID of the bin to force release all assignments for
     */
    public synchronized void forceReleaseAllBinAssignments(int binId) {
        // Check if bin is assigned to a vehicle
        if (binAssignments.containsKey(binId)) {
            Integer vehicleId = binAssignments.get(binId);
            System.out.println("TaskCoordinator: Forced release of all assignments for Bin " + binId);
            
            // Release the assignment
            binAssignments.remove(binId);
            binsBeingServiced.remove(binId);
            assignmentTimes.remove(binId);
            
            // Mark this as a problematic assignment to avoid repeating
            if (vehicleId != null) {
                String assignmentKey = vehicleId + "-" + binId;
                assignmentHistory.put(assignmentKey, MAX_REPEATED_ASSIGNMENTS);
            }
        }
    }
    
    /**
     * Check if a bin is assigned to a specific vehicle
     * 
     * @param vehicleId ID of the vehicle
     * @param binId ID of the bin
     * @return true if bin is assigned to this vehicle
     */
    public synchronized boolean isBinAssignedToVehicle(int vehicleId, int binId) {
        Integer assignedVehicle = binAssignments.get(binId);
        return assignedVehicle != null && assignedVehicle == vehicleId;
    }
    
    /**
     * Check if a bin is available (not assigned or being serviced)
     * 
     * @param binId ID of the bin
     * @return true if bin is available
     */
    public synchronized boolean isBinAvailable(int binId) {
        // Clean up stale assignments first
        cleanupStaleAssignments();
        
        return !binAssignments.containsKey(binId) && !binsBeingServiced.contains(binId);
    }
    
    /**
     * Check if a bin was recently emptied
     * 
     * @param binId ID of the bin
     * @return true if bin was emptied within the cooldown period
     */
    public synchronized boolean wasRecentlyEmptied(int binId) {
        Long emptyTime = recentlyEmptiedBins.get(binId);
        return emptyTime != null && System.currentTimeMillis() - emptyTime < EMPTY_COOLDOWN;
    }
    
    /**
     * Clean up stale assignments
     */
    private synchronized void cleanupStaleAssignments() {
        // Find stale assignments
        long currentTime = System.currentTimeMillis();
        Set<Integer> staleBins = new HashSet<>();
        
        for (Map.Entry<Integer, Long> entry : assignmentTimes.entrySet()) {
            if (currentTime - entry.getValue() > ASSIGNMENT_TIMEOUT) {
                staleBins.add(entry.getKey());
            }
        }
        
        // Force release stale assignments
        for (Integer binId : staleBins) {
            forceReleaseBin(binId);
        }
        
        // Also clean up recently emptied bins that are no longer in cooldown
        Set<Integer> oldBins = new HashSet<>();
        for (Map.Entry<Integer, Long> entry : recentlyEmptiedBins.entrySet()) {
            if (currentTime - entry.getValue() > EMPTY_COOLDOWN) {
                oldBins.add(entry.getKey());
            }
        }
        
        for (Integer binId : oldBins) {
            recentlyEmptiedBins.remove(binId);
        }
    }
}