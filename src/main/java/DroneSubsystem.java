/**
 * DroneSubsystem.java
 *
 * Represents the drone client for Iteration 1.
 * It repeatedly asks the Scheduler for work and reports completion.
 */
public class DroneSubsystem implements Runnable {
    private final MessageBuffer toScheduler;
    private final MessageBuffer fromScheduler;
    private static final int MAX_CAPACITY_LITERS = 15;
    private static final double TRAVEL_SPEED_MPS = 15.0;
    private static final int METERS_PER_ZONE = 10;
    private static final double DROP_SECONDS_PER_LITER = 0.5;
    private static final int BASE_ZONE_ID = 0;
    private int remainingLiters = MAX_CAPACITY_LITERS;
    private DroneState currentState = DroneState.IDLE;
    private Message.Type lastMessage;

    /**
     * @param toScheduler queue used to send messages to Scheduler
     * @param fromScheduler queue used to receive assignments from Scheduler
     */
    public DroneSubsystem(MessageBuffer toScheduler,
                          MessageBuffer fromScheduler) {
        this.toScheduler = toScheduler;
        this.fromScheduler = fromScheduler;
    }

    private static final int DRONE_ID = 1;

    @Override
    public void run() {
        int completed = 0;
        System.out.println("[Drone] Ready.");

        try {
            // Announce initial readiness and IDLE state.
            sendReadySignal();
            sendUserIdlingUpdate();

            while (true) {
                // Wait for the next message from the Scheduler.
                Message reply = receiveMessage();
                Message.Type replyType = reply.getType();
                switch (replyType) {
                    // Stop cleanly when a shutdown message is received.
                    case SHUTDOWN:
                        System.out.println("[Drone] Finished. Completed: " + completed);
                        return;
                    // Handle assignments and report completion.
                    case DRONE_ASSIGNMENT:
                        processAssignment(reply);
                        completed++;
                        break;
                    case DRONE_RETURN_TO_BASE:
                        handleReturnToBase();
                        break;
                    default:
                        System.out.println("[Drone] Unknown message type: " + replyType);
                }

                // Old loop
//                // Stop cleanly when a shutdown message is received.
//                if (isShutdown(reply)) {
//                    break;
//                }
//                // Handle assignments and report completion.
//                if (isAssignment(reply)) {
//                    processAssignment(reply);
//                    completed++;
//                } else if (isReturnToBase(reply)) {
//                    handleReturnToBase();
//                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Drone] Interrupted.", e);
        }
    }

    /**
     * Get the currentState of the drone.
     */
    DroneState getCurrentState(DroneState droneState) {
        return currentState;
    }

    /**
     * Set the currentState of the drone.
     */
    void setCurrentState(DroneState droneState) {
        currentState = droneState;
    }

    /**
     * Announce readiness to the Scheduler.
     */
    void sendReadySignal() throws InterruptedException {
        toScheduler.put(Message.droneReady());
    }
    
    /**
     * Send an update that the drone is IDLE at base.
     */
    void sendUserIdlingUpdate() throws InterruptedException {
        toScheduler.put(Message.droneStatus(new DroneStatus(DRONE_ID, DroneState.IDLE, BASE_ZONE_ID, remainingLiters)));
        setCurrentState(DroneState.IDLE);
    }

    /**
     * Send an update that the drone is IDLE at a specific zone.
     */
    void sendUserIdlingUpdate(int zoneId) throws InterruptedException {
        toScheduler.put(Message.droneStatus(new DroneStatus(DRONE_ID, DroneState.IDLE, zoneId, remainingLiters)));
        setCurrentState(DroneState.IDLE);
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
     * Checks if a message is a return to base command.
     */
    boolean isReturnToBase(Message reply) {
        return reply.getType() == Message.Type.DRONE_RETURN_TO_BASE;
    }

    /**
     * Handle an assignment from the Scheduler.
     */
    void processAssignment(Message reply) throws InterruptedException {
        FireEvent event = reply.getEvent();
        System.out.println("[Drone] Assigned: " + event);

        simulateService(event);
        sendCompletion(event);
        
        // Return to IDLE state at CURRENT ZONE
        System.out.println("[Drone] Task complete. Returning to Ready state at Zone " + event.getZoneId());
        // We stay at the current zone. The scheduler will decide whether to send us more work or Return to Base.
        sendReadySignal();
        sendUserIdlingUpdate(event.getZoneId());
    }

    /**
     * Reports completion of an event to the Scheduler.
     */
    void sendCompletion(FireEvent event) throws InterruptedException {
        toScheduler.put(Message.droneCompleted(event));
    }

    /**
     * Checks if a refill is needed and handles the refill process.
     */
    private void checkAndRefillIfNeeded() throws InterruptedException {
        if (remainingLiters <= 0) {
            System.out.println("[Drone] Tank empty. Returning to base for refill.");
            
            // Return to base
            sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
            simulateTravel(BASE_ZONE_ID);
            
            // Refill
            System.out.println("[Drone] Refilling...");
            sendStatus(DroneState.REFILLING, BASE_ZONE_ID);
            Thread.sleep(2000); // Simulate refill time
            remainingLiters = MAX_CAPACITY_LITERS;
            System.out.println("[Drone] Refilled. Capacity: " + remainingLiters);
        }
    }

    /**
     * Simulate service time based on severity.
     */
    private void simulateService(FireEvent event) throws InterruptedException {
        int remainingRequired = event.getRequiredLiters();
        int targetZone = event.getZoneId();

         // Check if we need to refill BEFORE heading out (if empty)
        checkAndRefillIfNeeded();

        // 1. Travel to Zone
        System.out.println("[Drone] Flying to Zone " + targetZone);
        sendStatus(DroneState.EN_ROUTE, targetZone);
        simulateTravel(targetZone);

        // 2. Extinguish
        while (remainingRequired > 0) {
            checkAndRefillIfNeeded();
            
            // If we had to return to base, we need to travel back to the fire
            if (remainingLiters == MAX_CAPACITY_LITERS) {
                 System.out.println("[Drone] Returning to Zone " + targetZone);
                 sendStatus(DroneState.EN_ROUTE, targetZone);
                 simulateTravel(targetZone);
            }

            System.out.println("[Drone] Extinguishing fire at Zone " + targetZone);
            sendStatus(DroneState.EXTINGUISHING, targetZone);
            
            int toDrop = Math.min(remainingLiters, remainingRequired);
            double dropSeconds = toDrop * DROP_SECONDS_PER_LITER;
            long sleepMs = Math.round(dropSeconds * 1000);
            
            Thread.sleep(sleepMs);
            
            remainingLiters -= toDrop;
            remainingRequired -= toDrop;
            System.out.println("[Drone] Dropped " + toDrop + "L. Remaining in tank: " + remainingLiters + "L. Fire needs: " + remainingRequired + "L");
            
            // Update status after drop
            sendStatus(DroneState.EXTINGUISHING, targetZone);
        }
        
        // 3. Return to Base (Optional optimization: stay if next task is close? 
        // For now, let's just stay here until next assignment or forced return)
        // But the prompt says "The drone either returns to base or proceeds to the next assignment."
        // For simplicity in this iteration, we'll mark as IDLE at Current Zone or Return to Base?
        // Let's Return to Base for consistency with Iteration 2 requirements
        
        System.out.println("[Drone] Fire extinguished. Awaiting next command.");
        // Removed automatic return to base. Scheduler will send DRONE_RETURN_TO_BASE if needed.
    }
    
    private void sendStatus(DroneState state, int zoneId) throws InterruptedException {
        toScheduler.put(Message.droneStatus(new DroneStatus(DRONE_ID, state, zoneId, remainingLiters)));
        setCurrentState(state);
    }
    
    private void simulateTravel(int zoneId) throws InterruptedException {
        // Simple placeholder for travel time
        // In real impl, calculate distance from current pos to zoneId
        double travelSeconds = 2.0; // Fixed 2 seconds for now
        Thread.sleep((long)(travelSeconds * 1000));
    }

    private double estimateTravelSeconds(int zoneId) {
        int distanceMeters = 50;
        return distanceMeters / TRAVEL_SPEED_MPS;
    }

    /**
     * Handles the explicit Return To Base command from Scheduler.
     */
    private void handleReturnToBase() throws InterruptedException {
        System.out.println("[Drone] Received Return to Base command.");
        
        // Travel to base
        sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
        simulateTravel(BASE_ZONE_ID);
        
        // Refill
        System.out.println("[Drone] Refilling...");
        sendStatus(DroneState.REFILLING, BASE_ZONE_ID);
        Thread.sleep(2000); // Simulate refill time
        remainingLiters = MAX_CAPACITY_LITERS;
        System.out.println("[Drone] Refilled. Capacity: " + remainingLiters);
        
        // Back to IDLE at Base
        sendReadySignal();
        sendUserIdlingUpdate();
    }
}