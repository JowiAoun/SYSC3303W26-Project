import java.nio.charset.StandardCharsets;

/**
 * Message.java
 *
 * Lightweight envelope passed between subsystem threads.
 * This keeps communication structured instead of raw strings.
 */
public class Message {
    /**
     * All supported message types for Iteration 1.
     */
    public enum Type {
        FIRE_EVENT,
        DRONE_READY,
        DRONE_ASSIGNMENT,
        DRONE_COMPLETED,
        DRONE_STATUS_UPDATE,
        DRONE_RETURN_TO_BASE,
        FIRE_ACK,
        SHUTDOWN
    }

    private final Type type;
    private final FireEvent event;
    private final DroneStatus status;

    /**
     * Constructor to force use of factory methods.
     */
    public Message(Type type, FireEvent event, DroneStatus status) {
        this.type = type;
        this.event = event;
        this.status = status;
    }

    /**
     * Wrap a fire event coming from the Fire Incident subsystem.
     */
    public static Message fireEvent(FireEvent event) {
        return new Message(Type.FIRE_EVENT, event, null);
    }

    /**
     * Drone asks the Scheduler for work.
     */
    public static Message droneReady() {
        return new Message(Type.DRONE_READY, null, null);
    }

    /**
     * Scheduler assigns a fire event to the Drone.
     */
    public static Message droneAssignment(FireEvent event) {
        return new Message(Type.DRONE_ASSIGNMENT, event, null);
    }
    
    /**
     * Scheduler commands the Drone to return to base.
     */
    public static Message droneReturnToBase() {
        return new Message(Type.DRONE_RETURN_TO_BASE, null, null);
    }

    /**
     * Drone reports completion back to the Scheduler.
     * Note: Detailed status update is sent separately.
     */
    public static Message droneCompleted(FireEvent event) {
        return new Message(Type.DRONE_COMPLETED, event, null);
    }

    /**
     * Drone sends a status update.
     */
    public static Message droneStatus(DroneStatus status) {
        return new Message(Type.DRONE_STATUS_UPDATE, null, status);
    }

    /**
     * Scheduler acknowledges completion to Fire Incident subsystem.
     */
    public static Message fireAck(FireEvent event) {
        return new Message(Type.FIRE_ACK, event, null);
    }

    /**
     * Global shutdown signal for clean termination.
     */
    public static Message shutdown() {
        return new Message(Type.SHUTDOWN, null, null);
    }

    /**
     * Get the message type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Get the attached FireEvent (if any).
     */
    public FireEvent getEvent() {
        return event;
    }

    /**
     * Get the attached DroneStatus (if any).
     */
    public DroneStatus getStatus() {
        return status;
    }

    /**
     * Convert Message data into bytes.
     */
    public byte[] toBytes() {
        String[] fields = new String[11];
        fields[0] = type.name();

        if (event != null) {
            fields[1] = event.getTime();
            fields[2] = Integer.toString(event.getZoneId());
            fields[3] = event.getEventType().name();
            fields[4] = event.getSeverity().name();
        } else {
            fields[1] = "";
            fields[2] = "";
            fields[3] = "";
            fields[4] = "";
        }

        if (status != null) {
            fields[5] = Integer.toString(status.getDroneId());
            fields[6] = status.getState().name();
            fields[7] = Integer.toString(status.getZoneId());
            fields[8] = Integer.toString(status.getRemainingLiters());
            fields[9] = Integer.toString(status.getCurrentCol());
            fields[10] = Integer.toString(status.getCurrentRow());
        } else {
            fields[5] = "";
            fields[6] = "";
            fields[7] = "";
            fields[8] = "";
            fields[9] = "";
            fields[10] = "";
        }

        String wire = String.join("|", fields);
        return wire.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode bytes into Message data.
     */
    public static Message fromBytes(byte[] data, int length) {
        String wire = new String(data, 0, length, StandardCharsets.UTF_8);
        String[] parts = wire.split("\\|", -1); // keep empty fields

        if (parts.length != 11) {
            throw new IllegalArgumentException("Invalid message format: " + wire);
        }

        Type type = Type.valueOf(parts[0]);

        FireEvent event = null;
        if (!parts[1].isEmpty()) {
            String time = parts[1];
            int zoneId = Integer.parseInt(parts[2]);
            FireEvent.EventType eventType = FireEvent.EventType.valueOf(parts[3]);
            FireEvent.Severity severity = FireEvent.Severity.valueOf(parts[4]);
            event = new FireEvent(time, zoneId, eventType, severity);
        }

        DroneStatus status = null;
        if (!parts[5].isEmpty()) {
            int droneId = Integer.parseInt(parts[5]);
            DroneState droneState = DroneState.valueOf(parts[6]);
            int zoneId = Integer.parseInt(parts[7]);
            int remainingLiters = Integer.parseInt(parts[8]);
            int col = parts[9].isEmpty() ? 0 : Integer.parseInt(parts[9]);
            int row = parts[10].isEmpty() ? 0 : Integer.parseInt(parts[10]);
            status = new DroneStatus(droneId, droneState, zoneId, remainingLiters, col, row);
        }

        return new Message(type, event, status);
    }

}
