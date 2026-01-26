/**
 * FirmwareTechnician.java
 * 
 * A concrete technician who has an infinite supply of control firmware.
 * This technician waits for frames and propulsion units to appear on the table,
 * then assembles a complete drone.
 * 
 * @author SYSC 3303 Assignment 1
 * @version 1.0
 */
public class FirmwareTechnician extends Technician {
    
    /**
     * Constructs a new FirmwareTechnician with a reference to the shared table.
     * 
     * @param table The shared assembly table
     */
    public FirmwareTechnician(Table table) {
        super(table);
    }
    
    /**
     * Returns the component that this technician has an infinite supply of.
     * 
     * @return Component.FIRMWARE
     */
    @Override
    public Component getComponent() {
        return Component.FIRMWARE;
    }
}
