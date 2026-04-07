/**
 * SchedulerMain.java
 *
 * entry point for the Scheduler subsystem.
 * Starts the Scheduler and GUI using CLI-provided settings.
 */
public class SchedulerMain {
    public static void main(String[] args) {
        // Read CLI args for Scheduler and the GUI.
        int port = Integer.parseInt(getArg(args, "port", Integer.toString(SwarmNetwork.SCHEDULER_PORT)));
        String zonesPath = getArg(args, "zones", "./src/main/resources/data/zones.csv");
        int droneCount = Integer.parseInt(getArg(args, "drones", "20"));
        boolean headless = hasFlag(args, "headless");

        // Build the GUI for the specified number of drones.
        FireDroneGUI gui = null;
        if (!headless) {
            gui = new FireDroneGUI(droneCount);
            FireDroneGUI finalGui = gui;
            javax.swing.SwingUtilities.invokeLater(() -> finalGui.setVisible(true));
        } else {
            System.out.println("[SchedulerMain] Running in headless mode (no GUI).");
        }

        // Build the Scheduler and bind its UDP port.
        Scheduler scheduler;
        try {
            scheduler = new Scheduler(gui, port, zonesPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Start the scheduler loop.
        Thread schedulerThread = new Thread(scheduler, "Scheduler");
        schedulerThread.start();
        try {
            schedulerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            scheduler.closeSocket();
        }
    }

    private static boolean hasFlag(String[] args, String key) {
        String flag = "--" + key;
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String getArg(String[] args, String key, String defaultValue) {
        // Simple "--key=value" argument helper.
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }
}
