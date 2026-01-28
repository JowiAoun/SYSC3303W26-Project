import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * FireIncidentSubsystem.java
 *
 * Reads the CSV input file and forwards each event to the Scheduler.
 * Then waits for completion acknowledgments.
 */
public class FireIncidentSubsystem implements Runnable {
    private final String inputCsvPath;
    private final MessageBuffer toScheduler;
    private final MessageBuffer fromScheduler;

    /**
     * @param inputCsvPath path to the input CSV file
     * @param toScheduler buffer to send messages to the Scheduler
     * @param fromScheduler buffer to receive acknowledgments from Scheduler
     */
    public FireIncidentSubsystem(String inputCsvPath,
                                 MessageBuffer toScheduler,
                                 MessageBuffer fromScheduler) {
        this.inputCsvPath = inputCsvPath;
        this.toScheduler = toScheduler;
        this.fromScheduler = fromScheduler;
    }

    /**
     * Main loop:
     * 1) Read and parse CSV lines into FireEvent objects.
     * 2) Send events to Scheduler.
     * 3) Wait for acknowledgments before exiting.
     */
    @Override
    public void run() {
        int eventsSent = 0;
        int eventsCompleted = 0;
        System.out.println("[FireIncident] Reading input: " + inputCsvPath);

        try (BufferedReader reader = new BufferedReader(new FileReader(inputCsvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.toLowerCase().startsWith("time")) {
                    continue; // header
                }

                String[] parts = trimmed.split(",");
                if (parts.length < 4) {
                    System.out.println("[FireIncident] Skipping invalid line: " + trimmed);
                    continue;
                }

                String time = parts[0].trim();
                int zoneId = Integer.parseInt(parts[1].trim());
                FireEvent.EventType eventType = FireEvent.parseEventType(parts[2]);
                FireEvent.Severity severity = FireEvent.parseSeverity(parts[3]);

                FireEvent event = new FireEvent(time, zoneId, eventType, severity);
                toScheduler.put(Message.fireEvent(event));
                eventsSent++;
                System.out.println("[FireIncident] Sent event: " + event);
            }
            toScheduler.put(Message.shutdown());
        } catch (IOException ex) {
            throw new RuntimeException("[FireIncident] File error: " + ex.getMessage(), ex);
        }

        System.out.println("[FireIncident] Waiting for acknowledgments...");
        while (true) {
            try {
                Message msg = fromScheduler.get();
                if (msg.getType() == Message.Type.FIRE_ACK) {
                    eventsCompleted++;
                    System.out.println("[FireIncident] Acknowledged: " + msg.getEvent());
                } else if (msg.getType() == Message.Type.SHUTDOWN) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[FireIncident] Finished. Completed: " + eventsCompleted + "/" + eventsSent);
    }
}
