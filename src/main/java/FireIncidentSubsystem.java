import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void run() {
        // Read all raw input lines from the CSV file.
        List<String> inputLines = readInputLines();
        // Parse validated lines into FireEvent objects.
        List<FireEvent> events = parseEvents(inputLines);
        // Send parsed events to the Scheduler.
        int eventsSent = sendEvents(events);
        // Signal that no more input events are coming.
        sendShutdownSignal();
        // Wait for completion acknowledgments before finishing.
        int eventsCompleted = waitForAcknowledgments();
        System.out.println("[FireIncident] Finished. Completed: " + eventsCompleted + "/" + eventsSent);
    }

    /**
     * Reads all lines from the input CSV file.
     */
    List<String> readInputLines() {
        List<String> lines = new ArrayList<>();
        System.out.println("[FireIncident] Reading input: " + inputCsvPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(inputCsvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ex) {
            throw new RuntimeException("[FireIncident] File error: " + ex.getMessage(), ex);
        }
        return lines;
    }

    /**
     * Parses the raw input lines into FireEvent objects.
     */
    List<FireEvent> parseEvents(List<String> lines) {
        List<FireEvent> events = new ArrayList<>();
        for (String line : lines) {
            FireEvent event = parseEventLine(line);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * Parses one CSV line into a FireEvent.
     */
    FireEvent parseEventLine(String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return null;
        }
        if (trimmedLine.toLowerCase().startsWith("time")) {
            return null; // header
        }
        String[] parts = trimmedLine.split(",");
        if (parts.length < 4) {
            System.out.println("[FireIncident] Skipping invalid line: " + trimmedLine);
            return null;
        }
        String time = parts[0].trim();
        int zoneId = Integer.parseInt(parts[1].trim());
        FireEvent.EventType eventType = FireEvent.parseEventType(parts[2]);
        FireEvent.Severity severity = FireEvent.parseSeverity(parts[3]);
        return new FireEvent(time, zoneId, eventType, severity);
    }

    /**
     * Sends events to the Scheduler.
     * @return number of events sent
     */
    int sendEvents(List<FireEvent> events) {
        int eventsSent = 0;
        for (FireEvent event : events) {
            toScheduler.put(Message.fireEvent(event));
            eventsSent++;
            System.out.println("[FireIncident] Sent event: " + event);
        }
        return eventsSent;
    }

    /**
     * Sends the shutdown signal to the Scheduler.
     */
    void sendShutdownSignal() {
        toScheduler.put(Message.shutdown());
    }

    /**
     * Waits for completion acknowledgments or shutdown.
     * @return number of completed events
     */
    int waitForAcknowledgments() {
        int eventsCompleted = 0;
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
        return eventsCompleted;
    }
}