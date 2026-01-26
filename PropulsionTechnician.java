/**
 * PropulsionTechnician.java
 * 
 * A concrete technician who has an infinite supply of propulsion units.
 * This technician waits for frames and firmware to appear on the table,
 * then assembles a complete drone.
 * 
 * @author SYSC 3303 Assignment 1
 * @version 1.0
 */
public class PropulsionTechnician extends Technician {
    
    /**
     * Constructs a new PropulsionTechnician with a reference to the shared table.
     * 
     * @param table The shared assembly table
     */
    public PropulsionTechnician(Table table) {
        super(table);
    }
    
    /**
     * Returns the component that this technician has an infinite supply of.
     * 
     * @return Component.PROPULSION
     */
    @Override
    public Component getComponent() {
        return Component.PROPULSION;
    }
}
