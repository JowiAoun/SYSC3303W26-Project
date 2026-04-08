/**
 * Main.java
 *
 * Starts the three subsystem threads and wires their queues together.
 */
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String inputPath = "./src/main/resources/data/events.csv";
        int droneCount = 20;

        boolean headless = false;
        boolean speedFromArgs = false;
        for (String arg : args) {
            if ("--headless".equals(arg)) {
                headless = true;
            } else if (arg.startsWith("--speed=")) {
                SimulationConfig.setTimeFactor(Integer.parseInt(arg.substring("--speed=".length())));
                speedFromArgs = true;
            }
        }

        // Build GUI (on EDT); simulation threads start only after user presses Start (speed locked there).
        FireDroneGUI gui = null;
        if (!headless) {
            FireDroneGUI window = new FireDroneGUI(droneCount);
            gui = window;
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> window.setVisible(true));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
            try {
                window.awaitStart();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        } else {
            System.out.println("[Main] Running in headless mode (no GUI).");
            if (!speedFromArgs) {
                SimulationConfig.setTimeFactor(480);
            }
            SimulationConfig.lockSpeed();
        }

        // Start the simulation clock at 0 now that speed is locked and before any threads run.
        SimulationConfig.resetSimClock();

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

        schedulerThread.start();
        for (Thread dt : droneThreads) {
            dt.start();
        }
        fireThread.start();

        // Wait until every registered drone is IDLE and all fire events are completed,
        // so timing metrics are populated before threads wind down.
        // CSV pacing can span many hours at 1x; default sim speed is fast-forward.
        long deadline = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12);
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

        scheduler.printMetrics();
    }
}
