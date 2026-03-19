/**
 * FireEvent.java
 *
 * Immutable data object for one fire incident entry from the CSV file.
 */
public class FireEvent {
    /**
     * Event type found in the input file.
     */
    public enum EventType {
        FIRE_DETECTED,
        DRONE_REQUEST
    }

    /**
     * Severity levels used to determine required water/foam.
     */
    public enum Severity {
        LOW,
        MODERATE,
        HIGH
    }

    private final String time;
    private final int zoneId;
    private final EventType eventType;
    private final Severity severity;
    private final FaultType faultType;
    private final long faultTime;


    /**
     * Construct a new FireEvent with all required fields.
     */
    public FireEvent(String time, int zoneId, EventType eventType, Severity severity,
                     FaultType faultType, long faultTime) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
        this.faultType = faultType;
        this.faultTime = faultTime;
    }

    /**
     * TODO: Temporary, to not break testing
     */
    public FireEvent(String time, int zoneId, EventType eventType, Severity severity) {
        this.time = time;
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.severity = severity;
        this.faultType = FaultType.NONE;
        this.faultTime = 0;
    }

    /**
     * Time stamp for this event.
     */
    public String getTime() {
        return time;
    }

    /**
     * Fire zone identifier.
     */
    public int getZoneId() {
        return zoneId;
    }

    /**
     * FIRE_DETECTED or DRONE_REQUEST.
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * LOW, MODERATE, or HIGH.
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Get Fault Type
     */
    public FaultType getFaultType() { return faultType; }

    /**
     * Time until fault triggers
     */
    public long getFaultTime() { return faultTime; }

    /**
     * Does event have fault
     */
    public boolean hasFault() {
        return faultType != FaultType.NONE;
    }

    /**
     * Map severity to required liters of agent.
     */
    public int getRequiredLiters() {
        switch (severity) {
            case LOW:
                return 10;
            case MODERATE:
                return 20;
            case HIGH:
                return 30;
            default:
                return 0;
        }
    }

    /**
     * Parse event type token from CSV.
     */
    public static EventType parseEventType(String token) {
        return EventType.valueOf(token.trim().toUpperCase());
    }

    /**
     * Parse severity token from CSV.
     */
    public static Severity parseSeverity(String token) {
        return Severity.valueOf(token.trim().toUpperCase());
    }

    /**
     * Parse fault type from CSV.
     */
    public static FaultType parseFaultType(String token) { return FaultType.valueOf(token.trim().toUpperCase()); }

    /**
     * Single-line printable summary for logs.
     */
    @Override
    public String toString() {
        return "FireEvent{" +
               "time=" + time +
               ", zoneId=" + zoneId +
               ", eventType=" + eventType +
               ", severity=" + severity +
               ", faultType=" + faultType +
               ", faultTime=" + faultTime +
               ", requiredLiters=" + getRequiredLiters() +
               '}';
    }
}
