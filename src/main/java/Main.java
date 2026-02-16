/**
 * Main.java
 *
 * Starts the three subsystem threads and wires their queues together.
 */
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Main {
    /**
     * Usage: java Main
     */
    public static void main(String[] args) {
        String inputPath = "./src/main/resources/data/events.csv";

        // Message buffers
        MessageBuffer toScheduler = new MessageBuffer();
        MessageBuffer schedulerToFire = new MessageBuffer();
        MessageBuffer schedulerToDrone = new MessageBuffer();

        // Build GUI (on EDT)
        FireDroneGUI gui = new FireDroneGUI();
        javax.swing.SwingUtilities.invokeLater(() -> gui.setVisible(true));

        // Build subsystems.
        FireIncidentSubsystem fireIncident = new FireIncidentSubsystem(inputPath, toScheduler, schedulerToFire);
        DroneSubsystem drone = new DroneSubsystem(toScheduler, schedulerToDrone);
        // Pass GUI to Scheduler
        Scheduler scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone, gui);

        // Launch threads.
        Thread fireThread = new Thread(fireIncident, "FireIncidentSubsystem");
        Thread droneThread = new Thread(drone, "DroneSubsystem");
        Thread schedulerThread = new Thread(scheduler, "Scheduler");

        fireThread.start();
        droneThread.start();
        schedulerThread.start();

        try {
            fireThread.join();
            droneThread.join();
            schedulerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
