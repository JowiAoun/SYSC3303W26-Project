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

        // Build the GUI for the specified number of drones.
        FireDroneGUI gui = new FireDroneGUI(droneCount);
        javax.swing.SwingUtilities.invokeLater(() -> gui.setVisible(true));

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
