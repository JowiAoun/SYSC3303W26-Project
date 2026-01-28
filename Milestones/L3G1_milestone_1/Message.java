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
        FIRE_ACK,
        SHUTDOWN
    }

    private final Type type;
    private final FireEvent event;

    /**
     * Private constructor to force use of factory methods.
     */
    private Message(Type type, FireEvent event) {
        this.type = type;
        this.event = event;
    }

    /**
     * Wrap a fire event coming from the Fire Incident subsystem.
     */
    public static Message fireEvent(FireEvent event) {
        return new Message(Type.FIRE_EVENT, event);
    }

    /**
     * Drone asks the Scheduler for work.
     */
    public static Message droneReady() {
        return new Message(Type.DRONE_READY, null);
    }

    /**
     * Scheduler assigns a fire event to the Drone.
     */
    public static Message droneAssignment(FireEvent event) {
        return new Message(Type.DRONE_ASSIGNMENT, event);
    }

    /**
     * Drone reports completion back to the Scheduler.
     */
    public static Message droneCompleted(FireEvent event) {
        return new Message(Type.DRONE_COMPLETED, event);
    }

    /**
     * Scheduler acknowledges completion to Fire Incident subsystem.
     */
    public static Message fireAck(FireEvent event) {
        return new Message(Type.FIRE_ACK, event);
    }

    /**
     * Global shutdown signal for clean termination.
     */
    public static Message shutdown() {
        return new Message(Type.SHUTDOWN, null);
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

}
