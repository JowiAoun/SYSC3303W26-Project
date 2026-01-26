import java.util.Random;

/**
 * Assembler.java
 * 
 * The agent/assembler thread that has an infinite supply of all three components
 * It randomly selects two different components and places them on the shared assembly table,
 * then waits for a technician to complete the assembly before placing the next pair
 * 
 * @author SYSC 3303 Assignment 1
 * @version 1.0
 */
public class Assembler implements Runnable {
    /** Reference to the shared assembly table */
    private Table table;
    
    /** Random number generator for selecting components */
    private Random rng;
    
    /**
     * Constructs a new Assembler with a reference to the shared table.
     * 
     * @param table The shared assembly table
     */
    public Assembler(Table table) {
        this.table = table;
        this.rng = new Random();
    }
    
    /**
     * Returns a random component from the available types
     * 
     * @return A randomly selected Component
     */
    private Component randomComponent() {
        Component[] components = Component.values();
        return components[rng.nextInt(components.length)];
    }
    
    /**
     * Main run loop for the assembler thread.
     * Continuously places two random different components on the table
     * until 20 drones have been assembled.
     */
    @Override
    public void run() {
        System.out.println("Assembler started (has infinite supply of all components)");
        
        while (!table.isComplete()) {
            // Wait for the table to be empty
            if (!table.waitUntilEmpty()) {
                break; // Max drones reached
            }
            
            if (table.isComplete()) {
                break;
            }
            
            // Select two different random components
            Component first = randomComponent();
            Component second;
            do {
                second = randomComponent();
            } while (second == first);
            
            // Determine which component is NOT being placed
            Component missing = null;
            for (Component c : Component.values()) {
                if (c != first && c != second) {
                    missing = c;
                    break;
                }
            }
            
            System.out.println("\n[Assembler] Placing components (missing: " + missing + ")");
            
            // Place the two components on the table
            table.add(first);
            table.add(second);
            
            // Notify technicians that components are available
            table.notifyTechnicians();
        }
        
        System.out.println("Assembler finished.");
    }
}
