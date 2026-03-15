/**
 * DroneMain.java
 *
 * entry point for one Drone subsystem instance.
 * Starts a single drone process with CLI-configured Scheduler settings.
 */
public class DroneMain {
    public static void main(String[] args) {
        // Read CLI args for this drone instance.
        int droneId = Integer.parseInt(getArg(args, "id", "1"));
        String schedulerHost = getArg(args, "schedulerHost", SwarmNetwork.LOCALHOST);
        int schedulerPort = Integer.parseInt(getArg(args, "schedulerPort", Integer.toString(SwarmNetwork.SCHEDULER_PORT)));

        // Build the drone subsystem with its ID and Scheduler address.
        DroneSubsystem drone;
        try {
            drone = new DroneSubsystem(droneId, schedulerHost, schedulerPort);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Start the drone state machine loop.
        Thread droneThread = new Thread(drone, "DroneSubsystem-" + droneId);
        droneThread.start();
        try {
            droneThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            drone.closeSocket();
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
