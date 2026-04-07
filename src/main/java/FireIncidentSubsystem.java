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
    public final boolean readInputEvent;
    private final DatagramSocket socket;
    private final InetAddress schedulerAddr;
    // Scheduler target port for UDP messages.
    private final int schedulerPort;

    /**
     * @param inputCsvPath path to the input CSV file
     */
    public FireIncidentSubsystem(String inputCsvPath) throws SocketException, UnknownHostException {
        this(inputCsvPath, SwarmNetwork.LOCALHOST,
                SwarmNetwork.SCHEDULER_PORT, SwarmNetwork.FIS_PORT);
    }

    /**
     * @param schedulerHost hostname/IP for Scheduler
     * @param schedulerPort Scheduler UDP port
     * @param localPort UDP port to bind locally
     */
    public FireIncidentSubsystem(String inputCsvPath,
                                 String schedulerHost,
                                 int schedulerPort,
                                 int localPort) throws SocketException, UnknownHostException {
        this.inputCsvPath = inputCsvPath;
        this.readInputEvent = hasAtLeastOneValidEvent();
        this.socket = new DatagramSocket(localPort);
        this.schedulerAddr = InetAddress.getByName(schedulerHost);
        this.schedulerPort = schedulerPort;
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
        if (!trimmedLine.isEmpty() && trimmedLine.charAt(0) == '\uFEFF') {
            trimmedLine = trimmedLine.substring(1).trim();
        }
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
        FireEvent.EventType eventType = FireEvent.parseEventType(parts[2].trim());
        FireEvent.Severity severity = FireEvent.parseSeverity(parts[3].trim());

        FaultType faultType = FaultType.NONE;
        long faultDelayTime = 0;

        if (parts.length >= 5) {
            faultType = FireEvent.parseFaultType(parts[4].trim());
        }
        if (parts.length >= 6 && !parts[5].trim().isEmpty()) {
            faultDelayTime = Long.parseLong(parts[5].trim());
        }

        return new FireEvent(time, zoneId, eventType, severity, faultType, faultDelayTime);
    }

    /**
     * Parses CSV time fields such as {@code HH:MM:SS} or {@code HH:MM:SS.mmm} into milliseconds since midnight.
     * @return ms since midnight, or -1 if unparseable
     */
    static long parseCsvTimeToMillisSinceMidnight(String raw) {
        if (raw == null) {
            return -1;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return -1;
        }
        int dot = t.indexOf('.');
        String fraction = "";
        if (dot >= 0) {
            fraction = t.substring(dot + 1).trim();
            t = t.substring(0, dot).trim();
        }
        String[] parts = t.split(":");
        if (parts.length != 3) {
            return -1;
        }
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            int s = Integer.parseInt(parts[2].trim());
            long ms = (h * 3600L + m * 60L + s) * 1000L;
            if (!fraction.isEmpty()) {
                String f = fraction;
                while (f.length() < 3) {
                    f += "0";
                }
                if (f.length() > 3) {
                    f = f.substring(0, 3);
                }
                ms += Long.parseLong(f);
            }
            return ms;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Sends events to the Scheduler.
     * Waits between events according to the CSV Time column when {@link SimulationConfig#isCsvTimePacingEnabled()}
     * is true; delay is divided by the simulation speed factor.
     * @return number of events sent
     */
    int sendEventsToScheduler(List<FireEvent> events) throws Exception {
        int eventsSent = 0;
        long prevMs = -1;
        for (FireEvent event : events) {
            long tMs = parseCsvTimeToMillisSinceMidnight(event.getTime());
            if (SimulationConfig.isCsvTimePacingEnabled() && prevMs >= 0 && tMs >= 0) {
                long delta = tMs - prevMs;
                if (delta < 0) {
                    delta += 24L * 3600_000;
                }
                if (delta > 0) {
                    try {
                        // Advance simulation time by the CSV delta; sleep scaled wall time for fast-forward.
                        SimulationConfig.sleepSimulated(delta);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
            SwarmNetwork.sendMessage(socket, schedulerAddr, schedulerPort, Message.fireEvent(event), "[FireIncident]", "sent Event", "to Scheduler");
            eventsSent++;
            System.out.println("[FireIncident] Sent event: " + event);
            if (tMs >= 0) {
                prevMs = tMs;
            }
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
        SwarmNetwork.sendMessage(socket, schedulerAddr, schedulerPort, Message.shutdown(), "[FireIncident]", "sent Shutdown", "to Scheduler");
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