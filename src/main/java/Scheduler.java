import java.util.ArrayDeque;
import java.util.Queue;
/**
 * Scheduler.java
 *
 * Pass-through coordinator for Iteration 1.
 * It buffers incoming fire events and assigns them to the drone on request.
 */
public class Scheduler implements Runnable {
    private final MessageBuffer fromSubsystems;
    private final MessageBuffer toFireIncident;
    private final MessageBuffer toDrone;

    private final Queue<FireEvent> pending = new ArrayDeque<>();
    private int totalEvents = 0;
    private int completed = 0;
    private boolean fireIncidentDone = false;
    private boolean shutdownSentToFire = false;
    private boolean shutdownSentToDrone = false;

    // Track the single drone's status.
    // Initially null until first ready/status message received.
    private DroneStatus lastDroneStatus = null;
    
    private final FireDroneGUI gui;

    /**
     * @param fromSubsystems shared buffer of incoming messages
     * @param toFireIncident buffer to send acknowledgments back
     * @param toDrone buffer to send assignments to the drone
     * @param gui reference to the main GUI window
     */
    public Scheduler(MessageBuffer fromSubsystems,
                     MessageBuffer toFireIncident,
                     MessageBuffer toDrone,
                     FireDroneGUI gui) {
        this.fromSubsystems = fromSubsystems;
        this.toFireIncident = toFireIncident;
        this.toDrone = toDrone;
        this.gui = gui;
    }

    public int getTotalEvents() { return totalEvents; }

    @Override
    public void run() {
        System.out.println("[Scheduler] Started.");

        try {
            while (true) {
                Message msg = receiveSubsystemMessage();
                handleIncomingMessage(msg);
                
                // Try to dispatch whenever state changes or new events arrive
                if (canDispatchPendingEvent()) {
                    dispatchPendingEvent();
                }
                
                sendShutdownToDroneIfComplete();
                sendShutdownToFireIfComplete();
                if (shouldTerminate()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Scheduler] Interrupted.", e);
        }

        System.out.println("[Scheduler] Finished.");
    }

    /**
     * Reads one message from the shared buffer.
     */
    Message receiveSubsystemMessage() throws InterruptedException {
        return fromSubsystems.get();
    }

    /**
     * Handles a message from either subsystem.
     */
    void handleIncomingMessage(Message msg) throws InterruptedException {
        switch (msg.getType()) {
            case FIRE_EVENT:
            case SHUTDOWN:
                handleFireIncidentMessage(msg);
                break;
            case DRONE_READY:
            case DRONE_COMPLETED:
            case DRONE_STATUS_UPDATE:
                handleDroneMessage(msg);
                break;
            default:
                System.out.println("[Scheduler] Unknown message type: " + msg.getType());
        }
    }

    /**
     * Checks if the scheduler can dispatch a pending event.
     */
    boolean canDispatchPendingEvent() {
        boolean droneAvailable = lastDroneStatus != null && lastDroneStatus.getState() == DroneState.IDLE;
        return droneAvailable && !pending.isEmpty();
    }

    /**
     * Dispatches the next pending event to the drone.
     */
    void dispatchPendingEvent() throws InterruptedException {
        FireEvent next = pending.poll();
        toDrone.put(Message.droneAssignment(next));
        
        // Optimistically update status to prevent double dispatch
        // The drone will confirm with EN_ROUTE shortly
        lastDroneStatus = new DroneStatus(
                lastDroneStatus != null ? lastDroneStatus.getDroneId() : 1,
                DroneState.EN_ROUTE, 
                next.getZoneId(), 
                lastDroneStatus != null ? lastDroneStatus.getRemainingLiters() : 15
        );
        
        System.out.println("[Scheduler] Dispatched to drone: " + next);
    }

    /**
     * Sends shutdown to Drone subsystem if conditions are met.
     */
    void sendShutdownToDroneIfComplete() throws InterruptedException {
        if (isProcessingComplete() && !shutdownSentToDrone) {
            toDrone.put(Message.shutdown());
            shutdownSentToDrone = true;
            System.out.println("[Scheduler] Sent shutdown to Drone.");
        }
    }

    /**
     * Sends shutdown to Fire Incident subsystem if conditions are met.
     */
    void sendShutdownToFireIfComplete() throws InterruptedException {
        if (isProcessingComplete() && !shutdownSentToFire) {
            toFireIncident.put(Message.shutdown());
            shutdownSentToFire = true;
            System.out.println("[Scheduler] Sent shutdown to Fire Incident.");
        }
    }

    /**
     * Checks whether all events are complete and queues are empty.
     */
    boolean isProcessingComplete() {
        // We are done if:
        // 1. Fire Incident has stopped sending events.
        // 2. No pending events in queue.
        // 3. All events have been acknowledged as completed.
        // 4. Drone is IDLE (not working on a task).
        boolean droneIdle = lastDroneStatus != null && lastDroneStatus.getState() == DroneState.IDLE;
        return fireIncidentDone && pending.isEmpty() && completed == totalEvents && droneIdle;
    }

    /**
     * Checks whether the scheduler should terminate.
     */
    boolean shouldTerminate() {
        return isProcessingComplete() && shutdownSentToDrone && shutdownSentToFire;
    }

    /**
     * Handle messages from Fire Incident subsystem.
     */
    private void handleFireIncidentMessage(Message message) throws InterruptedException {
        if (message.getType() == Message.Type.FIRE_EVENT) {
            FireEvent event = message.getEvent();
            pending.add(event);
            totalEvents++;
            System.out.println("[Scheduler] Received event: " + event);
            
            // Update GUI: New Fire
            if (gui != null) {
                gui.setZoneFire(event.getZoneId(), true);
            }
            
        } else if (message.getType() == Message.Type.SHUTDOWN) {
            fireIncidentDone = true;
            System.out.println("[Scheduler] Fire Incident input complete.");
        }
    }

    /**
     * Handle messages from the Drone subsystem.
     */
    private void handleDroneMessage(Message message) throws InterruptedException {
        switch (message.getType()) {
            case DRONE_READY:
                System.out.println("[Scheduler] Received Drone Ready Signal");
                break;
                
            case DRONE_STATUS_UPDATE:
                DroneStatus status = message.getStatus();
                System.out.println("[Scheduler] Drone Status Update: " + status);
                this.lastDroneStatus = status;
                
                if (gui != null) {
                    gui.updateDroneStatus(status);
                }
                break;
                
            case DRONE_COMPLETED:
                completed++;
                toFireIncident.put(Message.fireAck(message.getEvent()));
                System.out.println("[Scheduler] Completion ack forwarded: " + message.getEvent());
                
                // Update GUI: Fire Extinguished
                if (gui != null) {
                    gui.setZoneFire(message.getEvent().getZoneId(), false);
                }
                break;
            default:
                // Ignore other messages like SHUTDOWN if they come here by mistake,
                // or legitimate ignored cases.
                break;
        }
    }
}