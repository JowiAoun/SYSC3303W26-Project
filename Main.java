/**
 * Main.java
 *
 * The main driver class for the Autonomous Drone Assembly Line simulation.
 * Creates and starts all threads, then waits for the simulation to complete.
 *
 * This program simulates the classic "Cigarette Smokers Problem" adapted as
 * an autonomous drone assembly scenario. Three technicians, each with an
 * infinite supply of one component, work with an assembler who places
 * random pairs of components on a shared table.
 * 
 * @author SYSC 3303 Assignment 1
 * @version 1.0
 */
public class Main {
    
    /**
     * Main entry point for the simulation.
     */
    static void main() {
        System.out.println("=".repeat(60));
        System.out.println("       AUTONOMOUS DRONE ASSEMBLY LINE SIMULATION");
        System.out.println("=".repeat(60));
        System.out.println("Goal: Assemble 20 drones for deployment\n");
        
        // Create the shared assembly table (monitor)
        Table table = new Table();
        
        // Create the technician threads
        Technician frameTech = new FrameTechnician(table);
        Technician propulsionTech = new PropulsionTechnician(table);
        Technician firmwareTech = new FirmwareTechnician(table);
        
        // Create the assembler thread
        Assembler assembler = new Assembler(table);
        
        // Create thread objects
        Thread frameTechThread = new Thread(frameTech, "FrameTechnician");
        Thread propulsionTechThread = new Thread(propulsionTech, "PropulsionTechnician");
        Thread firmwareTechThread = new Thread(firmwareTech, "FirmwareTechnician");
        Thread assemblerThread = new Thread(assembler, "Assembler");
        
        // Start all threads
        System.out.println("Starting all threads...\n");
        frameTechThread.start();
        propulsionTechThread.start();
        firmwareTechThread.start();
        assemblerThread.start();
        
        // Wait for all threads to complete
        try {
            assemblerThread.join();
            frameTechThread.join();
            propulsionTechThread.join();
            firmwareTechThread.join();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        
        // Print final summary
        System.out.println("\n" + "=".repeat(60));
        System.out.println("                    SIMULATION COMPLETE");
        System.out.println("=".repeat(60));
        System.out.println("Total drones assembled and ready for deployment: " + 
                           table.getDronesAssembled());
        System.out.println("=".repeat(60));
    }
}
