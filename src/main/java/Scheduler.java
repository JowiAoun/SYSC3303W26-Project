import java.util.ArrayDeque;
import java.util.Queue;
/**
 * Scheduler.java
 *
 * State-machine-driven coordinator.
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

    // State machine
    private SchedulerState currentState = SchedulerState.IDLE;

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

    SchedulerState getCurrentState() { return currentState; }

    @Override
    public void run() {
        System.out.println("[Scheduler] Started.");

        try {
            while (currentState != SchedulerState.SHUTTING_DOWN) {
                Message msg = receiveSubsystemMessage();
                handleIncomingMessage(msg);

                // Try to dispatch whenever state changes or new events arrive
                if (canDispatchPendingEvent()) {
                    dispatchPendingEvent();
                } else {
                    checkAndSendReturnToBase();
                }

                sendShutdownToDroneIfComplete();
                sendShutdownToFireIfComplete();
                evaluateTransition();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Scheduler] Interrupted.", e);
        }

        System.out.println("[Scheduler] Finished.");
    }

    // ── State machine ───────────────────────────────────────────────

    /**
     * Centralized transition logic. Called after every message is processed.
     * Determines next state based on: drone status, pending queue, completion flags.
     */
    void evaluateTransition() {
        if (shouldTerminate()) {
            currentState = SchedulerState.SHUTTING_DOWN;
            System.out.println("[Scheduler] State -> SHUTTING_DOWN");
            return;
        }

        boolean droneIdle = lastDroneStatus != null && lastDroneStatus.getState() == DroneState.IDLE;
        boolean hasPending = !pending.isEmpty();

        if (hasPending && !droneIdle) {
            currentState = SchedulerState.AWAITING_DRONE;
        } else if (!droneIdle) {
            currentState = SchedulerState.DRONE_ACTIVE;
        } else {
            currentState = SchedulerState.IDLE;
        }
    }

    // ── Message processing ──────────────────────────────────────────

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
        // Check if the drone has enough agent for the next event
        FireEvent next = pending.peek();
        if (next == null) return;

        // If drone is at a remote zone (not base), check if it has enough agent
        if (lastDroneStatus.getZoneId() != 0) {
            if (lastDroneStatus.getRemainingLiters() < next.getRequiredLiters()) {
                 // Not enough agent for this task.
                 // We MUST return to base first.
                 // Note: Ideally we'd look for a smaller task, but for now just return.
                 sendReturnToBase();
                 return;
            }
        }

        // If we get here, we can dispatch
        next = pending.poll(); // remove from queue
        toDrone.put(Message.droneAssignment(next));

        // Optimistically update status to prevent double dispatch
        // The drone will confirm with EN_ROUTE shortly
        lastDroneStatus = new DroneStatus(
                lastDroneStatus != null ? lastDroneStatus.getDroneId() : 1,
                DroneState.EN_ROUTE,
                next.getZoneId(),
                lastDroneStatus != null ? lastDroneStatus.getRemainingLiters() : 15
        );

        String dispatchMsg = "[Scheduler] Dispatched to drone: " + next;
        System.out.println(dispatchMsg);
        if (gui != null) {
            gui.appendEvent(dispatchMsg);
        }
    }

    /**
     * Sends a command to the drone to return to base.
     */
    void sendReturnToBase() throws InterruptedException {
        toDrone.put(Message.droneReturnToBase());

        // Optimistically update status
        lastDroneStatus = new DroneStatus(
                lastDroneStatus != null ? lastDroneStatus.getDroneId() : 1,
                DroneState.RETURNING,
                0,
                lastDroneStatus != null ? lastDroneStatus.getRemainingLiters() : 0
        );

        String rtbMsg = "[Scheduler] Commanding drone to Return to Base.";
        System.out.println(rtbMsg);
        if (gui != null) {
            gui.appendEvent(rtbMsg);
        }
    }

    /**
     * Checks if the drone should return to base (Idle at remote zone + no work or no agent).
     */
    void checkAndSendReturnToBase() throws InterruptedException {
         if (lastDroneStatus != null &&
             lastDroneStatus.getState() == DroneState.IDLE &&
             lastDroneStatus.getZoneId() != 0) {

             // Drone is idle at a remote zone.
             // If no pending events, return to base.
             if (pending.isEmpty()) {
                 sendReturnToBase();
             }
         }
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
            String msg = "[Scheduler] Received event: " + event;
            System.out.println(msg);

            // Update GUI: New Fire with severity
            if (gui != null) {
                gui.setZoneFire(event.getZoneId(), true, event.getSeverity());
                gui.appendEvent(msg);
            }

        } else if (message.getType() == Message.Type.SHUTDOWN) {
            fireIncidentDone = true;
            String inputCompleteMsg = "[Scheduler] Fire Incident input complete.";
            System.out.println(inputCompleteMsg);
            if (gui != null) {
                gui.appendEvent(inputCompleteMsg);
            }
        }
    }

    /**
     * Handle messages from the Drone subsystem.
     */
    private void handleDroneMessage(Message message) throws InterruptedException {
        switch (message.getType()) {
            case DRONE_READY:
                String readyMsg = "[Scheduler] Received Drone Ready Signal";
                System.out.println(readyMsg);
                if (gui != null) {
                    gui.appendEvent(readyMsg);
                }
                break;

            case DRONE_STATUS_UPDATE:
                DroneStatus status = message.getStatus();
                String statusMsg = "[Scheduler] Drone Status Update: " + status;
                System.out.println(statusMsg);
                this.lastDroneStatus = status;

                if (gui != null) {
                    gui.updateDroneStatus(status);
                    gui.appendEvent(statusMsg);
                }
                break;

            case DRONE_COMPLETED:
                completed++;
                toFireIncident.put(Message.fireAck(message.getEvent()));
                String completedMsg = "[Scheduler] Completion ack forwarded: " + message.getEvent();
                System.out.println(completedMsg);

                // Update GUI: Fire Extinguished
                if (gui != null) {
                    gui.setZoneFire(message.getEvent().getZoneId(), false);
                    gui.appendEvent(completedMsg);
                }
                break;
            default:
                // Ignore other messages like SHUTDOWN if they come here by mistake,
                // or legitimate ignored cases.
                break;
        }
    }
}
