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
     * Private constructor to force use of factory methods.
     */
    private Message(Type type, FireEvent event, DroneStatus status) {
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

}
