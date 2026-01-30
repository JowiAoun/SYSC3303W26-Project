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
    private boolean droneReady = false;
    private boolean shutdownSentToFire = false;
    private boolean shutdownSentToDrone = false;

    /**
     * @param fromSubsystems shared buffer of incoming messages
     * @param toFireIncident buffer to send acknowledgments back
     * @param toDrone buffer to send assignments to the drone
     */
    public Scheduler(MessageBuffer fromSubsystems,
                     MessageBuffer toFireIncident,
                     MessageBuffer toDrone) {
        this.fromSubsystems = fromSubsystems;
        this.toFireIncident = toFireIncident;
        this.toDrone = toDrone;
    }

    @Override
    public void run() {
        System.out.println("[Scheduler] Started.");

        try {
            while (true) {
                // Read the next incoming message.
                Message msg = readNextMessage();
                // Route it to the Fire Incident or Drone handler.
                routeMessage(msg);
                // Dispatch a pending event if the drone is ready.
                if (hasPendingDispatch()) {
                    dispatchNextEvent();
                }
                // Send shutdowns once all work is complete.
                sendShutdownToDroneIfNeeded();
                sendShutdownToFireIfNeeded();
                // Exit after both subsystems are shut down.
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
    Message readNextMessage() throws InterruptedException {
        return fromSubsystems.get();
    }

    /**
     * Routes a message to the correct handler.
     */
    void routeMessage(Message msg) throws InterruptedException {
        if (msg.getType() == Message.Type.FIRE_EVENT ||
                msg.getType() == Message.Type.SHUTDOWN) {
            handleFireIncidentMessage(msg);
        } else {
            handleDroneMessage(msg);
        }
    }

    /**
     * Checks if the scheduler can dispatch a pending event.
     */
    boolean hasPendingDispatch() {
        return droneReady && !pending.isEmpty();
    }

    /**
     * Dispatches the next pending event to the drone.
     */
    void dispatchNextEvent() throws InterruptedException {
        FireEvent next = pending.poll();
        toDrone.put(Message.droneAssignment(next));
        droneReady = false;
        System.out.println("[Scheduler] Dispatched to drone: " + next);
    }

    /**
     * Sends shutdown to Drone subsystem if conditions are met.
     */
    void sendShutdownToDroneIfNeeded() throws InterruptedException {
        if (isAllDone() && !shutdownSentToDrone) {
            toDrone.put(Message.shutdown());
            shutdownSentToDrone = true;
            System.out.println("[Scheduler] Sent shutdown to Drone.");
        }
    }

    /**
     * Sends shutdown to Fire Incident subsystem if conditions are met.
     */
    void sendShutdownToFireIfNeeded() throws InterruptedException {
        if (isAllDone() && !shutdownSentToFire) {
            toFireIncident.put(Message.shutdown());
            shutdownSentToFire = true;
            System.out.println("[Scheduler] Sent shutdown to Fire Incident.");
        }
    }

    /**
     * Checks whether all events are complete and queues are empty.
     */
    boolean isAllDone() {
        return fireIncidentDone && pending.isEmpty() && completed == totalEvents;
    }

    /**
     * Checks whether the scheduler should terminate.
     */
    boolean shouldTerminate() {
        return isAllDone() && shutdownSentToDrone && shutdownSentToFire;
    }

    /**
     * Handle messages from Fire Incident subsystem.
     */
    private void handleFireIncidentMessage(Message message) throws InterruptedException {
        if (message.getType() == Message.Type.FIRE_EVENT) {
            pending.add(message.getEvent());
            totalEvents++;
            System.out.println("[Scheduler] Received event: " + message.getEvent());
        } else if (message.getType() == Message.Type.SHUTDOWN) {
            fireIncidentDone = true;
            System.out.println("[Scheduler] Fire Incident input complete.");
        }
    }

    /**
     * Handle messages from the Drone subsystem.
     */
    private void handleDroneMessage(Message message) throws InterruptedException {
        if (message.getType() == Message.Type.DRONE_READY) {
            droneReady = true;
        } else if (message.getType() == Message.Type.DRONE_COMPLETED) {
            completed++;
            toFireIncident.put(Message.fireAck(message.getEvent()));
            System.out.println("[Scheduler] Completion ack forwarded: " + message.getEvent());
        }
    }
}