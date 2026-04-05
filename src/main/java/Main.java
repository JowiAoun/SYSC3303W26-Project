/**
 * Main.java
 *
 * Starts the three subsystem threads and wires their queues together.
 */
import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {
    /**
     * Usage: java Main
     */
    public static void main(String[] args) {
        String inputPath = "./src/main/resources/data/events.csv";
        int droneCount = 3;

        // Build GUI (on EDT)
        FireDroneGUI gui = new FireDroneGUI(droneCount);
        javax.swing.SwingUtilities.invokeLater(() -> gui.setVisible(true));

        // Build subsystems.
        FireIncidentSubsystem fireIncident = null;
        try {
            fireIncident = new FireIncidentSubsystem(inputPath);
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e);
        }

        // Create drone instances
        DroneSubsystem[] drones = new DroneSubsystem[droneCount];
        for (int i = 0; i < droneCount; i++) {
            try {
                drones[i] = new DroneSubsystem(i + 1);
            } catch (SocketException | UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        // Pass GUI to Scheduler
        Scheduler scheduler = null;
        try {
            scheduler = new Scheduler(gui);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        // Launch threads.
        Thread fireThread = new Thread(fireIncident, "FireIncidentSubsystem");
        Thread[] droneThreads = new Thread[droneCount];
        for (int i = 0; i < droneCount; i++) {
            droneThreads[i] = new Thread(drones[i], "DroneSubsystem-" + (i + 1));
        }
        Thread schedulerThread = new Thread(scheduler, "Scheduler");

        fireThread.start();
        for (Thread dt : droneThreads) {
            dt.start();
        }
        schedulerThread.start();

        try {
            fireThread.join();
            for (Thread dt : droneThreads) {
                dt.join();
            }
            schedulerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        fireIncident.closeSocket();
        for (DroneSubsystem d : drones) {
            d.closeSocket();
        }
        scheduler.closeSocket();

        // Print performance metrics
        System.out.println("Average response time: " + scheduler.getAverageResponseTime() + " seconds");
        System.out.println("Maximum response time: " + scheduler.getMaxResponseTime() + " seconds");
        System.out.println("Average completion time: " + scheduler.getAverageCompletionTime() + " seconds");
        System.out.println("Maximum completion time: " + scheduler.getMaxCompletionTime() + " seconds");
    }
}
