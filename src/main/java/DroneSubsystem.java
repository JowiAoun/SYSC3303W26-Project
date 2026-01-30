/**
 * DroneSubsystem.java
 *
 * Represents the drone client for Iteration 1.
 * It repeatedly asks the Scheduler for work and reports completion.
 */
public class DroneSubsystem implements Runnable {
    private final MessageBuffer toScheduler;
    private final MessageBuffer fromScheduler;

    /**
     * @param toScheduler queue used to send messages to Scheduler
     * @param fromScheduler queue used to receive assignments from Scheduler
     */
    public DroneSubsystem(MessageBuffer toScheduler,
                          MessageBuffer fromScheduler) {
        this.toScheduler = toScheduler;
        this.fromScheduler = fromScheduler;
    }

    @Override
    public void run() {
        int completed = 0;
        System.out.println("[Drone] Ready.");

        try {
            // Announce initial readiness to the Scheduler.
            sendReadySignal();
            while (true) {
                // Wait for the next message from the Scheduler.
                Message reply = receiveMessage();
                // Stop cleanly when a shutdown message is received.
                if (isShutdown(reply)) {
                    break;
                }
                // Handle assignments and report completion.
                if (isAssignment(reply)) {
                    processAssignment(reply);
                    completed++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Drone] Interrupted.", e);
        }

        System.out.println("[Drone] Finished. Completed: " + completed);
    }

    /**
     * Announce readiness to the Scheduler.
     */
    void sendReadySignal() throws InterruptedException {
        toScheduler.put(Message.droneReady());
    }

    /**
     * Reads one message from the Scheduler.
     */
    Message receiveMessage() throws InterruptedException {
        return fromScheduler.get();
    }

    /**
     * Checks if a message is a shutdown signal.
     */
    boolean isShutdown(Message reply) {
        return reply.getType() == Message.Type.SHUTDOWN;
    }

    /**
     * Checks if a message is an assignment.
     */
    boolean isAssignment(Message reply) {
        return reply.getType() == Message.Type.DRONE_ASSIGNMENT;
    }

    /**
     * Handle an assignment from the Scheduler.
     */
    void processAssignment(Message reply) throws InterruptedException {
        FireEvent event = reply.getEvent();
        System.out.println("[Drone] Assigned: " + event);

        simulateService(event);
        sendCompletion(event);
        System.out.println("[Drone] Ready.");
        sendReadySignal();
    }

    /**
     * Reports completion of an event to the Scheduler.
     */
    void sendCompletion(FireEvent event) throws InterruptedException {
        toScheduler.put(Message.droneCompleted(event));
    }

    /**
     * Simulate service time based on severity (placeholder timing).
     */
    private void simulateService(FireEvent event) throws InterruptedException {
        int sleepMs;
        switch (event.getSeverity()) {
            case LOW:
                sleepMs = 300;
                break;
            case MODERATE:
                sleepMs = 600;
                break;
            case HIGH:
                sleepMs = 900;
                break;
            default:
                sleepMs = 400;
        }
        Thread.sleep(sleepMs);
    }
}