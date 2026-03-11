/**
 * Main.java
 *
 * Starts the three subsystem threads and wires their queues together.
 */
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.UnknownHostException;
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
        FireIncidentSubsystem fireIncident = null;
        try {
            fireIncident = new FireIncidentSubsystem(inputPath, toScheduler, schedulerToFire);
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e);
        }
        DroneSubsystem drone = null;
        try {
            drone = new DroneSubsystem(toScheduler, schedulerToDrone);
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e);
        }
        // Pass GUI to Scheduler
        Scheduler scheduler = null;
        try {
            scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone, gui);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

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
        fireIncident.closeSocket();
        drone.closeSocket();
        scheduler.closeSocket();
    }
}
