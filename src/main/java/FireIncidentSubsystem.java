import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * FireIncidentSubsystem.java
 *
 * Reads the CSV input file and forwards each event to the Scheduler.
 * Then waits for completion acknowledgments.
 */
public class FireIncidentSubsystem implements Runnable {
    private String inputCsvPath;
    private final MessageBuffer toScheduler;
    private final MessageBuffer fromScheduler;
    public final boolean readInputEvent;
    private final DatagramSocket socket;
    private final InetAddress schedulerAddr;

    /**
     * @param inputCsvPath path to the input CSV file
     * @param toScheduler buffer to send messages to the Scheduler
     * @param fromScheduler buffer to receive acknowledgments from Scheduler
     */
    public FireIncidentSubsystem(String inputCsvPath,
                                 MessageBuffer toScheduler,
                                 MessageBuffer fromScheduler) throws SocketException, UnknownHostException {
        this.inputCsvPath = inputCsvPath;
        this.toScheduler = toScheduler;
        this.fromScheduler = fromScheduler;
        this.readInputEvent = hasAtLeastOneValidEvent();
        this.socket = new DatagramSocket(SwarmNetwork.FIS_PORT);
        this.schedulerAddr = InetAddress.getByName(SwarmNetwork.LOCALHOST);
    }

    public void setInputCsvPath(String inputCsvPath) {
        this.inputCsvPath = inputCsvPath;
    }

    public void closeSocket() { socket.close(); }

    @Override
    public void run() {
        List<FireEvent> events = loadEventsFromCsv();
        int eventsSent = 0;
        try {
            eventsSent = sendEventsToScheduler(events);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            notifySchedulerInputComplete();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int eventsCompleted = awaitAcknowledgements();
        System.out.println("[FireIncident] Finished. Completed: " + eventsCompleted + "/" + eventsSent);
    }

    /**
     * Loads and parses FireEvents from the input CSV file.
     */
    List<FireEvent> loadEventsFromCsv() {
        List<FireEvent> events = new ArrayList<>();
        System.out.println("[FireIncident] Reading input: " + inputCsvPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(inputCsvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                FireEvent event = parseEventLine(line);
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("[FireIncident] File error: " + ex.getMessage(), ex);
        }
        return events;
    }

    /**
     * Checks whether the input contains at least one valid event.
     */
    boolean hasAtLeastOneValidEvent() {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputCsvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (parseEventLine(line) != null) {
                    return true;
                }
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
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
    int sendEventsToScheduler(List<FireEvent> events) throws Exception {
        int eventsSent = 0;
        for (FireEvent event : events) {
            // retaining old method for now
            // toScheduler.put(Message.fireEvent(event));
            // NEW: UDP sendMessage
            SwarmNetwork.sendMessage(socket, schedulerAddr, SwarmNetwork.SCHEDULER_PORT, Message.fireEvent(event), "[FireIncident]", "sent Event", "to Scheduler");
            eventsSent++;
            System.out.println("[FireIncident] Sent event: " + event);
        }
        return eventsSent;
    }

    /**
     * Reads one message from the Scheduler.
     */
    Message receiveMessage() throws Exception {
        // return fromScheduler.get();
        return SwarmNetwork.receiveMessage(socket);
    }

    /**
     * Sends the shutdown signal to the Scheduler.
     */
    void notifySchedulerInputComplete() throws Exception {
        // toScheduler.put(Message.shutdown());
        SwarmNetwork.sendMessage(socket, schedulerAddr, SwarmNetwork.SCHEDULER_PORT, Message.shutdown(), "[FireIncident]", "sent Shutdown", "to Scheduler");
    }

    /**
     * Waits for completion acknowledgments or shutdown.
     * @return number of completed events
     */
    public int awaitAcknowledgements() {
        int eventsCompleted = 0;
        System.out.println("[FireIncident] Waiting for acknowledgments...");
        while (true) {
            try {
                Message msg = receiveMessage();
                if (msg.getType() == Message.Type.FIRE_ACK) {
                    eventsCompleted++;
                    System.out.println("[FireIncident] Acknowledged: " + msg.getEvent());
                } else if (msg.getType() == Message.Type.SHUTDOWN) {
                    break;
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return eventsCompleted;
    }
}