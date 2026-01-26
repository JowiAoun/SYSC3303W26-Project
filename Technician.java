/**
 * Technician.java
 * 
 * Abstract base class for drone technicians. Each technician has an infinite supply
 * of one specific component and waits for the other two components to appear on the
 * assembly table before completing a drone assembly.
 * 
 * @author SYSC 3303 Assignment 1
 * @version 1.0
 */
public abstract class Technician implements Runnable {
    /** Reference to the shared assembly table */
    protected Table table;
    
    /** Counter for frames used by this technician */
    protected int numFrames;
    
    /** Counter for propulsion units used by this technician */
    protected int numPropulsion;
    
    /** Counter for firmware used by this technician */
    protected int numFirmware;
    
    /** Counter for drones assembled by this technician */
    protected int dronesAssembled;
    
    /**
     * Constructs a new Technician with a reference to the shared table.
     * 
     * @param table The shared assembly table
     */
    public Technician(Table table) {
        this.table = table;
        this.numFrames = 0;
        this.numPropulsion = 0;
        this.numFirmware = 0;
        this.dronesAssembled = 0;
    }
    
    /**
     * Returns the component that this technician has an infinite supply of.
     * Each concrete technician subclass must implement this method.
     * 
     * @return The component this technician provides
     */
    public abstract Component getComponent();
    
    /**
     * Checks if this technician should assemble a drone.
     * The technician should assemble if the table has the other two components
     * that this technician doesn't have.
     * 
     * @return true if the technician should assemble
     */
    protected boolean shouldAssemble() {
        Component myComponent = getComponent();
        switch (myComponent) {
            case FRAME:
                return table.has(Component.PROPULSION) && table.has(Component.FIRMWARE);
            case PROPULSION:
                return table.has(Component.FRAME) && table.has(Component.FIRMWARE);
            case FIRMWARE:
                return table.has(Component.FRAME) && table.has(Component.PROPULSION);
            default:
                return false;
        }
    }
    
    /**
     * Prints a message indicating that a drone has been assembled and is ready for deployment.
     * 
     * @param droneNumber The sequential number of the assembled drone
     */
    protected void printReadyToAssemble(int droneNumber) {
        System.out.println(">>> Drone #" + droneNumber + " assembled by " + 
                           getClass().getSimpleName() + " (using " + getComponent() + 
                           ") and is READY FOR DEPLOYMENT <<<");
    }
    
    /**
     * Main run loop for the technician thread.
     * Continuously waits for the required components and assembles drones
     * until 20 drones have been assembled.
     */
    @Override
    public void run() {
        System.out.println(getClass().getSimpleName() + " started (has infinite " + 
                           getComponent() + " supply)");
        
        while (!table.isComplete()) {
            // Wait for the other two components and assemble
            if (table.assemble(getComponent())) {
                dronesAssembled++;
                
                // Update component counters based on what we used
                switch (getComponent()) {
                    case FRAME:
                        numFrames++;
                        numPropulsion++;
                        numFirmware++;
                        break;
                    case PROPULSION:
                        numFrames++;
                        numPropulsion++;
                        numFirmware++;
                        break;
                    case FIRMWARE:
                        numFrames++;
                        numPropulsion++;
                        numFirmware++;
                        break;
                }
                
                // Print that the drone is ready
                printReadyToAssemble(table.getDronesAssembled());
            }
        }
        
        System.out.println(getClass().getSimpleName() + " finished. Assembled " + 
                           dronesAssembled + " drones.");
    }
}
