import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

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
        FAULT_INJECTION,
        FIRE_READY,
        SIM_START,
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
     * Scheduler commands the Drone to return to base with fault info attached.
     * The drone reads the fault type and delay from the event.
     */
    public static Message droneReturnToBase(FireEvent faultInfo) {
        return new Message(Type.DRONE_RETURN_TO_BASE, faultInfo, null);
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
    /**
     * GUI injects a fault on a specific drone via UDP.
     * The faultInfo carries: droneId (in zoneId), faultType, duration (in faultDelayTime).
     */
    public static Message faultInjection(FireEvent faultInfo) {
        return new Message(Type.FAULT_INJECTION, faultInfo, null);
    }

    public static Message fireAck(FireEvent event) {
        return new Message(Type.FIRE_ACK, event, null);
    }

    /**
     * Fire Incident signals it is ready and waiting for the simulation to start.
     */
    public static Message fireReady() {
        return new Message(Type.FIRE_READY, null, null);
    }

    /**
     * Scheduler signals that the simulation has started, carrying the speed factor.
     * The speed factor is piggy-backed in the FireEvent's zoneId field to avoid adding
     * a dedicated message field for a one-time handshake value.
     */
    public static Message simStart(int speedFactor) {
        FireEvent payload = new FireEvent("0", speedFactor, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW, FaultType.NONE, 0);
        return new Message(Type.SIM_START, payload, null);
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
    /**
     * Wire format: 15 pipe-delimited fields.
     * [0] type | [1-6] FireEvent fields | [7-13] DroneStatus fields | [14] CRC32 checksum.
     * Empty strings fill unused slots. The checksum is computed over fields [0-13] with [14] blank,
     * then placed into [14] for the final serialized string.
     */
    public byte[] toBytes() {
        String[] fields = new String[15];
        fields[0] = type.name();

        if (event != null) {
            fields[1] = event.getTime();
            fields[2] = Integer.toString(event.getZoneId());
            fields[3] = event.getEventType().name();
            fields[4] = event.getSeverity().name();
            fields[5] = event.getFaultType().name();
            fields[6] = Long.toString(event.getFaultDelayTime());
        } else {
            fields[1] = "";
            fields[2] = "";
            fields[3] = "";
            fields[4] = "";
            fields[5] = "";
            fields[6] = "";
        }

        if (status != null) {
            fields[7] = Integer.toString(status.getDroneId());
            fields[8] = status.getState().name();
            fields[9] = Integer.toString(status.getZoneId());
            fields[10] = Integer.toString(status.getRemainingLiters());
            fields[11] = Integer.toString(status.getCurrentCol());
            fields[12] = Integer.toString(status.getCurrentRow());
            fields[13] = status.getFaultType().name();
        } else {
            fields[7] = "";
            fields[8] = "";
            fields[9] = "";
            fields[10] = "";
            fields[11] = "";
            fields[12] = "";
            fields[13] = "";
        }

        fields[14] = "";

        String withoutChecksum = String.join("|", fields);
        long checksum = getCRC32Checksum(withoutChecksum);
        fields[14] = Long.toString(checksum);

        String wire = String.join("|", fields);
        return wire.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode bytes into Message data.
     */
    /**
     * Deserialize a wire-format byte array back into a Message.
     * split("\\|", -1) is used so trailing empty fields are preserved (default split drops them).
     * The checksum in field [14] is extracted, then field [14] is blanked before recomputing
     * the CRC32 over the same string the sender used — any mismatch indicates corruption.
     */
    public static Message fromBytes(byte[] data, int length) {
        String wire = new String(data, 0, length, StandardCharsets.UTF_8);
        String[] parts = wire.split("\\|", -1);

        if (parts.length != 15) {
            throw new IllegalArgumentException("Invalid message format: " + wire);
        }

        String receivedChecksum = parts[14];
        parts[14] = "";

        String withoutChecksum = String.join("|", parts);
        long expectedChecksum = getCRC32Checksum(withoutChecksum);

        if (receivedChecksum.isEmpty()) {
            throw new IllegalArgumentException("No Checksum");
        }

        long receivedValue = Long.parseLong(receivedChecksum);
        if (receivedValue != expectedChecksum) {
            throw new IllegalArgumentException("Checksum doesn't match");
        }

        Type type = Type.valueOf(parts[0]);

        FireEvent event = null;
        if (!parts[1].isEmpty()) {
            String time = parts[1];
            int zoneId = Integer.parseInt(parts[2]);
            FireEvent.EventType eventType = FireEvent.EventType.valueOf(parts[3]);
            FireEvent.Severity severity = FireEvent.Severity.valueOf(parts[4]);
            FaultType faultType = parts[5].isEmpty() ? FaultType.NONE : FaultType.valueOf(parts[5]);
            long faultDelayTime = parts[6].isEmpty() ? 0 : Long.parseLong(parts[6]);

            event = new FireEvent(time, zoneId, eventType, severity, faultType, faultDelayTime);
        }

        DroneStatus status = null;
        if (!parts[7].isEmpty()) {
            int droneId = Integer.parseInt(parts[7]);
            DroneState droneState = DroneState.valueOf(parts[8]);
            int zoneId = Integer.parseInt(parts[9]);
            int remainingLiters = Integer.parseInt(parts[10]);
            int col = parts[11].isEmpty() ? 0 : Integer.parseInt(parts[11]);
            int row = parts[12].isEmpty() ? 0 : Integer.parseInt(parts[12]);
            FaultType faultType = parts[13].isEmpty() ? FaultType.NONE : FaultType.valueOf(parts[13]);
            status = new DroneStatus(droneId, droneState, zoneId, remainingLiters, col, row, faultType);
        }

        return new Message(type, event, status);
    }

    public static long getCRC32Checksum(String value) {
        Checksum crc32  = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}