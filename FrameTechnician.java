/**
 * FrameTechnician.java
 * 
 * A concrete technician who has an infinite supply of drone frames.
 * This technician waits for propulsion units and firmware to appear on the table,
 * then assembles a complete drone.
 * 
 * @author SYSC 3303 Assignment 1
 * @version 1.0
 */
public class FrameTechnician extends Technician {
    
    /**
     * Constructs a new FrameTechnician with a reference to the shared table.
     * 
     * @param table The shared assembly table
     */
    public FrameTechnician(Table table) {
        super(table);
    }
    
    /**
     * Returns the component that this technician has an infinite supply of.
     * 
     * @return Component.FRAME
     */
    @Override
    public Component getComponent() {
        return Component.FRAME;
    }
}
