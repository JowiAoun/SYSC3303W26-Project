/**
 * FireIncidentMain.java
 *
 * entry point for the Fire Incident subsystem.
 * Reads input events and sends them to the Scheduler over UDP.
 */
public class FireIncidentMain {
    public static void main(String[] args) {
        // Read CLI args for the Fire Incident subsystem.
        String inputPath = getArg(args, "input", "./src/main/resources/data/events.csv");
        String schedulerHost = getArg(args, "schedulerHost", SwarmNetwork.LOCALHOST);
        int schedulerPort = Integer.parseInt(getArg(args, "schedulerPort", Integer.toString(SwarmNetwork.SCHEDULER_PORT)));
        int localPort = Integer.parseInt(getArg(args, "localPort", Integer.toString(SwarmNetwork.FIS_PORT)));

        // Build the Fire Incident subsystem and bind its UDP port.
        FireIncidentSubsystem fireIncident;
        try {
            fireIncident = new FireIncidentSubsystem(inputPath, schedulerHost, schedulerPort, localPort);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Start the input/event loop.
        Thread fireThread = new Thread(fireIncident, "FireIncidentSubsystem");
        fireThread.start();
        try {
            fireThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fireIncident.closeSocket();
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
