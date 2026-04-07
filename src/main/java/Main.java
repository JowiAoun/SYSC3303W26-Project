/**
 * Main.java
 *
 * Starts the three subsystem threads and wires their queues together.
 */
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class Main {
    /**
     * Usage: java Main
     */
    public static void main(String[] args) {
        String inputPath = "./src/main/resources/data/events.csv";
        int droneCount = 20;

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

        // Wait until every registered drone is IDLE and all fire events are completed,
        // so timing metrics are populated before threads wind down.
        long deadline = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
        while (schedulerThread.isAlive() && System.currentTimeMillis() < deadline) {
            if (scheduler.isProcessingComplete()) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        try {
            fireThread.join();
            for (Thread dt : droneThreads) {
                dt.join();
            }
            schedulerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fireIncident.closeSocket();
            for (DroneSubsystem d : drones) {
                d.closeSocket();
            }
            scheduler.closeSocket();
        }

        printMetrics(scheduler);
    }

    private static void printMetrics(Scheduler scheduler) {
        String line1 = "Average response time: " + scheduler.getAverageResponseTime() + " seconds";
        String line2 = "Maximum response time: " + scheduler.getMaxResponseTime() + " seconds";
        String line3 = "Average completion time: " + scheduler.getAverageCompletionTime() + " seconds";
        String line4 = "Maximum completion time: " + scheduler.getMaxCompletionTime() + " seconds";
        String line5 = scheduler.getDroneUtilization();
        System.out.println("[Main] --- Performance metrics ---");
        System.out.println(line1);
        System.out.println(line2);
        System.out.println(line3);
        System.out.println(line4);
        System.out.print(line5);
        System.err.println("[Main] --- Performance metrics ---");
        System.err.println(line1);
        System.err.println(line2);
        System.err.println(line3);
        System.err.println(line4);
        System.err.print(line5);
        System.out.flush();
        System.err.flush();
    }
}
